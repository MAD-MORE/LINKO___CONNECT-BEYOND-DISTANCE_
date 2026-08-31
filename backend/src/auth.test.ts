import test from "node:test";
import assert from "node:assert/strict";
import { issueDeviceToken, verifyDeviceToken, isBootstrapSecret } from "./auth.js";

test("device token round-trips and carries device identity", () => {
  const token = issueDeviceToken("user-1", "device-1");
  const payload = verifyDeviceToken(token);
  assert.equal(payload?.sub, "user-1");
  assert.equal(payload?.deviceId, "device-1");
});

test("tampered token is rejected", () => {
  const token = issueDeviceToken("user-1", "device-1");
  assert.equal(verifyDeviceToken(`${token}x`), null);
});

test("bootstrap authentication uses the configured development secret", () => {
  assert.equal(isBootstrapSecret("development-bootstrap"), true);
  assert.equal(isBootstrapSecret("wrong-secret"), false);
});
