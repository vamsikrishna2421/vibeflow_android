'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'

export default function Login() {
  const [pw, setPw] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const router = useRouter()

  async function submit(e) {
    e.preventDefault()
    setBusy(true); setErr('')
    const r = await fetch('/api/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: pw }),
    })
    setBusy(false)
    if (r.ok) router.replace('/')
    else setErr('Wrong password')
  }

  return (
    <main className="login">
      <form onSubmit={submit} className="loginbox">
        <h1>VibeFlow <span>admin</span></h1>
        <input type="password" placeholder="Admin password" value={pw}
          onChange={(e) => setPw(e.target.value)} autoFocus />
        {err && <div className="err">{err}</div>}
        <button disabled={busy}>{busy ? 'Checking…' : 'Sign in'}</button>
      </form>
    </main>
  )
}
