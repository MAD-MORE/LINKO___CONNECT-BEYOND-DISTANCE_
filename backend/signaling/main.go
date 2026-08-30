package main

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/google/uuid"
)

var addr = flag.String("addr", ":3000", "http service address")
var dbUrl = flag.String("database_url", "postgres://postgres:postgres@db:5432/linko?sslmode=disable", "Postgres DSN")

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

type RegisterRequest struct {
	DeviceID string `json:"device_id"`
	Name     string `json:"name,omitempty"`
}

type PairRequest struct {
	DeviceA string `json:"device_a"`
	DeviceB string `json:"device_b"`
}

type Session struct {
	ID        string    `json:"id"`
	DeviceA   string    `json:"device_a"`
	DeviceB   string    `json:"device_b"`
	CreatedAt time.Time `json:"created_at"`
}

// in-memory ws registry for routing
var conns = struct {
	sync.RWMutex
	byDevice map[string]*websocket.Conn
}{byDevice: make(map[string]*websocket.Conn)}

var db *pgxpool.Pool

func randomToken(n int) (string, error) {
	b := make([]byte, n)
	_, err := rand.Read(b)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func ensureSchema(ctx context.Context) error {
	_, err := db.Exec(ctx, `CREATE TABLE IF NOT EXISTS devices (
  device_id TEXT PRIMARY KEY,
  name TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  device_a TEXT NOT NULL,
  device_b TEXT NOT NULL,
  token TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audits (
  id UUID PRIMARY KEY,
  session_id TEXT,
  device_id TEXT,
  event JSONB,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
`)
	return err
}

func registerHandler(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad request", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" {
		http.Error(w, "device_id required", http.StatusBadRequest)
		return
	}
	ctx := r.Context()
	_, err := db.Exec(ctx, `INSERT INTO devices(device_id, name) VALUES($1,$2)
	ON CONFLICT (device_id) DO UPDATE SET name = EXCLUDED.name`, req.DeviceID, req.Name)
	if err != nil {
		log.Println("db insert device error:", err)
		http.Error(w, "internal", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok", "device_id": req.DeviceID})
}

func pairHandler(w http.ResponseWriter, r *http.Request) {
	var req PairRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad request", http.StatusBadRequest)
		return
	}
	if req.DeviceA == "" || req.DeviceB == "" {
		http.Error(w, "both devices required", http.StatusBadRequest)
		return
	}
	id := uuid.New().String()
	token, err := randomToken(16)
	if err != nil {
		log.Println("token gen error:", err)
		http.Error(w, "internal", http.StatusInternalServerError)
		return
	}
	ctx := r.Context()
	_, err = db.Exec(ctx, `INSERT INTO sessions(id, device_a, device_b, token) VALUES($1,$2,$3,$4)`, id, req.DeviceA, req.DeviceB, token)
	if err != nil {
		log.Println("db insert session error:", err)
		http.Error(w, "internal", http.StatusInternalServerError)
		return
	}
	s := Session{ID: id, DeviceA: req.DeviceA, DeviceB: req.DeviceB, CreatedAt: time.Now()}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"session": s, "token": token})
}

func wsHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	device := q.Get("device_id")
	token := q.Get("token")
	if device == "" {
		http.Error(w, "device_id required", http.StatusBadRequest)
		return
	}
	// optional token validation: if token provided, check session exists
	if token != "" {
		var exists bool
		ctx := r.Context()
		err := db.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM sessions WHERE token=$1)`, token).Scan(&exists)
		if err != nil || !exists {
			log.Println("invalid token ws", err)
			http.Error(w, "invalid token", http.StatusUnauthorized)
			return
		}
	}

	c, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Print("upgrade:", err)
		return
	}
	defer c.Close()

	// register connection
	conns.Lock()
	conns.byDevice[device] = c
	conns.Unlock()

	for {
		_, msg, err := c.ReadMessage()
		if err != nil {
			log.Println("read error:", err)
			break
		}
		// Expect messages that contain {"to":"deviceId","type":"offer","payload":{...}}
		var m map[string]interface{}
		if err := json.Unmarshal(msg, &m); err != nil {
			log.Println("invalid message", err)
			continue
		}
		// record audit
		if sid, ok := m["session_id"].(string); ok {
			raw, _ := json.Marshal(m)
			uuidVal := uuid.New()
			ctx := context.Background()
			_, _ = db.Exec(ctx, `INSERT INTO audits(id, session_id, device_id, event) VALUES($1,$2,$3,$4)`, uuidVal.String(), sid, device, raw)
		}
		if to, ok := m["to"].(string); ok {
			conns.RLock()
			if oc, found := conns.byDevice[to]; found {
				oc.WriteMessage(websocket.TextMessage, msg)
			}
			conns.RUnlock()
		}
	}

	// cleanup on exit
	conns.Lock()
	if conns.byDevice[device] == c {
		delete(conns.byDevice, device)
	}
	conns.Unlock()
}

func sessionsHandler(w http.ResponseWriter, r *http.Request) {
	// simple list sessions (for operators)
	ctx := r.Context()
	rows, err := db.Query(ctx, `SELECT id, device_a, device_b, created_at FROM sessions ORDER BY created_at DESC LIMIT 100`)
	if err != nil {
		http.Error(w, "internal", http.StatusInternalServerError)
		return
	}
	defer rows.Close()
	list := []Session{}
	for rows.Next() {
		var s Session
		if err := rows.Scan(&s.ID, &s.DeviceA, &s.DeviceB, &s.CreatedAt); err != nil {
			continue
		}
		list = append(list, s)
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"sessions": list})
}

func main() {
	flag.Parse()
	ctx := context.Background()
	var err error
	cfgUrl := os.Getenv("DATABASE_URL")
	if cfgUrl == "" {
		cfgUrl = *dbUrl
	}
	db, err = pgxpool.New(ctx, cfgUrl)
	if err != nil {
		log.Fatalf("unable to connect to db: %v", err)
	}
	defer db.Close()
	// ensure schema
	if err := ensureSchema(ctx); err != nil {
		log.Fatalf("schema init failed: %v", err)
	}

	http.HandleFunc("/api/register", registerHandler)
	http.HandleFunc("/api/pair", pairHandler)
	http.HandleFunc("/api/sessions", sessionsHandler)
	http.HandleFunc("/ws", wsHandler)
	log.Printf("signaling service listening on %s", *addr)
	log.Fatal(http.ListenAndServe(*addr, nil))
}
