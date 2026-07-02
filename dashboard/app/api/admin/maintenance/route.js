import { NextResponse } from 'next/server'
import { admin } from '@/lib/supabase'

// Start/end the maintenance broadcast (the polish kill-switch). on=true sets the message
// and an optional `until`; on=false clears it.
export async function POST(req) {
  const { on, message, minutes } = await req.json().catch(() => ({}))
  const until = on && minutes ? new Date(Date.now() + Number(minutes) * 60000).toISOString() : null
  const { error } = await admin()
    .from('app_status')
    .update({ maintenance: !!on, message: message || '', until, updated_at: new Date().toISOString() })
    .eq('id', true)
  if (error) return NextResponse.json({ ok: false, error: error.message }, { status: 500 })
  return NextResponse.json({ ok: true })
}
