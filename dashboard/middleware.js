import { NextResponse } from 'next/server'

// Gate everything behind the admin cookie, except the login page + its API.
export function middleware(req) {
  const { pathname } = req.nextUrl
  if (pathname.startsWith('/login') || pathname.startsWith('/api/login') || pathname.startsWith('/legal')) {
    return NextResponse.next()
  }
  const token = req.cookies.get('vf_admin')?.value
  if (token && token === process.env.ADMIN_SESSION_TOKEN) return NextResponse.next()
  const url = req.nextUrl.clone()
  url.pathname = '/login'
  return NextResponse.redirect(url)
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
}
