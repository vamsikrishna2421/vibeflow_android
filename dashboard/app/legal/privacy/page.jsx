import { PRIVACY, renderLegal } from '@/lib/legal'

export const metadata = { title: 'VibeFlow — Privacy Policy' }

export default function PrivacyPage() {
  return <main className="legal">{renderLegal(PRIVACY)}</main>
}
