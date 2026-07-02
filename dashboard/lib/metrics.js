import { admin } from '@/lib/supabase'

const COST_PER_MTOK = 0.30 // USD per 1M tokens (GPT-nano estimate)

/** Pulls users + profiles + devices + status, merges, and computes the overview metrics. */
export async function getDashboardData() {
  const sb = admin()
  const [usersRes, profRes, devRes, statusRes] = await Promise.all([
    sb.auth.admin.listUsers({ page: 1, perPage: 1000 }),
    sb.from('profiles').select('*'),
    sb.from('devices').select('user_id,last_seen'),
    sb.from('app_status').select('*').eq('id', true).maybeSingle(),
  ])
  const users = usersRes?.data?.users || []
  const profiles = profRes?.data || []
  const devices = devRes?.data || []
  const status = statusRes?.data || { maintenance: false, message: '', until: null }

  const profById = Object.fromEntries(profiles.map((p) => [p.id, p]))
  const devByUser = {}
  const weekAgo = Date.now() - 7 * 864e5
  const active7d = new Set()
  for (const d of devices) {
    const e = (devByUser[d.user_id] = devByUser[d.user_id] || { count: 0, last: 0 })
    e.count++
    const t = new Date(d.last_seen).getTime()
    if (t > e.last) e.last = t
    if (t > weekAgo) active7d.add(d.user_id)
  }

  const rows = users.map((u) => {
    const p = profById[u.id] || {}
    const dv = devByUser[u.id] || { count: 0, last: 0 }
    return {
      id: u.id,
      email: u.email || '(no email)',
      created_at: u.created_at,
      is_pro: !!p.is_pro,
      free_used: p.free_used ?? 0,
      free_limit: p.free_limit ?? 50,
      device_limit: p.device_limit ?? 1,
      devices: dv.count,
      last_active: dv.last ? new Date(dv.last).toISOString() : null,
      total_polishes: p.total_polishes ?? 0,
      tokens: (p.prompt_tokens ?? 0) + (p.completion_tokens ?? 0),
    }
  })
  rows.sort((a, b) => (b.last_active || '').localeCompare(a.last_active || ''))

  const sum = (f) => rows.reduce((a, r) => a + f(r), 0)
  const overview = {
    signups: users.length,
    paid: rows.filter((r) => r.is_pro).length,
    free: rows.filter((r) => !r.is_pro).length,
    free_exhausted: rows.filter((r) => !r.is_pro && r.free_used >= r.free_limit).length,
    active7d: active7d.size,
    devices: devices.length,
    polishes: sum((r) => r.total_polishes),
    tokens: sum((r) => r.tokens),
  }
  overview.est_cost_usd = (overview.tokens / 1e6) * COST_PER_MTOK
  return { overview, users: rows, status }
}
