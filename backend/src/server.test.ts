import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "./server.js";

/**
 * Backend integration tests.
 * Starts the real control-plane HTTP server and exercises the documented API contract.
 *
 * Run: npm test
 */

const PORT = 18_099;
const BASE = `http://127.0.0.1:${PORT}`;
const ENROLLMENT_TOKEN = "test-enrollment-token";

async function request(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  headers.set("connection", "close");
  return fetch(`${BASE}${path}`, { ...init, headers });
}

let server: ReturnType<typeof createServer>["server"];
let controlPlane: ReturnType<typeof createServer>["controlPlane"];

before(async () => {
  ({ server, controlPlane } = createServer({ enrollmentToken: ENROLLMENT_TOKEN }));
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(PORT, "127.0.0.1", () => resolve());
  });
});

after(async () => {
  server.closeIdleConnections();
  server.closeAllConnections();
  controlPlane.database.db.close();
  await new Promise<void>((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
});

describe("GET /healthz", () => {
  it("returns 200 with ok status", async () => {
    const res = await request(`/healthz`);
    assert.equal(res.status, 200);
    const body = await res.json() as Record<string, unknown>;
    assert.equal(body.status, "ok");
  });
});

describe("POST /v1/devices/register", () => {
  it("rejects enrollment without the enrollment token", async () => {
    const res = await fetch(`${BASE}/v1/devices/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: "device-no-auth", publicKey: "test-public-key-1234" }),
    });
    assert.equal(res.status, 401);
  });

  it("registers a device and returns a short-lived access token", async () => {
    const deviceId = `ci-device-${Date.now()}`;
    const res = await fetch(`${BASE}/v1/devices/register`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Enrollment-Token": ENROLLMENT_TOKEN,
      },
      body: JSON.stringify({ deviceId, publicKey: "test-public-key-1234" }),
    });
    assert.equal(res.status, 201);
    const body = await res.json() as Record<string, unknown>;
    assert.equal(typeof body.accessToken, "string");
    assert.equal(typeof body.expiresAtEpochSeconds, "number");
  });

  it("rejects an invalid device payload", async () => {
    const res = await fetch(`${BASE}/v1/devices/register`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Enrollment-Token": ENROLLMENT_TOKEN,
      },
      body: JSON.stringify({ deviceId: "bad id", publicKey: "short" }),
    });
    assert.equal(res.status, 400);
  });
});

describe("Protected API routes", () => {
  it("rejects friends lookup without a device JWT", async () => {
    const res = await request(`/v1/friends`);
    assert.equal(res.status, 401);
  });

  it("rejects session-state changes without a device JWT", async () => {
    const res = await fetch(`${BASE}/v1/sessions/fake-session/state`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ state: "connected" }),
    });
    assert.equal(res.status, 401);
  });

  it("rejects unknown API routes without authentication", async () => {
    const res = await request(`/v1/unknown-endpoint`);
    assert.equal(res.status, 401);
  });
});
