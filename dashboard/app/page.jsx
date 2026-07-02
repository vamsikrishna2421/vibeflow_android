import { getDashboardData } from '@/lib/metrics'
import MaintenancePanel from '@/components/MaintenancePanel'
import UserTable from '@/components/UserTable'

export const dynamic = 'force-dynamic' // always read live

export default async function Page() {
  const { overview, users, status } = await getDashboardData()
  const cards = [
    ['Signups', overview.signups],
    ['Paid', overview.paid],
    ['Free', overview.free],
    ['Free exhausted', overview.free_exhausted],
    ['Active (7d)', overview.active7d],
    ['Devices', overview.devices],
    ['Polishes', overview.polishes],
    ['Tokens', overview.tokens.toLocaleString()],
    ['Est. cost', '$' + overview.est_cost_usd.toFixed(4)],
  ]
  return (
    <main className="wrap">
      <header className="top">
        <h1>VibeFlow <span>admin</span></h1>
        <form action="/api/logout" method="post"><button className="ghost">Sign out</button></form>
      </header>
      <section className="cards">
        {cards.map(([k, v]) => (
          <div key={k} className="card"><div className="k">{k}</div><div className="v">{v}</div></div>
        ))}
      </section>
      <MaintenancePanel status={status} />
      <UserTable users={users} />
      <p className="foot">Live from Supabase · cost est. at $0.30 / 1M tokens · refresh the page to update</p>
    </main>
  )
}
