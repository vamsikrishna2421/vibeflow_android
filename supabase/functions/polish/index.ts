// VibeFlow — managed Smart Formatting proxy (Supabase Edge Function).
//
// The keyboard sends { text, system } plus the signed-in user's JWT. We:
//   1. verify the user,
//   2. atomically reserve one polish (pro, or under the free-50 limit) — else 402,
//   3. call OpenAI GPT-nano with VIBEFLOW'S key (a server secret, never in the app),
//   4. record token usage, and
//   5. return { text, remaining, isPro, promptTokens, completionTokens }.
//
// On any upstream failure the reserved polish is refunded, so a failure costs nothing.
//
// Secrets used (set OPENAI_API_KEY yourself; the SUPABASE_* ones are auto-injected):
//   OPENAI_API_KEY            — a VibeFlow OpenAI key (rotate the one pasted in-app earlier)
//   POLISH_MODEL              — optional, defaults to gpt-4.1-nano
//   SUPABASE_URL              — auto
//   SUPABASE_SERVICE_ROLE_KEY — auto (used for metering; bypasses RLS)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const MODEL = Deno.env.get("POLISH_MODEL") ?? "gpt-4.1-nano";

// Hard cost guard: reject oversized input so nobody can run our key as a bulk LLM
// (e.g. pasting a huge doc into a capture and hitting Polish). Must stay in sync with
// the client cap (SmartFormatter.MAX_INPUT_CHARS). ~20000 chars ≈ 20 min of fast
// expert speech (~160 wpm) — covers any real dictation, blocks abuse (~5k tokens).
const MAX_TEXT = 20000;
const MAX_SYSTEM = 4000;

// ── L3 polish prompt (server-side) ──────────────────────────────────────────────────
// Mirrors the Kotlin SmartFormatter, but lives HERE so prompt quality can be improved by a
// server deploy alone — no app update (the "silent upgrade" the managed tier is built for).
// Validated by the on-device eval harness: 38% → 92% on 104 real-dictation cases. When the
// client sends `style`, we build the prompt here; older apps that send `system` still work.
const NORMALIZE = `NORMALIZE spoken forms to their standard WRITTEN form:
- Numbers: spelled-out to digits, INCLUDING small numbers, whenever they are quantities, counts, measurements, durations, money or list counts ("twenty five" to "25", "two loaves" to "2 loaves", "ten days" to "10 days", "three hundred forty" to "340").
- List markers: when the speaker enumerates items with "number one / number two / number three" (or "first / second / third" as list markers), ALWAYS convert them to a numbered list — "1.", "2.", "3." — putting each item on its own line when there are two or more items.
- Quarters & fiscal: "quarter three" to "Q3"; "fiscal year twenty four" to "FY24". Years: "twenty twenty five" to "2025".
- Money: "fifty dollars" to "$50". Percent: "ten percent" to "10%".
- Time: "three thirty pm" to "3:30 PM". Dates: "june twenty fifth" to "June 25".
- Units (abbreviate with the digit): km, m, kg, g, ml, L, GB, MB ("five kilometers" to "5 km", "two point three gigabytes" to "2.3 GB").
- Spoken acronyms: "a p i" to "API"; "u s a" to "USA".
- Spoken punctuation — replace the SPOKEN WORD with the exact mark, never a different one and never the word itself: "comma" -> ",", "period"/"full stop" -> ".", "question mark" -> "?", "exclamation point" -> "!", "new line" -> a line break, "new paragraph" -> a blank line.
- Obvious homophone slips, ONLY when context is unambiguous: by->buy, to->too, there->their, its->it's, your->you're, then->than. A trailing "to"/"two" that means "also/as well" becomes "too" and stays in the sentence.
- Remove fillers ("um", "uh", "you know", "i mean", filler "like", "so basically") and collapse stutters/repeats ("the the" -> "the").`;

const PRESERVE = `PRESERVE — never change these:
- The speaker's meaning, intent, facts, numbers and order. Do NOT paraphrase, summarize, shorten, expand or invent anything beyond the formatting requested above.
- Real names, brands and technical terms exactly as said (e.g. ColorOS, OnePlus, Kubernetes, VibeFlow, WhatsApp, Docker). Never swap a real proper noun for a common word.`;

const INTRO = `You convert raw phone dictation (speech-to-text output) into clean, correctly written text — exactly what the speaker meant to type.

FIX: grammar, spelling, punctuation, capitalization, and sentence/paragraph breaks. Standalone "i" becomes "I". Capitalize sentence starts and obvious names/places. Prefer commas and periods over dashes; never output an em-dash.`;

