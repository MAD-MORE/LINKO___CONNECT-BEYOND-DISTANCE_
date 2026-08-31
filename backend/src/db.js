const Database = require('better-sqlite3');
const path = require('node:path');

const databasePath = process.env.LINKSHARE_DB_PATH || path.resolve(__dirname, '..', 'linkshare.db');
const db = new Database(databasePath);

db.pragma('foreign_keys = ON');
db.pragma('journal_mode = WAL');
db.exec(`
  CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
  CREATE TABLE IF NOT EXISTS friends (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    friend_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('pending', 'accepted', 'blocked')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, friend_id)
  );
  CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_device_id TEXT NOT NULL,
    client_device_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('requesting', 'handshaking', 'connected', 'failed', 'ended')),
    started_at TEXT,
    ended_at TEXT,
    bytes_used INTEGER NOT NULL DEFAULT 0 CHECK (bytes_used >= 0)
  );
  CREATE TABLE IF NOT EXISTS tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    expires_at TEXT NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0 CHECK (revoked IN (0, 1))
  );
  CREATE INDEX IF NOT EXISTS idx_devices_device_id ON devices(device_id);
  CREATE INDEX IF NOT EXISTS idx_tokens_token ON tokens(token);
  CREATE INDEX IF NOT EXISTS idx_friends_user_id ON friends(user_id);
  CREATE INDEX IF NOT EXISTS idx_sessions_participants ON sessions(host_device_id, client_device_id);
`);

const statements = {
  insertDevice: db.prepare('INSERT INTO devices (device_id, public_key) VALUES (?, ?)'),
  getDeviceById: db.prepare('SELECT id, device_id, public_key, created_at FROM devices WHERE device_id = ?'),
  createFriendRequest: db.prepare("INSERT INTO friends (user_id, friend_id, status) VALUES (?, ?, 'pending') ON CONFLICT(user_id, friend_id) DO NOTHING"),
  updateFriendStatus: db.prepare("UPDATE friends SET status = ? WHERE user_id = ? AND friend_id = ? AND ? IN ('pending', 'accepted', 'blocked')"),
  getFriendship: db.prepare('SELECT id, user_id, friend_id, status, created_at FROM friends WHERE user_id = ? AND friend_id = ?'),
  getFriendsForUser: db.prepare("SELECT friend_id FROM friends WHERE user_id = ? AND status = 'accepted'"),
  createSession: db.prepare("INSERT INTO sessions (host_device_id, client_device_id, status) VALUES (?, ?, 'requesting')"),
  getSessionById: db.prepare('SELECT id, host_device_id, client_device_id, status, started_at, ended_at, bytes_used FROM sessions WHERE id = ?'),
  getSessionForParticipant: db.prepare('SELECT id, host_device_id, client_device_id, status, started_at, ended_at, bytes_used FROM sessions WHERE id = ? AND (host_device_id = ? OR client_device_id = ?)'),
  updateSessionStatus: db.prepare("UPDATE sessions SET status = ?, started_at = CASE WHEN ? = 'handshaking' AND started_at IS NULL THEN CURRENT_TIMESTAMP ELSE started_at END, ended_at = CASE WHEN ? IN ('failed', 'ended') THEN CURRENT_TIMESTAMP ELSE ended_at END WHERE id = ?"),
  incrementSessionBytes: db.prepare('UPDATE sessions SET bytes_used = bytes_used + ? WHERE id = ?'),
  createToken: db.prepare('INSERT INTO tokens (device_id, token, expires_at) VALUES (?, ?, ?)'),
  revokeToken: db.prepare('UPDATE tokens SET revoked = 1 WHERE token = ?'),
  getValidToken: db.prepare('SELECT id, device_id, token, expires_at, revoked FROM tokens WHERE token = ? AND revoked = 0 AND expires_at > CURRENT_TIMESTAMP'),
};

function insertDevice(deviceId, publicKey) { return statements.insertDevice.run(deviceId, publicKey); }
function getDeviceById(deviceId) { return statements.getDeviceById.get(deviceId); }
function createFriendRequest(userId, friendId) { return statements.createFriendRequest.run(userId, friendId); }
function updateFriendStatus(userId, friendId, status) { return statements.updateFriendStatus.run(status, userId, friendId, status); }
function getFriendship(userId, friendId) { return statements.getFriendship.get(userId, friendId); }
function getFriendsForUser(userId) { return statements.getFriendsForUser.all(userId); }
function createSession(hostDeviceId, clientDeviceId) { return statements.createSession.run(hostDeviceId, clientDeviceId); }
function getSessionById(sessionId) { return statements.getSessionById.get(sessionId); }
function getSessionForParticipant(sessionId, deviceId) { return statements.getSessionForParticipant.get(sessionId, deviceId, deviceId); }
function updateSessionStatus(sessionId, status) { return statements.updateSessionStatus.run(status, status, status, sessionId); }
function incrementSessionBytes(sessionId, bytes) { return statements.incrementSessionBytes.run(bytes, sessionId); }
function createToken(deviceId, token, expiresAt) { return statements.createToken.run(deviceId, token, expiresAt); }
function revokeToken(token) { return statements.revokeToken.run(token); }
function isTokenValid(token) { return statements.getValidToken.get(token); }

module.exports = { db, insertDevice, getDeviceById, createFriendRequest, updateFriendStatus, getFriendship, getFriendsForUser, createSession, getSessionById, getSessionForParticipant, updateSessionStatus, incrementSessionBytes, createToken, revokeToken, isTokenValid };
