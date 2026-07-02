// Public legal text — mirror of the in-app copy (LegalScreen.kt). Static; update both together.
export const UPDATED = '27 June 2026'
export const CONTACT = 'vamsy.24@gmail.com'

export const PRIVACY = `
# VibeFlow Privacy Policy
Last updated: ${UPDATED}

VibeFlow is a voice keyboard that turns speech into text, with optional AI "Smart Formatting." This policy explains what we collect, why, and your choices. We built VibeFlow to keep as much as possible on your device.

## Your speech stays on your device
Your voice is transcribed entirely on your device. Audio is never uploaded, stored, or sent to us or anyone else. We do not record or keep your audio.

## Your history is local
The history of your dictations is stored only on your device. It is not uploaded to our servers. Uninstalling the app removes it.

## AI Smart Formatting (optional)
Smart Formatting is optional. When you use it, only the text of your dictation (never audio) is sent for AI processing, depending on the mode you choose:
- Private mode: nothing is sent anywhere — formatting happens on-device.
- Your own key (BYOK): the text goes directly from your device to the AI provider whose key you entered (e.g. OpenAI), under that provider's terms.
- VibeFlow managed: the text is sent through VibeFlow's server to our AI provider (OpenAI), formatted, and returned. We do NOT store the text of your dictations on our servers — only counts (polishes used) and token totals, for quotas, billing, and abuse prevention.

## Account information (managed tier only)
If you sign in to use the managed tier, we store, via our backend host (Supabase):
- your email address (from Google sign-in),
- a random device identifier (to enforce device limits and prevent abuse),
- usage counts (polishes, token totals) and your plan/entitlement.
We use this only to run the service. We do not sell your data or use it for advertising.

## Third parties
- Google — for sign-in. Google's privacy policy applies.
- OpenAI — for AI text formatting. OpenAI's policies apply to text processed by their models.
- Supabase — our backend and database host.

## Permissions
- Microphone — to transcribe your speech on-device. Required for voice input.
- Notifications and "display over other apps" — optional, for recording status and the floating mic.

## Data retention and deletion
History stays on your device. For managed-tier account data, email us at ${CONTACT} to request deletion and we will remove your account information.

## Children
VibeFlow is not directed to children under 13 (or the minimum age required in your country).

## Changes
We may update this policy and will change the "Last updated" date above.

## Contact
Questions or data requests: ${CONTACT}
`

export const TERMS = `
# VibeFlow Terms of Service
Last updated: ${UPDATED}

By using VibeFlow, you agree to these terms.

## The service
VibeFlow is a voice keyboard with optional AI "Smart Formatting." Speech is transcribed on your device; AI formatting is optional, as described in our Privacy Policy.

## Your responsibilities
- Use VibeFlow lawfully and don't misuse it — no attempts to break, overload, or abuse the service or its quotas.
- You are responsible for the content you create and send using VibeFlow.

## AI output
AI-formatted text is generated automatically and may be inaccurate or not what you intended. Review anything before you send or rely on it. You are responsible for the final text.

## Free tier and subscriptions
- The managed tier includes a free allowance (a one-time welcome amount, then a recurring weekly amount) that may change over time.
- Paid plans, when offered, are billed through Google Play and governed by Google Play's terms. You can manage or cancel a subscription in Google Play.

## Accounts and fair use
We may limit the number of devices per account and rate-limit usage to keep the service fair and prevent abuse. We may suspend accounts that abuse the service.

## Availability
The service is provided "as is," without warranties. We may change, suspend, or discontinue features, and there may be downtime.

## Limitation of liability
To the extent permitted by law, VibeFlow and its developers are not liable for indirect or consequential damages, or for issues arising from AI output or third-party providers.

## Termination
You can stop using VibeFlow anytime by uninstalling. We may terminate or limit access for violations of these terms.

## Governing law
These terms are governed by the laws of India.

## Contact
${CONTACT}
`

/** Tiny markdown-ish renderer → React nodes (# / ## / - / blank). */
export function renderLegal(text) {
  return text.trim().split('\n').map((line, i) => {
    if (line.startsWith('# ')) return <h1 key={i}>{line.slice(2)}</h1>
    if (line.startsWith('## ')) return <h2 key={i}>{line.slice(3)}</h2>
    if (line.startsWith('- ')) return <li key={i}>{line.slice(2)}</li>
    if (!line.trim()) return <div key={i} style={{ height: 6 }} />
    return <p key={i}>{line}</p>
  })
}