function buildSystemPrompt(style: string, userName: string, userTitle: string, appName: string): string {
  switch (style) {
    case "message":
      return `Clean up the dictated text into a clear, natural chat message — keep it casual but correctly written.\n\n${NORMALIZE}\n\n${PRESERVE}\n\nReturn ONLY the message.`;
    case "structured":
      return `Clean up and structure the dictated text: fix grammar and punctuation, add sentence/paragraph breaks, and use bullets or numbering only if the speaker clearly listed items.\n\n${NORMALIZE}\n\n${PRESERVE}\n\nReturn ONLY the formatted text, with no preamble.`;
    case "auto": {
      const where = appName ? `the app "${appName}"` : "an app";
      const signoff = userName ? `\n\nIf a sign-off is appropriate, sign as "${userName}"${userTitle ? `, ${userTitle}` : ""}.` : "";
      return `The user is dictating into ${where}. Lightly shape it to suit that context — an email client to an email shape, a chat app to a clean casual message, a notes app to tidy notes — without changing what was said.\n\n${NORMALIZE}\n\n${PRESERVE}${signoff}\n\nReturn ONLY the formatted text, with no preamble.`;
    }
    case "email": {
      let s = `Rewrite the dictated text as a clear, professional email. ALWAYS begin with a brief greeting on its own line (e.g. "Hi,"). Write a well-structured body. ALWAYS end with a courteous sign-off (e.g. "Best regards,")`;
      if (userName) {
        s += ` on its own line, then the sender's name "${userName}"`;
        if (userTitle) s += ` and on the next line the title "${userTitle}"`;
        s += ".";
      } else {
        s += `. If a sender name is unknown, leave a "[Your name]" placeholder.`;
      }
      s += ` Normalize numbers, dates, times, money, percentages and units to standard written form (e.g. numbered lists as "1." "2.", Q3, $50, 10%, 3:30 PM, June 25). Preserve every fact, name and brand. Return ONLY the email.`;
      return s;
    }
    case "notes":
      return `Reformat the dictated text into concise, well-organized notes: short bullet points and small headings where helpful. Normalize numbers, dates, times, money and units to standard written form (e.g. numbered lists as "1." "2.", Q3, $50, 3:30 PM). Preserve all information, names and brands. Return ONLY the notes.`;
    case "cleanup":
    default:
      return `${INTRO}\n\n${NORMALIZE}\n\n${PRESERVE}\n\nOutput ONLY the cleaned text — no preamble, quotes or explanation.`;
  }
}

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(obj: unknown, status: number): Response {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE, { auth: { persistSession: false } });

  // 0. Maintenance / kill-switch: short-circuit BEFORE any OpenAI spend. Flipping
  //    app_status.maintenance=true stops all calls (even with a compromised key) and
  //    broadcasts the message; the app shows it and falls back to offline mode.
  const { data: status } = await admin.from("app_status").select("maintenance,message,until").eq("id", true).maybeSingle();
  if (status?.maintenance) {
    return json({ error: "maintenance", message: status.message ?? "", until: status.until ?? null }, 503);
  }

  // 1. Authenticate the caller.
  const jwt = (req.headers.get("Authorization") ?? "").replace("Bearer ", "").trim();
  if (!jwt) return json({ error: "unauthenticated" }, 401);
  const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
  if (userErr || !userData?.user) return json({ error: "unauthenticated" }, 401);
  const userId = userData.user.id;

  // 2. Validate the request.
  let body: {
    text?: string; system?: string; style?: string;
    userName?: string; userTitle?: string; appName?: string;
    device_id?: string; platform?: string;
  };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const text = (body.text ?? "").toString();
  const style = (body.style ?? "").toString();
  const deviceId = (body.device_id ?? "").toString();
  const platform = (body.platform ?? "mobile").toString();
  if (!text.trim()) return json({ error: "empty" }, 400);

  // Prefer the SERVER-built prompt (from `style`) so prompt quality ships without an app
  // update; fall back to a client-sent `system` for older app versions that don't send a style.
  const system = style
    ? buildSystemPrompt(style, (body.userName ?? "").toString(), (body.userTitle ?? "").toString(), (body.appName ?? "").toString())
    : (body.system ?? "").toString();
  if (text.length > MAX_TEXT || system.length > MAX_SYSTEM) return json({ error: "too_long" }, 413);

  // Device binding: a superseded device (a newer sign-in took this platform's slot) is
  // rejected before it can consume any quota. The app signs out on 409.
  if (deviceId) {
    const { data: holds, error: devErr } = await admin.rpc("check_device", {
      p_user: userId, p_device: deviceId, p_platform: platform,
    });
    if (!devErr && holds === false) return json({ error: "device_superseded" }, 409);
  }

  // 3. Reserve one polish (atomic entitlement check).
  const { data: rsvData, error: rsvErr } = await admin.rpc("reserve_polish", { p_user: userId, p_device: deviceId || null });
  if (rsvErr) return json({ error: "metering_failed", detail: rsvErr.message }, 500);
  const reserve = Array.isArray(rsvData) ? rsvData[0] : rsvData;
  if (!reserve?.allowed) {
    return json({ error: "limit_reached", remaining: 0, isPro: reserve?.is_pro ?? false }, 402);
  }

  // 4. Proxy to OpenAI; refund the reservation on any failure.
  const refund = async () => {
    await admin.rpc("refund_polish", { p_user: userId, p_device: deviceId || null, p_bucket: reserve?.bucket ?? null });
  };
  let oai: Response;
  try {
    oai = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${OPENAI_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: MODEL,
        temperature: 0.3,
        messages: [
          ...(system.trim() ? [{ role: "system", content: system }] : []),
          { role: "user", content: text },
        ],
      }),
    });
  } catch {
    await refund();
    return json({ error: "upstream_unreachable" }, 502);
  }
  if (!oai.ok) {
    await refund();
    return json({ error: "upstream_error", status: oai.status }, 502);
  }

  const data = await oai.json();
  const polished = (data?.choices?.[0]?.message?.content ?? "").toString().trim();
  const pt = Number(data?.usage?.prompt_tokens ?? 0);
  const ct = Number(data?.usage?.completion_tokens ?? 0);
  if (!polished) {
    await refund();
    return json({ error: "empty_result" }, 502);
  }

  // 5. Record token usage (best-effort) and return.
  await admin.rpc("record_tokens", { p_user: userId, p_prompt: pt, p_completion: ct });
  return json(
    { text: polished, remaining: reserve.remaining, isPro: reserve.is_pro, promptTokens: pt, completionTokens: ct },
    200,
  );
});
