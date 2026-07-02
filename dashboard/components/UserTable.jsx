'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'

export default function UserTable({ users }) {
  const router = useRouter()
  const [busy, setBusy] = useState('')
  const [q, setQ] = useState('')

  async function act(id, action, value) {
    setBusy(id + action)
    await fetch('/api/admin/user', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, action, value }),
    })
    setBusy(''); router.refresh()
  }
  function seats(u) {
    const v = prompt('Device seats for ' + u.email + '?', String(u.device_limit))
    if (v !== null) act(u.id, 'seats', v)
  }

  const shown = users.filter((u) => u.email.toLowerCase().includes(q.toLowerCase()))
  return (
    <section className="users">
      <div className="panel-h">
        <h2>Users ({users.length})</h2>
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
            {shown.map((u) => (
              <tr key={u.id}>
                <td className="email">{u.email}</td>
                <td>{u.is_pro ? <span className="pro">PRO</span> : <span className="muted">free</span>}</td>
                <td>{u.free_used}/{u.free_limit}</td>
                <td>{u.device_limit}</td>
                <td>{u.devices}</td>
                <td>{u.total_polishes}</td>
                <td>{u.tokens.toLocaleString()}</td>
                <td className="muted">{u.last_active ? new Date(u.last_active).toLocaleString() : '—'}</td>
                <td className="actions">
                  <button onClick={() => act(u.id, 'pro', !u.is_pro)}>{u.is_pro ? 'Unset Pro' : 'Make Pro'}</button>
                  <button onClick={() => seats(u)}>Seats</button>
                  <button onClick={() => act(u.id, 'reset')}>Reset free</button>
                  <button className="warn-ghost" onClick={() => act(u.id, 'signout')}>Sign out</button>
                </td>
              </tr>
            ))}
            {shown.length === 0 && <tr><td colSpan={9} className="muted" style={{ textAlign: 'center', padding: 24 }}>No users</td></tr>}
          </tbody>
        </table>
      </div>
    </section>
  )
}
