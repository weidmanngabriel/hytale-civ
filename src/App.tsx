import { useEffect, useRef, useState } from 'react'
import {
  readActiveGoogleAccount,
  readStoredGoogleAccounts,
  removeGoogleAccount,
  setActiveGoogleAccount,
  storeGoogleAccount,
  type GoogleUser,
} from './auth/google'

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

function App() {
  const [accounts, setAccounts] = useState<GoogleUser[]>([])
  const [user, setUser] = useState<GoogleUser | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [googleReady, setGoogleReady] = useState(false)
  const googleButtonRef = useRef<HTMLDivElement>(null)
  const addGoogleButtonRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const storedAccounts = readStoredGoogleAccounts()
    setAccounts(storedAccounts)
    setUser(readActiveGoogleAccount(storedAccounts))
  }, [])

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID) return

    const target = user ? (menuOpen ? addGoogleButtonRef.current : null) : googleButtonRef.current
    if (!target) return

    const initializeGoogle = () => {
      if (!window.google?.accounts.id) return false

      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        auto_select: false,
        cancel_on_tap_outside: true,
        callback: ({ credential }) => {
          const nextUser = storeGoogleAccount(credential)
          if (!nextUser) return
          const nextAccounts = readStoredGoogleAccounts()
          setAccounts(nextAccounts)
          setUser(nextUser)
          setMenuOpen(false)
        },
      })

      target.replaceChildren()
      window.google.accounts.id.renderButton(target, {
        type: 'standard',
        theme: 'outline',
        size: 'medium',
        shape: 'pill',
        text: user ? 'continue_with' : 'signin_with',
        locale: 'de',
        width: 220,
      })
      setGoogleReady(true)
      return true
    }

    setGoogleReady(false)
    if (initializeGoogle()) return

    const timer = window.setInterval(() => {
      if (initializeGoogle()) window.clearInterval(timer)
    }, 150)

    return () => window.clearInterval(timer)
  }, [menuOpen, user?.email])

  useEffect(() => {
    if (!menuOpen) return
    const closeMenu = (event: MouseEvent) => {
      const target = event.target as HTMLElement
      if (!target.closest('.account-area')) setMenuOpen(false)
    }
    window.addEventListener('click', closeMenu)
    return () => window.removeEventListener('click', closeMenu)
  }, [menuOpen])

  const switchAccount = (nextUser: GoogleUser) => {
    setActiveGoogleAccount(nextUser.email)
    setUser(nextUser)
    setMenuOpen(false)
  }

  const logout = () => {
    if (!user) return
    const { accounts: nextAccounts, active } = removeGoogleAccount(user.email)
    setAccounts(nextAccounts)
    setUser(active)
    setMenuOpen(false)
    if (!active) window.google?.accounts.id.disableAutoSelect()
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="PWA Template Startseite">
          <span className="brand-mark">P</span>
          <span>PWA Template</span>
        </a>

        <div className="header-actions">
          <span className="status-pill"><span /> PWA ready</span>
          <div className="account-area">
            {user ? (
              <>
                <button className="account-button" type="button" onClick={() => setMenuOpen((open) => !open)} aria-expanded={menuOpen}>
                  {user.picture ? <img src={user.picture} alt="" referrerPolicy="no-referrer" /> : <span className="avatar-fallback">{initials(user.name)}</span>}
                  <span className="account-button-copy"><strong>{user.name}</strong><small>{user.email}</small></span>
                  <span aria-hidden="true">⌄</span>
                </button>

                {menuOpen && (
                  <div className="account-menu">
                    <p className="menu-label">Google-Konten</p>
                    <div className="account-list">
                      {accounts.map((account) => {
                        const active = account.email.toLowerCase() === user.email.toLowerCase()
                        return (
                          <button key={account.email} type="button" className={`account-option${active ? ' is-active' : ''}`} onClick={() => switchAccount(account)}>
                            {account.picture ? <img src={account.picture} alt="" referrerPolicy="no-referrer" /> : <span className="avatar-fallback small">{initials(account.name)}</span>}
                            <span><strong>{account.name}</strong><small>{account.email}</small></span>
                            {active && <span className="active-dot" title="Aktiv" />}
                          </button>
                        )
                      })}
                    </div>
                    <div className="menu-divider" />
                    <p className="menu-label">Weiteres Konto hinzufügen</p>
                    <div className="google-button-slot" ref={addGoogleButtonRef} />
                    {!googleReady && <small className="google-note">Google Login wird geladen …</small>}
                    <button className="logout-button" type="button" onClick={logout}>Aktives Konto abmelden</button>
                  </div>
                )}
              </>
            ) : (
              <div className="login-slot">
                <div className="google-button-slot" ref={googleButtonRef} />
                {!GOOGLE_CLIENT_ID && <span className="config-note">Google Login noch nicht konfiguriert</span>}
                {GOOGLE_CLIENT_ID && !googleReady && <span className="config-note">Google Login wird geladen …</span>}
              </div>
            )}
          </div>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <p className="eyebrow">STARTER TEMPLATE</p>
            <h1>Deine neue App startet hier.</h1>
            <p className="hero-text">Eine kleine, installierbare Web-App mit React, TypeScript, PWA-Support, Google Login und automatischem Deployment über GitHub Pages.</p>
            <div className="hero-actions">
              <a className="primary-button" href="#basis">Basis ansehen</a>
              <a className="secondary-button" href="https://github.com/weidmanngabriel/pwa-template" target="_blank" rel="noreferrer">Repository öffnen</a>
            </div>
          </div>

          <aside className="status-card" aria-label="Template Status">
            <p className="card-kicker">SYSTEM STATUS</p>
            <h2>Bereit zum Anpassen</h2>
            <div className="status-row"><span>React + TypeScript</span><strong>bereit</strong></div>
            <div className="status-row"><span>Installierbare PWA</span><strong>bereit</strong></div>
            <div className="status-row"><span>Google Login</span><strong>{GOOGLE_CLIENT_ID ? 'konfiguriert' : 'Setup nötig'}</strong></div>
            <div className="status-row"><span>GitHub Pages</span><strong>vorbereitet</strong></div>
          </aside>
        </section>

        <section className="basis-section" id="basis">
          <div className="section-heading">
            <p className="eyebrow">DIE BASIS</p>
            <h2>Genug Struktur, ohne Produktballast.</h2>
          </div>
          <div className="feature-grid">
            <article className="feature-card">
              <span className="feature-index">01</span>
              <h3>PWA</h3>
              <p>Manifest, Service Worker, App-Icons und Pull-to-Refresh sind vorbereitet.</p>
            </article>
            <article className="feature-card">
              <span className="feature-index">02</span>
              <h3>Google Login</h3>
              <p>Google-Anmeldung mit mehreren lokal gespeicherten Konten und einfachem Wechsel.</p>
            </article>
            <article className="feature-card">
              <span className="feature-index">03</span>
              <h3>Deployment</h3>
              <p>Ein GitHub-Actions-Workflow baut die App und veröffentlicht ausschließlich den Build.</p>
            </article>
          </div>
        </section>

        <section className="account-demo-section">
          <div>
            <p className="eyebrow">LOGIN CHECK</p>
            <h2>{user ? `Google Login funktioniert, ${user.name}.` : 'Google Login direkt testbar.'}</h2>
            <p>{user ? 'Das aktive Konto wird lokal gespeichert. Weitere Google-Konten können oben im Kontomenü ergänzt und gewechselt werden.' : 'Sobald eine Google Client-ID hinterlegt ist, erscheint der Anmeldebutton oben im Header.'}</p>
          </div>
          <div className={`login-state-card${user ? ' is-signed-in' : ''}`}>
            <span className="login-state-dot" />
            <div><strong>{user ? 'Angemeldet' : 'Nicht angemeldet'}</strong><small>{user ? user.email : 'Kein Google-Konto aktiv'}</small></div>
          </div>
        </section>
      </main>

      <footer>
        <span>PWA Template</span>
        <span>React · TypeScript · Vite</span>
      </footer>
    </div>
  )
}

export default App
