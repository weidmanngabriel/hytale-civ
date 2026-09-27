export type GoogleUser = {
  sub: string
  name: string
  email: string
  picture?: string
  exp?: number
}

const ACCOUNTS_STORAGE_KEY = 'pwa-template-google-accounts'
const ACTIVE_ACCOUNT_STORAGE_KEY = 'pwa-template-google-active-account'

type StoredGoogleAccount = {
  user: GoogleUser
}

function decodeCredential(credential: string): GoogleUser | null {
  try {
    const payload = credential.split('.')[1]
    if (!payload) return null

    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const decoded = decodeURIComponent(
      atob(padded)
        .split('')
        .map((character) => `%${character.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join(''),
    )
    const user = JSON.parse(decoded) as GoogleUser
    if (!user.sub || !user.email || !user.name) return null
    if (user.exp && user.exp * 1000 <= Date.now()) return null
    return user
  } catch {
    return null
  }
}

function isStoredGoogleUser(value: unknown): value is GoogleUser {
  if (!value || typeof value !== 'object') return false
  const user = value as Partial<GoogleUser>
  return Boolean(user.sub && user.name && user.email)
}

function readEntries(): StoredGoogleAccount[] {
  try {
    const raw = localStorage.getItem(ACCOUNTS_STORAGE_KEY)
    if (!raw) return []
    const value = JSON.parse(raw) as StoredGoogleAccount[]
    if (!Array.isArray(value)) return []
    return value.filter((entry) => isStoredGoogleUser(entry?.user))
  } catch {
    return []
  }
}

function writeEntries(entries: StoredGoogleAccount[]) {
  if (entries.length) localStorage.setItem(ACCOUNTS_STORAGE_KEY, JSON.stringify(entries))
  else localStorage.removeItem(ACCOUNTS_STORAGE_KEY)
}

export function readStoredGoogleAccounts(): GoogleUser[] {
  const users = readEntries().map((entry) => entry.user)
  const activeEmail = localStorage.getItem(ACTIVE_ACCOUNT_STORAGE_KEY)?.toLowerCase()

  if (!activeEmail || !users.some((user) => user.email.toLowerCase() === activeEmail)) {
    if (users[0]) localStorage.setItem(ACTIVE_ACCOUNT_STORAGE_KEY, users[0].email.toLowerCase())
    else localStorage.removeItem(ACTIVE_ACCOUNT_STORAGE_KEY)
  }

  return users
}

export function readActiveGoogleAccount(accounts = readStoredGoogleAccounts()) {
  const activeEmail = localStorage.getItem(ACTIVE_ACCOUNT_STORAGE_KEY)?.toLowerCase()
  return accounts.find((account) => account.email.toLowerCase() === activeEmail) ?? accounts[0] ?? null
}

export function storeGoogleAccount(credential: string) {
  const user = decodeCredential(credential)
  if (!user) return null

  const email = user.email.toLowerCase()
  const nextEntries = readEntries().filter((entry) => entry.user.email.toLowerCase() !== email)
  nextEntries.push({ user })
  writeEntries(nextEntries)
  localStorage.setItem(ACTIVE_ACCOUNT_STORAGE_KEY, email)
  return user
}

export function setActiveGoogleAccount(email: string) {
  localStorage.setItem(ACTIVE_ACCOUNT_STORAGE_KEY, email.toLowerCase())
}

export function removeGoogleAccount(email: string) {
  const target = email.toLowerCase()
  const entries = readEntries().filter((entry) => entry.user.email.toLowerCase() !== target)
  writeEntries(entries)

  const accounts = entries.map((entry) => entry.user)
  const active = accounts[0] ?? null
  if (active) localStorage.setItem(ACTIVE_ACCOUNT_STORAGE_KEY, active.email.toLowerCase())
  else localStorage.removeItem(ACTIVE_ACCOUNT_STORAGE_KEY)

  return { accounts, active }
}
