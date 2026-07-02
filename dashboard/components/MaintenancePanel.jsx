'use client'
import { useState } from 'react'
import { useRouter } from 'next/navigation'

export default function MaintenancePanel({ status }) {
  const router = useRouter()
  const on = !!status?.maintenance
  const [message, setMessage] = useState(
    status?.message || 'VibeFlow AI is down for ~30 min — keep dictating, offline mode still works. Sorry!',
  )
  const [minutes, setMinutes] = useState(30)
  const [busy, setBusy] = useState(false)

  async function call(turnOn) {
    setBusy(true)
    await fetch('/api/admin/maintenance', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ on: turnOn, message, minutes }),
    })
    setBusy(false); router.refresh()
  }

  return (
    <section className={'panel' + (on ? ' danger' : '')}>
      <div className="panel-h">
        <h2>Maintenance / kill-switch</h2>
        <span className={'pill ' + (on ? 'pill-on' : 'pill-off')}>{on ? 'DOWN' : 'Operational'}</span>
      </div>
      {on && status?.until && <p className="muted">Scheduled until {new Date(status.until).toLocaleString()}</p>}
      <textarea value={message} onChange={(e) => setMessage(e.target.value)} rows={2}
        placeholder="Message shown in every app" />
      <div className="row">
        <label className="muted">Minutes&nbsp;
          <input type="number" value={minutes} min={1} style={{ width: 70 }}
            onChange={(e) => setMinutes(e.target.value)} />
        </label>
        {on
          ? <button disabled={busy} className="ok" onClick={() => call(false)}>End maintenance</button>
          : <button disabled={busy} className="warn" onClick={() => call(true)}>Broadcast downtime</button>}
      </div>
      <p className="muted small">Stops all OpenAI spend immediately (checked before the key is used) and every app shows the message + falls back to offline mode.</p>
    </section>
  )
}
