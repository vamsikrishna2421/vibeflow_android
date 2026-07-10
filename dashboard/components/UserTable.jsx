'use client'
import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'

export default function UserTable({ users }) {
  const router = useRouter()
  const [busy, setBusy] = useState('')
  const [q, setQ] = useState('')
  // Local copy so an action reflects INSTANTLY, even if the server refresh is
  // slow or the RSC refetch errors (previously the row never updated → looked
  // like the button did nothing). Server truth re-syncs whenever `users` changes.
  const [rows, setRows] = useState(users)
  useEffect(() => { setRows(users) }, [users])

  // Only mounted-client timestamps are rendered, so server HTML (UTC/server
  // locale) never mismatches the client (local locale) → no hydration error.
  const [mounted, setMounted] = useState(false)
  useEffect(() => { setMounted(true) }, [])

  function applyOptimistic(id, action, value) {
    setRows((rs) => rs.map((r) => {
      if (r.id !== id) return r
      if (action === 'pro') return { ...r, is_pro: !!value }
      if (action === 'seats') return { ...r, device_limit: Math.max(1, Number(value) || 1) }
      if (action === 'reset') return { ...r, free_used: 0 }
      if (action === 'signout') return { ...r, devices: 0 }
      return r
    }))
  }

  async function act(id, action, value) {
    setBusy(id + action)
    const snapshot = rows
    applyOptimistic(id, action, value) // reflect immediately
    try {
      const res = await fetch('/api/admin/user', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, action, value }),
      })
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error || res.statusText)
    } catch (e) {
      setRows(snapshot) // revert on failure
      alert('Action failed: ' + (e?.message || 'unknown error'))
    } finally {
      setBusy('')
      router.refresh() // pull server truth; UI already updated so a slow/failed refresh is harmless
    }
  }

  function seats(u) {
    const v = prompt('Device seats for ' + u.email + '?', String(u.device_limit))
    if (v !== null) act(u.id, 'seats', v)
  }

  const shown = rows.filter((u) => u.email.toLowerCase().includes(q.toLowerCase()))
  return (
    <section className="users">
      <div className="panel-h">
        <h2>Users ({rows.length})</h2>
        <input className="search" placeholder="Search email…" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div className="tablewrap">
        <table>
          <thead>
            <tr>
              <th>Email</th><th>Plan</th><th>Free</th><th>Seats</th><th>Devices</th>
              <th>Polishes</th><th>Tokens</th><th>Last active</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((u) => {
              const proBusy = busy === u.id + 'pro'
              return (
                <tr key={u.id}>
                  <td className="email">{u.email}</td>
                  <td>{u.is_pro ? <span className="pro">PRO</span> : <span className="muted">free</span>}</td>
                  <td>{u.free_used}/{u.free_limit}</td>
                  <td>{u.device_limit}</td>
                  <td>{u.devices}</td>
                  <td>{u.total_polishes}</td>
                  <td>{u.tokens.toLocaleString()}</td>
                  <td className="muted" suppressHydrationWarning>
                    {mounted && u.last_active ? new Date(u.last_active).toLocaleString() : u.last_active ? '…' : '—'}
                  </td>
                  <td className="actions">
                    <button disabled={proBusy} onClick={() => act(u.id, 'pro', !u.is_pro)}>
                      {proBusy ? '…' : u.is_pro ? 'Unset Pro' : 'Make Pro'}
                    </button>
                    <button disabled={!!busy} onClick={() => seats(u)}>Seats</button>
                    <button disabled={!!busy} onClick={() => act(u.id, 'reset')}>Reset free</button>
                    <button className="warn-ghost" disabled={!!busy} onClick={() => act(u.id, 'signout')}>Sign out</button>
                  </td>
                </tr>
              )
            })}
            {shown.length === 0 && <tr><td colSpan={9} className="muted" style={{ textAlign: 'center', padding: 24 }}>No users</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  )
}
