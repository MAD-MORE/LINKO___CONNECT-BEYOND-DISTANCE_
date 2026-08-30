package main

import (
	"encoding/json"
	"log"
	"net/http"
	"time"
	"github.com/google/uuid"
)

// WGOffer represents a client's WireGuard offer data that can be forwarded to the peer.
type WGOffer struct {
	SessionID string `json:"session_id"`
	From      string `json:"from"`
	To        string `json:"to"`
	PubKey    string `json:"pubkey"`
	Endpoint  string `json:"endpoint,omitempty"`
	Candidates []string `json:"candidates,omitempty"`
	Timestamp time.Time `json:"timestamp"`
}

func wgOfferHandler(w http.ResponseWriter, r *http.Request) {
	var o WGOffer
	if err := json.NewDecoder(r.Body).Decode(&o); err != nil {
		http.Error(w, "bad request", http.StatusBadRequest)
		return
	}
	if o.From == "" || o.To == "" || o.PubKey == "" {
		http.Error(w, "from,to,pubkey required", http.StatusBadRequest)
		return
	}
	// attach timestamp and id
	o.Timestamp = time.Now()
	// audit
	uuidVal := uuid.New()
	raw, _ := json.Marshal(o)
	ctx := r.Context()
	_, _ = db.Exec(ctx, `INSERT INTO audits(id, session_id, device_id, event) VALUES($1,$2,$3,$4)`, uuidVal.String(), o.SessionID, o.From, raw)
	// forward to peer if online
	conns.RLock()
	if oc, found := conns.byDevice[o.To]; found {
		oc.WriteJSON(map[string]interface{}{"type": "wg-offer", "payload": o})
	}
	conns.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}
