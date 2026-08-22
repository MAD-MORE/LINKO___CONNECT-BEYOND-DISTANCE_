import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

const ALGORITHM = "aes-256-gcm";
const NONCE_BYTES = 12;
const TAG_BYTES = 16;
const MAX_PLAINTEXT = 64 * 1024;

export function encryptPacket(key: Buffer, plaintext: Uint8Array): Buffer {
  if (key.length !== 32) throw new Error("invalid_tunnel_key");
  if (plaintext.length > MAX_PLAINTEXT) throw new Error("tunnel_frame_too_large");
  const nonce = randomBytes(NONCE_BYTES);
  const cipher = createCipheriv(ALGORITHM, key, nonce);
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([nonce, ciphertext, tag]);
}

export function decryptPacket(key: Buffer, frame: Uint8Array): Buffer {
  if (key.length !== 32) throw new Error("invalid_tunnel_key");
  if (frame.length < NONCE_BYTES + TAG_BYTES) throw new Error("invalid_tunnel_frame");
  const nonce = Buffer.from(frame.subarray(0, NONCE_BYTES));
  const tag = Buffer.from(frame.subarray(frame.length - TAG_BYTES));
  const ciphertext = Buffer.from(frame.subarray(NONCE_BYTES, frame.length - TAG_BYTES));
  if (ciphertext.length > MAX_PLAINTEXT) throw new Error("tunnel_frame_too_large");
  const decipher = createDecipheriv(ALGORITHM, key, nonce);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}
