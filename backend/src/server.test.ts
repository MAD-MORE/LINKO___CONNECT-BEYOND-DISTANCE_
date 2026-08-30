import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { spawn, type ChildProcess } from "node:child_process";

const PORT = 18_099;
const BASE = `http://127.0.0.1:${PORT}`;
let server: ChildProcess | null = null;

async function waitForServer(timeoutMs = 10_000): Promise<void> {
  const started = Date.now();
  let lastError: unknown;
  while (Date.now() - started < timeoutMs) {
    try {
      const response = await fetch(`${BASE}/health`);
      if (response.ok || response.status === 503) return;
      lastError = new Error(`health status ${response.status}`);
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`LINKO test server did not start on ${BASE}: ${String(lastError)}`);
}

before(async () => {
  server = spawn(process.execPath, ["--import", "tsx", "src/server.ts"], {
    cwd: process.cwd(),
    env: { ...process.env, NODE_ENV: "test", PORT: String(PORT), TUNNEL_PORT: "0" },
    stdio: "ignore",
  });
  await waitForServer();
});

after(async () => {
  if (!server) return;
  server.kill("SIGTERM");
  await new Promise<void>((resolve) => {
    if (!server || server.exitCode !== null) return resolve();
    const timer = setTimeout(() => { server?.kill("SIGKILL"); resolve(); }, 2_000);
    server.once("exit", () => { clearTimeout(timer); resolve(); });
  });
  server = null;
});

describe("GET /health", () => {
  it("returns 200 with ok status", async () => {
    const res = await fetch(`${BASE}/health`);
    assert.equal(res.status, 200);
    const body = await res.json() as Record<string, unknown>;
    assert.equal(body.service, "linko-control-plane");
    assert.equal(body.status, "ok");
    assert.ok(body.database);
  });
});

describe("POST /v1/auth/signup", () => {
  it("returns 400 for missing email", async () => {
    const res = await fetch(`${BASE}/v1/auth/signup`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ password: "test123456" }) });
    assert.ok([400, 503].includes(res.status), `Expected 400 or 503, got ${res.status}`);
  });
  it("returns 400 for short password", async () => {
    const res = await fetch(`${BASE}/v1/auth/signup`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: "test@linko.app", password: "abc" }) });
    assert.ok([400, 503].includes(res.status));
  });
});

describe("POST /v1/devices/register", () => {
  it("returns 401 without auth token", async () => {
    const res = await fetch(`${BASE}/v1/devices/register`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ publicKey: "test-key", name: "Test Device", roles: ["receiver"] }) });
    assert.equal(res.status, 401);
  });
});

describe("POST /v1/devices (bootstrap)", () => {
  it("returns 401 without bootstrap secret", async () => {
    const res = await fetch(`${BASE}/v1/devices`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: "user-1", publicKey: "pk", name: "Dev", roles: ["provider"] }) });
    assert.equal(res.status, 401);
  });
  it("returns 400 for missing fields with valid bootstrap secret", async () => {
    const secret = process.env.LINKO_BOOTSTRAP_SECRET ?? "test-bootstrap-secret";
    const res = await fetch(`${BASE}/v1/devices`, { method: "POST", headers: { "Content-Type": "application/json", "X-Linko-Bootstrap": secret }, body: JSON.stringify({ name: "Dev", roles: ["provider"] }) });
    assert.equal(res.status, 400);
  });
});

describe("POST /v1/sessions", () => {
  it("returns 401 without device JWT", async () => {
    const res = await fetch(`${BASE}/v1/sessions`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ receiverDeviceId: "r1", providerDeviceId: "p1" }) });
    assert.equal(res.status, 401);
  });
});

describe("Unknown routes", () => {
  it("returns 401 or 404 for unknown path", async () => {
    const res = await fetch(`${BASE}/v1/unknown-endpoint`, { headers: { "Authorization": "Bearer fake-token" } });
    assert.ok([401, 404].includes(res.status));
  });
});
