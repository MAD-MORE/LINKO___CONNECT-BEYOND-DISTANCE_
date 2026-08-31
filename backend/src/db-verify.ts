import { Pool } from "pg";

const connectionString = process.env.LINKO_DATABASE_URL ?? process.env.DATABASE_URL;

if (!connectionString) {
  throw new Error("LINKO_DATABASE_URL_required");
}

const pool = new Pool({
  connectionString,
  max: 1,
  ssl: process.env.DATABASE_SSL === "false" ? false : { rejectUnauthorized: false },
});

try {
  const result = await pool.query<{ ok: number }>("select 1 as ok");
  if (result.rows[0]?.ok !== 1) throw new Error("database_health_check_failed");
  const schema = await pool.query<{ devices: boolean; sessions: boolean }>(
    `select
       to_regclass('public.devices') is not null as devices,
       to_regclass('public.sessions') is not null as sessions`
  );
  const row = schema.rows[0];
  if (!row?.devices || !row.sessions) throw new Error("linko_schema_missing");
  console.log("LINKO production database connection verified");
} finally {
  await pool.end();
}
