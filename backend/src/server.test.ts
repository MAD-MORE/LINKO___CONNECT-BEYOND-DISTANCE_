import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { server } from "./server.js";

/** Backend integration tests. Run: npm test */

const PORT = 18_099;
const BASE = `http://127.0.0.1:${PORT}`;
const BOOTSTRAP_SECRET = process.env.LINKO_BOOTSTRAP_SECRET ?? "development-bootstrap";

async function request(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers);
  headers.set("connection", "close");
  return fetch(`${BASE}${path}`, { ...init, headers });
}

before(async () => {
  server.keepAliveTimeout = 0;
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(PORT, "127.0.0.1", () => resolve());
  });
});

after(async () => {
  await new Promise<void>((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
  server.closeIdleConnections();
  server.closeAllConnections();
});

describe("GET /health", () => {
  it("returns 200 with ok status", async () => {
    const res = await request(`/health`);
    assert.equal(res.status, 200);
    const body = await res.json() as Record<string, unknown>;
    assert.equal(body.status, "ok");
  });
});

describe("POST /v1/devices", () => {
  it("rejects enrollment without the bootstrap secret", async () => {
    const res = await request(`/v1/devices`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: "device-no-auth", publicKey: "test-public-key-1234", name: "CI", roles: ["receiver"] }),
    });
    assert.equal(res.status, 401);
  });

  it("registers a device and returns a device token", async () => {
    const res = await request(`/v1/devices`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Linko-Bootstrap": BOOTSTRAP_SECRET,
      },
      body: JSON.stringify({ userId: `ci-user-${Date.now()}`, publicKey: "test-public-key-1234", name: "CI", roles: ["receiver"] }),
    });
    assert.equal(res.status, 201);
    const body = await res.json() as Record<string, unknown>;
    assert.equal(typeof body.accessToken, "string");
  });

  it("rejects an invalid device payload", async () => {
    const res = await request(`/v1/devices`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Linko-Bootstrap": BOOTSTRAP_SECRET,
      },
      body: JSON.stringify({ userId: "bad" }),
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
    const res = await request(`/v1/sessions/fake-session/transition`, {
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
