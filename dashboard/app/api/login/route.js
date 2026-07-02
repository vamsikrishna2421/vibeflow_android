import { NextResponse } from 'next/server'

export async function POST(req) {
  const { password } = await req.json().catch(() => ({}))
  if (!password || password !== process.env.ADMIN_PASSWORD) {
    return NextResponse.json({ ok: false }, { status: 401 })
  }
  const res = NextResponse.json({ ok: true })
  res.cookies.set('vf_admin', process.env.ADMIN_SESSION_TOKEN || '', {
    httpOnly: true, secure: true, sameSite: 'lax', path: '/', maxAge: 60 * 60 * 24 * 30,
  })
  return res
}
