import { Pool } from "pg";
import type { Device, Session, SessionState } from "./types.js";

export class PostgresControlPlaneStore {
  private readonly pool: Pool;

  constructor(connectionString = process.env.DATABASE_URL) {
    if (!connectionString) throw new Error("DATABASE_URL_required");
    this.pool = new Pool({ connectionString, max: 10, ssl: process.env.DATABASE_SSL === "false" ? false : { rejectUnauthorized: false } });
  }

  async health(): Promise<void> {
    await this.pool.query("select 1");
  }

  async registerDevice(input: Omit<Device, "id" | "lastSeenAt">): Promise<Device> {
    const result = await this.pool.query(
      `insert into devices (id, user_id, public_key, name, roles, last_seen_at, revoked_at)
       values (gen_random_uuid(), $1, $2, $3, $4, now(), null)
       returning id, user_id, public_key, name, roles, extract(epoch from last_seen_at) * 1000 as last_seen_ms, extract(epoch from revoked_at) * 1000 as revoked_ms`,
      [input.userId, input.publicKey, input.name, input.roles]
    );
    return this.device(result.rows[0]);
  }

  async getDevice(id: string): Promise<Device | undefined> {
    const result = await this.pool.query(
      `select id, user_id, public_key, name, roles, extract(epoch from last_seen_at) * 1000 as last_seen_ms, extract(epoch from revoked_at) * 1000 as revoked_ms from devices where id = $1`,
      [id]
    );
    return result.rows[0] ? this.device(result.rows[0]) : undefined;
  }

  async createSession(receiverDeviceId: string, providerDeviceId: string, ttlSeconds = 300): Promise<Session> {
    const devices = await this.pool.query(`select id, roles, revoked_at from devices where id = any($1::uuid[])`, [[receiverDeviceId, providerDeviceId]]);
    const receiver = devices.rows.find((row) => row.id === receiverDeviceId);
    const provider = devices.rows.find((row) => row.id === providerDeviceId);
    if (!receiver || receiver.revoked_at) throw new Error("receiver_not_available");
    if (!provider || provider.revoked_at) throw new Error("provider_not_available");
    if (!receiver.roles.includes("receiver")) throw new Error("receiver_role_required");
    if (!provider.roles.includes("provider")) throw new Error("provider_role_required");

    const result = await this.pool.query(
      `insert into sessions (id, receiver_device_id, provider_device_id, state, expires_at)
       values (gen_random_uuid(), $1, $2, 'requested', now() + ($3 * interval '1 second'))
       returning *`,
      [receiverDeviceId, providerDeviceId, ttlSeconds]
    );
    return this.session(result.rows[0]);
  }

  async getSession(id: string): Promise<Session | undefined> {
    const result = await this.pool.query(`select * from sessions where id = $1`, [id]);
    return result.rows[0] ? this.session(result.rows[0]) : undefined;
  }

  async transitionSession(id: string, next: SessionState): Promise<Session> {
    const session = await this.getSession(id);
    if (!session) throw new Error("session_not_found");
    if (session.expiresAt <= Date.now() && !["expired", "revoked", "denied"].includes(session.state)) {
      await this.pool.query(`update sessions set state = 'expired' where id = $1`, [id]);
      throw new Error("session_expired");
    }

    const allowed: Record<SessionState, SessionState[]> = {
      requested: ["approved", "denied", "expired", "revoked"],
      approved: ["signaling", "revoked", "expired"],
      signaling: ["connected", "revoked", "expired"],
      connected: ["revoked", "expired"],
      revoked: [], expired: [], denied: []
    };
    if (!allowed[session.state].includes(next)) throw new Error(`invalid_transition:${session.state}->${next}`);

    const result = await this.pool.query(
      `update sessions set state = $2,
         approved_at = case when $2 = 'approved' then now() else approved_at end,
         revoked_at = case when $2 = 'revoked' then now() else revoked_at end
       where id = $1 returning *`,
      [id, next]
    );
    return this.session(result.rows[0]);
  }

  async revokeDevice(id: string): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("begin");
      const device = await client.query(`select id from devices where id = $1`, [id]);
      if (!device.rowCount) throw new Error("device_not_found");
      await client.query(`update devices set revoked_at = now() where id = $1`, [id]);
      await client.query(`update sessions set state = 'revoked', revoked_at = now() where (receiver_device_id = $1 or provider_device_id = $1) and state not in ('revoked','expired','denied')`, [id]);
      await client.query("commit");
    } catch (error) {
      await client.query("rollback");
      throw error;
    } finally {
      client.release();
    }
  }

  async close(): Promise<void> { await this.pool.end(); }

  private device(row: any): Device {
    return { id: row.id, userId: row.user_id, publicKey: row.public_key, name: row.name, roles: row.roles, lastSeenAt: Number(row.last_seen_ms), ...(row.revoked_ms ? { revokedAt: Number(row.revoked_ms) } : {}) };
  }

  private session(row: any): Session {
    return { id: row.id, receiverDeviceId: row.receiver_device_id, providerDeviceId: row.provider_device_id, state: row.state, createdAt: new Date(row.created_at).getTime(), expiresAt: new Date(row.expires_at).getTime(), ...(row.approved_at ? { approvedAt: new Date(row.approved_at).getTime() } : {}), ...(row.revoked_at ? { revokedAt: new Date(row.revoked_at).getTime() } : {}) };
  }
}
