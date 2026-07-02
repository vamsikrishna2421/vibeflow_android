// VibeFlow — claim a device slot (one active mobile + one active desktop per user).
//
// Called by the app right after sign-in. Records this device as the active device for
// its platform; any previously-active device on that platform is thereby superseded and
// will be signed out on its next `polish` call (which checks the slot). Body: { device_id, platform }.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(obj: unknown, status: number): Response {
  return new Response(JSON.stringify(obj), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE, { auth: { persistSession: false } });
  const jwt = (req.headers.get("Authorization") ?? "").replace("Bearer ", "").trim();
  if (!jwt) return json({ error: "unauthenticated" }, 401);
  const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
  if (userErr || !userData?.user) return json({ error: "unauthenticated" }, 401);

  let body: { device_id?: string; platform?: string };
  try { body = await req.json(); } catch { return json({ error: "bad_request" }, 400); }
  const deviceId = (body.device_id ?? "").toString();
  const platform = (body.platform ?? "mobile").toString();
  if (!deviceId) return json({ error: "missing_device_id" }, 400);

  const { error } = await admin.rpc("claim_device", { p_user: userData.user.id, p_device: deviceId, p_platform: platform });
  if (error) return json({ error: "claim_failed", detail: error.message }, 500);
  return json({ ok: true }, 200);
});
