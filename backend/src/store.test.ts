import assert from "node:assert/strict";
import { test } from "node:test";
import { ControlPlaneStore } from "./store.js";

test("session lifecycle is idempotent at the state-machine boundary", () => {
  const store = new ControlPlaneStore();
  const provider = store.registerDevice({ userId: "u-provider", publicKey: "pk-provider", name: "Provider", roles: ["provider"] });
  const receiver = store.registerDevice({ userId: "u-receiver", publicKey: "pk-receiver", name: "Receiver", roles: ["receiver"] });
  const session = store.createSession(receiver.id, provider.id);

  assert.equal(session.state, "requested");
  assert.equal(store.transitionSession(session.id, "approved").state, "approved");
  assert.equal(store.transitionSession(session.id, "signaling").state, "signaling");
  assert.equal(store.transitionSession(session.id, "connected").state, "connected");
  assert.throws(() => store.transitionSession(session.id, "approved"), /invalid_transition/);
});

test("device revocation propagates to active sessions", () => {
  const store = new ControlPlaneStore();
  const provider = store.registerDevice({ userId: "u-provider", publicKey: "pk-provider", name: "Provider", roles: ["provider"] });
  const receiver = store.registerDevice({ userId: "u-receiver", publicKey: "pk-receiver", name: "Receiver", roles: ["receiver"] });
  const session = store.createSession(receiver.id, provider.id);

  store.revokeDevice(provider.id);
  assert.equal(store.getSession(session.id)?.state, "revoked");
});
