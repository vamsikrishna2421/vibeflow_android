import { TERMS, renderLegal } from '@/lib/legal'

export const metadata = { title: 'VibeFlow — Terms of Service' }

export default function TermsPage() {
  return <main className="legal">{renderLegal(TERMS)}</main>
}
