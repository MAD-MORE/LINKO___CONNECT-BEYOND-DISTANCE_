import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

const issuer = "linko-control-plane";
const secret = process.env.LINKO_AUTH_SECRET ?? "development-only-change-me";
const bootstrapSecret = process.env.LINKO_BOOTSTRAP_SECRET ?? "development-bootstrap";
const tokenTtlSeconds = 15 * 60;

type TokenPayload = { sub: string; deviceId: string; iat: number; exp: number; iss: string };

function encode(value: unknown): string {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function sign(input: string): string {
  return createHmac("sha256", secret).update(input).digest("base64url");
}

export function issueDeviceToken(userId: string, deviceId: string): string {
  const now = Math.floor(Date.now() / 1000);
  const payload: TokenPayload = { sub: userId, deviceId, iat: now, exp: now + tokenTtlSeconds, iss: issuer };
  const encoded = encode(payload);
  return `${encoded}.${sign(encoded)}`;
}

export function verifyDeviceToken(value: string): TokenPayload | null {
  const [encoded, signature] = value.split(".");
  if (!encoded || !signature) return null;
  const expected = sign(encoded);
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;
  try {
    const payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8")) as TokenPayload;
    if (payload.iss !== issuer || payload.exp <= Math.floor(Date.now() / 1000)) return null;
    if (typeof payload.sub !== "string" || typeof payload.deviceId !== "string") return null;
    return payload;
  } catch {
    return null;
  }
}

export function isBootstrapSecret(value: string | undefined): boolean {
  if (!value) return false;
  const a = Buffer.from(value);
  const b = Buffer.from(bootstrapSecret);
  return a.length === b.length && timingSafeEqual(a, b);
}

export function generateBootstrapSecret(): string {
  return randomBytes(24).toString("base64url");
}
