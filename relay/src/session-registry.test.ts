import test from 'node:test';
import assert from 'node:assert/strict';
import { SessionRegistry } from './session-registry.js';

test('registers and authenticates a session', () => {
  const registry = new SessionRegistry();
  const session = registry.register('session_123', '0123456789abcdef0123456789abcdef');
  assert.equal(registry.authenticate(session.id, '0123456789abcdef0123456789abcdef')?.id, session.id);
});

test('rejects a wrong token', () => {
  const registry = new SessionRegistry();
  registry.register('session_123', '0123456789abcdef0123456789abcdef');
  assert.equal(registry.authenticate('session_123', 'ffffffffffffffffffffffffffffffff'), undefined);
});

test('expires idle sessions', () => {
  const registry = new SessionRegistry(10);
  registry.register('session_123', '0123456789abcdef0123456789abcdef');
  assert.equal(registry.removeExpired(Date.now() + 11), 1);
  assert.equal(registry.size(), 0);
});
