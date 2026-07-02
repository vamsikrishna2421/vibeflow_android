import { NextResponse } from 'next/server'
import { admin } from '@/lib/supabase'

// Per-user admin actions: toggle Pro, set device seats, reset free usage, sign out all devices.
export async function POST(req) {
  const { id, action, value } = await req.json().catch(() => ({}))
  if (!id || !action) return NextResponse.json({ ok: false, error: 'bad request' }, { status: 400 })
  const sb = admin()
  let error
  if (action === 'pro') {
    ({ error } = await sb.from('profiles').update({ is_pro: !!value }).eq('id', id))
  } else if (action === 'seats') {
    const n = Math.max(1, Number(value) || 1)
    ;({ error } = await sb.from('profiles').update({ device_limit: n }).eq('id', id))
  } else if (action === 'reset') {
    ({ error } = await sb.from('profiles').update({ free_used: 0 }).eq('id', id))
  } else if (action === 'signout') {
    ({ error } = await sb.from('devices').delete().eq('user_id', id))
  } else {
    return NextResponse.json({ ok: false, error: 'unknown action' }, { status: 400 })
  }
  if (error) return NextResponse.json({ ok: false, error: error.message }, { status: 500 })
  return NextResponse.json({ ok: true })
}
