import pg from 'pg';

const { Pool } = pg;

export class Store {
  constructor(databaseUrl = process.env.DATABASE_URL) {
    if (!databaseUrl) throw new Error('DATABASE_URL is required');
    this.pool = new Pool({
      connectionString: databaseUrl,
      ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: true } : false,
    });
  }

  async createRequest({ receiverId, providerId, expiresAt }) {
    const { rows } = await this.pool.query(
      `insert into public.linko_connection_requests (receiver_id, provider_id, status, expires_at)
       values ($1,$2,'pending',$3)
       returning id, receiver_id, provider_id, status, created_at, expires_at`,
      [receiverId, providerId, expiresAt]
    );
    return rows[0];
  }

  async getRequest(id) {
    const { rows } = await this.pool.query(
      `select id, receiver_id, provider_id, status, created_at, expires_at
       from public.linko_connection_requests where id=$1`,
      [id]
    );
    return rows[0] || null;
  }

  async listPendingRequests(providerId) {
    const { rows } = await this.pool.query(
      `select id, receiver_id, provider_id, status, created_at, expires_at
       from public.linko_connection_requests
       where provider_id=$1 and status='pending' and expires_at > now()
       order by created_at desc`,
      [providerId]
    );
    return rows;
  }

  async setRequestStatus(id, status) {
    const { rows } = await this.pool.query(
      `update public.linko_connection_requests set status=$2 where id=$1
       returning id, receiver_id, provider_id, status, created_at, expires_at`,
      [id, status]
    );
    return rows[0] || null;
  }

  async createSession(request) {
    const { rows } = await this.pool.query(
      `insert into public.linko_sessions
       (request_id, receiver_id, provider_id, transport, expires_at)
       values ($1,$2,$3,'pending',$4)
       returning *`,
      [request.id, request.receiver_id, request.provider_id, request.expires_at]
    );
    return rows[0];
  }

  async getSession(id) {
    const { rows } = await this.pool.query(`select * from public.linko_sessions where id=$1`, [id]);
    return rows[0] || null;
  }

  async setSessionPublicKey(id, role, publicKey) {
    const column = role === 'receiver' ? 'receiver_public_key' : role === 'provider' ? 'provider_public_key' : null;
    if (!column || !publicKey) throw new Error('invalid key role');
    const { rows } = await this.pool.query(
      `update public.linko_sessions set ${column}=$2 where id=$1 returning *`,
      [id, publicKey]
    );
    return rows[0] || null;
  }

  async closeSession(id) {
    await this.pool.query(
      `update public.linko_sessions set status='closed', closed_at=now() where id=$1`,
      [id]
    );
  }

  async close() {
    await this.pool.end();
  }
}
