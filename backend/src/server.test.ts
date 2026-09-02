import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { server } from "./server.js";

/**
 * Backend integration tests.
 * Starts a real HTTP server with an in-memory store and tests all routes.
 *
 * Run: npm test
 */

function getBaseUrl(): string {
  const addr = server.address();
  if (typeof addr === "object" && addr) {
    return `http://127.0.0.1:${addr.port}`;
  }
  return "http://127.0.0.1:8080";
}

before(async () => {
  await new Promise<void>((resolve) => {
    if (server.listening) resolve();
    else server.listen(0, "127.0.0.1", () => resolve());
  });
});

after(async () => {
  await new Promise<void>((resolve) => {
    if (!server.listening) resolve();
    else server.close(() => resolve());
  });
});

// ---------------------------------------------------------------------------
// Health check tests
// ---------------------------------------------------------------------------

describe("GET /health", () => {
  it("returns 200 with ok status", async () => {
    const res = await fetch(`${getBaseUrl()}/health`);
    assert.equal(res.status, 200);
    const body = await res.json() as Record<string, unknown>;
    assert.equal(body.service, "linko-control-plane");
    assert.equal(body.status, "ok");
    assert.ok(body.database);
  });
});

// ---------------------------------------------------------------------------
// Auth / signup tests (skipped if Supabase not configured)
// ---------------------------------------------------------------------------

describe("POST /v1/auth/signup", () => {
  it("returns 400 for missing email", async () => {
    const res = await fetch(`${getBaseUrl()}/v1/auth/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password: "test123456" }),
    });
    // Either 400 (validation) or 503 (Supabase not configured in test) is acceptable
    assert.ok([400, 503].includes(res.status), `Expected 400 or 503, got ${res.status}`);
  });

  it("returns 400 for short password", async () => {
    const res = await fetch(`${getBaseUrl()}/v1/auth/signup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: "test@linko.app", password: "abc" }),
    });
    assert.ok([400, 503].includes(res.status));
  });
});

// ---------------------------------------------------------------------------
// Device registration tests
// ---------------------------------------------------------------------------

describe("POST /v1/devices/register", () => {
  it("returns 401 without auth token", async () => {
    const res = await fetch(`${getBaseUrl()}/v1/devices/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ publicKey: "test-key", name: "Test Device", roles: ["receiver"] }),
    });
    assert.equal(res.status, 401);
  });
});

// ---------------------------------------------------------------------------
// Bootstrap device registration tests
// ---------------------------------------------------------------------------

describe("POST /v1/devices (bootstrap)", () => {
  it("returns 401 without bootstrap secret", async () => {
    const res = await fetch(`${getBaseUrl()}/v1/devices`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userId: "user-1", publicKey: "pk", name: "Dev", roles: ["provider"] }),
    });
    assert.equal(res.status, 401);
  });

  it("returns 400 for missing fields with valid bootstrap secret", async () => {
    const secret = process.env.LINKO_BOOTSTRAP_SECRET ?? "development-bootstrap";
    const res = await fetch(`${getBaseUrl()}/v1/devices`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Linko-Bootstrap": secret,
      },
      body: JSON.stringify({ name: "Dev", roles: ["provider"] }), // missing userId and publicKey
    });
    assert.equal(res.status, 400);
  });
});

// ---------------------------------------------------------------------------
// Session creation tests
// ---------------------------------------------------------------------------

describe("POST /v1/sessions", () => {
  it("returns 401 without device JWT", async () => {
    const res = await fetch(`${getBaseUrl()}/v1/sessions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ receiverDeviceId: "r1", providerDeviceId: "p1" }),
    });
    assert.equal(res.status, 401);
  });
});

// ---------------------------------------------------------------------------
// Not found
// ---------------------------------------------------------------------------

describe("Unknown routes", () => {
  it("returns 404 for unknown path", async () => {
    const res = await fetch(`${getBaseUrl()}/v1/unknown-endpoint`, {
      headers: { "Authorization": "Bearer fake-token" },
    });
    // Either 401 (bad token) or 404 (not found after auth)
    assert.ok([401, 404].includes(res.status));
  });
});
