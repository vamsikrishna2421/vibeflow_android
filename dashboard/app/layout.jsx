import './globals.css'

export const metadata = { title: 'VibeFlow admin', description: 'VibeFlow ops dashboard' }

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
