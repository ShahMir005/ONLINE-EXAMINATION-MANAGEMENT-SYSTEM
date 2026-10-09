/**
 * @fileoverview ExamPro — SPA boot module (ES module).
 *
 * This is the **only** `<script type="module">` tag in `app.html`.
 * It bootstraps the entire single-page application:
 *
 *   1. Sets the footer year.
 *   2. Fetches `/api/me` to identify the authenticated user.
 *   3. Redirects to `/login` if the session has expired.
 *   4. Stores the user in the shared {@link module:state} store.
 *   5. Renders the navigation (links + auth area).
 *   6. Starts the hash-based router.
 *
 * All view modules are loaded lazily by the router on first navigation.
 *
 * @module main
 */

import { getMe, logout }            from './api.js';
import { setUser, getUser }          from './state.js';
import { startRouter }               from './router.js';
import { toast, setText, el, badge } from './ui.js';

/* ═══════════════════════════════════════════════════════════════════════════
   Navigation config (role → visible links)
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} NavEntry
 * @property {string} hash  — Hash route (e.g. `'#/exams'`).
 * @property {string} label — Display label.
 */

/** @type {Record<string, NavEntry[]>} */
const NAV_BY_ROLE = {
  ADMIN:   [
    { hash: '#/',            label: 'Dashboard' },
    { hash: '#/exams',       label: 'Exams' },
    { hash: '#/students',    label: 'Students' },
    { hash: '#/results',     label: 'Results' },
    { hash: '#/admin/users', label: 'Users' },
  ],
  TEACHER: [
    { hash: '#/',      label: 'Dashboard' },
    { hash: '#/exams', label: 'Exams' },
    { hash: '#/results', label: 'Results' },
  ],
  STUDENT: [
    { hash: '#/',      label: 'Dashboard' },
    { hash: '#/exams', label: 'Exams' },
    { hash: '#/results', label: 'Results' },
  ],
};

/* ═══════════════════════════════════════════════════════════════════════════
   Navigation renderer (DOM-only, no innerHTML with user data)
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Render the navbar links and auth area based on the user's role.
 *
 * @param {{username: string, role: string}} user — Authenticated user.
 */
function renderNav(user) {
  const linksEl = document.getElementById('nav-links');
  const ctaEl   = document.getElementById('nav-cta');
  if (!linksEl || !ctaEl) return;

  // ── Build nav links via DOM API (XSS-safe) ──────────────────────────
  const entries = NAV_BY_ROLE[user.role] ?? NAV_BY_ROLE.STUDENT;
  linksEl.textContent = '';

  for (const entry of entries) {
    const li = document.createElement('li');
    li.className = 'nav-item';
    li.dataset.route = entry.hash;

    const a = document.createElement('a');
    a.href = entry.hash;
    a.textContent = entry.label;

    li.appendChild(a);
    linksEl.appendChild(li);
  }

  // ── Build auth area (user name + sign out) ──────────────────────────
  ctaEl.textContent = '';

  const row = document.createElement('div');
  row.className = 'nav-user-row';

  const nameSpan = document.createElement('span');
  nameSpan.className = 'nav-user-name';
  nameSpan.textContent = 'Signed in as ';

  const emailSpan = document.createElement('span');
  emailSpan.className = 'nav-user-email';
  emailSpan.textContent = user.username;
  nameSpan.appendChild(emailSpan);

  const logoutBtn = document.createElement('button');
  logoutBtn.type = 'button';
  logoutBtn.className = 'btn btn-secondary btn-sm';
  logoutBtn.id = 'nav-logout-btn';
  logoutBtn.textContent = 'Sign out';
  logoutBtn.addEventListener('click', () => logout());

  row.append(nameSpan, logoutBtn);
  ctaEl.appendChild(row);

  // ── Active link highlight ───────────────────────────────────────────
  window.addEventListener('hashchange', highlightActive);
  highlightActive();
}

/**
 * Toggle the `.active` class on nav items to match the current hash.
 */
function highlightActive() {
  const hash = location.hash || '#/';
  document.querySelectorAll('#nav-links .nav-item').forEach(li => {
    const route = /** @type {string} */ (li.dataset.route);
    const isActive = (hash === route) || (route !== '#/' && hash.startsWith(route));
    li.classList.toggle('active', isActive);
  });
}

/* ═══════════════════════════════════════════════════════════════════════════
   Boot sequence
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Application entry point. Called immediately when the module loads.
 * @returns {Promise<void>}
 */
async function boot() {
  // ── Footer year ─────────────────────────────────────────────────────
  setText('footer-year-span', String(new Date().getFullYear()));

  // ── Fetch current user ──────────────────────────────────────────────
  /** @type {import('./state.js').AppUser|null} */
  let user;
  try {
    user = await getMe();
  } catch {
    window.location.href = '/login';
    return;
  }

  if (!user) {
    window.location.href = '/login';
    return;
  }

  // ── Store user in shared state ──────────────────────────────────────
  setUser(user);

  // ── Render navigation ───────────────────────────────────────────────
  renderNav(user);

  // ── Start the hash router ───────────────────────────────────────────
  startRouter();
}

boot();
