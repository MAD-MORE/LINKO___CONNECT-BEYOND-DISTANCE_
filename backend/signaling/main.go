package main

import (
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"sync"

	"github.com/gorilla/websocket"
)

var addr = flag.String("addr", ":3000", "http service address")

type RegisterRequest struct {
	DeviceID string `json:"device_id"`
}

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
}

// Simple in-memory session registry
var sessions = struct {
	sync.RWMutex
	byDevice map[string]*websocket.Conn
}{byDevice: make(map[string]*websocket.Conn)}

func registerHandler(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "bad request", http.StatusBadRequest)
		return
	}
	// For now just echo back an OK with a small payload.
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok", "device_id": req.DeviceID})
}

func wsHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	device := q.Get("device_id")
	if device == "" {
		http.Error(w, "device_id required", http.StatusBadRequest)
		return
	}

	c, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Print("upgrade:", err)
		return
	}
	defer c.Close()

	// register connection
	sessions.Lock()
	sessions.byDevice[device] = c
	sessions.Unlock()

	for {
		_, msg, err := c.ReadMessage()
		if err != nil {
			log.Println("read error:", err)
			break
		}
		// Expect messages that contain {"to":"deviceId","payload":{...}}
		var m map[string]interface{}
		if err := json.Unmarshal(msg, &m); err != nil {
			log.Println("invalid message", err)
			continue
		}
		if to, ok := m["to"].(string); ok {
			sessions.RLock()
			if oc, found := sessions.byDevice[to]; found {
				oc.WriteMessage(websocket.TextMessage, msg)
			}
			sessions.RUnlock()
		}
	}

	// cleanup on exit
	sessions.Lock()
	if sessions.byDevice[device] == c {
		delete(sessions.byDevice, device)
	}
	sessions.Unlock()
}

func main() {
	flag.Parse()
	http.HandleFunc("/api/register", registerHandler)
	http.HandleFunc("/ws", wsHandler)
	log.Printf("signaling service listening on %s", *addr)
	log.Fatal(http.ListenAndServe(*addr, nil))
}
