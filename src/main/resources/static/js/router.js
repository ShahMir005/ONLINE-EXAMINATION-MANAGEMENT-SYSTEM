/**
 * @fileoverview ExamPro — Hash-based SPA router with role guards (ES module).
 *
 * Route table (matches DESIGN.md § Routing):
 *
 * | Hash               | View module              | Roles             |
 * |--------------------|--------------------------|-------------------|
 * | `#/`               | `views/home.js`          | ALL               |
 * | `#/exams`          | `views/exams.js`         | ALL               |
 * | `#/exams/new`      | `views/exam-create.js`   | ADMIN, TEACHER    |
 * | `#/exams/:id/take` | `views/exam-take.js`     | STUDENT           |
 * | `#/results`        | `views/results.js`       | ALL               |
 * | `#/students`       | `views/students.js`      | ADMIN             |
 * | `#/admin/users`    | `views/users.js`         | ADMIN             |
 *
 * Access control is **enforced server-side**; the router only hides routes
 * from the UI for non-privileged roles so the experience feels seamless.
 *
 * @module router
 */

import { getUser } from './state.js';

/* ═══════════════════════════════════════════════════════════════════════════
   Route table
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} Route
 * @property {RegExp}          pattern — Regex to match against `location.hash`.
 * @property {function(): Promise<{render: RenderFn}>} view — Dynamic import returning the view module.
 * @property {string[]|null}   roles   — Allowed roles, or `null` for any authenticated user.
 */

/**
 * @typedef {function(HTMLElement, ViewContext): Promise<void>} RenderFn
 * A view module's `render()` export.
 */

/**
 * @typedef {Object} ViewContext
 * @property {{username:string, role:string, studentId?:number}} user — Current user.
 * @property {string[]} params — Captured regex groups from the hash pattern.
 */

/** @type {Route[]} */
const ROUTES = [
  { pattern: /^#\/$|^$|^#$/,                view: () => import('./views/home.js'),        roles: null },
  { pattern: /^#\/dashboard\/admin$/,       view: () => import('./views/dashboard-admin.js'), roles: ['ADMIN'] },
  { pattern: /^#\/dashboard\/teacher$/,     view: () => import('./views/dashboard-teacher.js'), roles: ['TEACHER'] },
  { pattern: /^#\/dashboard\/student$/,     view: () => import('./views/dashboard-student.js'), roles: ['STUDENT'] },
  { pattern: /^#\/exams\/new$/,              view: () => import('./views/exam-create.js'), roles: ['ADMIN', 'TEACHER'] },
  { pattern: /^#\/exams\/(\d+)\/take$/,      view: () => import('./views/exam-take.js'),   roles: ['STUDENT'] },
  { pattern: /^#\/exams$/,                   view: () => import('./views/exams.js'),       roles: null },
  { pattern: /^#\/results$/,                 view: () => import('./views/results.js'),     roles: null },
  { pattern: /^#\/scorecard\/(\d+)$/,         view: () => import('./views/scorecard.js'),   roles: null },
  { pattern: /^#\/results\/(\d+)\/scorecard$/, view: () => import('./views/scorecard.js'),  roles: null },
  { pattern: /^#\/students$/,                view: () => import('./views/students.js'),    roles: ['ADMIN'] },
  { pattern: /^#\/admin\/users$/,            view: () => import('./views/users.js'),       roles: ['ADMIN'] },
];

/* ═══════════════════════════════════════════════════════════════════════════
   DOM helpers — XSS-safe (textContent only, never innerHTML for user data)
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Return the `#view` or `#app-root` element (the SPA mount point).
 * @returns {HTMLElement|null}
 */
const ROOT_EL = () => document.getElementById('view') || document.getElementById('app-root');

/**
 * Build the skeleton loading placeholder using DOM APIs (no innerHTML).
 * @param {HTMLElement} root — Container to fill.
 */
function renderSkeleton(root) {
  root.textContent = '';
  const frag = document.createDocumentFragment();

  const bars = [
    { height: '2.5rem', width: '55%',  mb: '1.5rem' },
    { height: '1rem',   width: '35%',  mb: '3rem'   },
    { height: '10rem',  width: '100%', mb: '1rem'   },
    { height: '10rem',  width: '100%', mb: '0'      },
  ];

  for (const bar of bars) {
    const el = document.createElement('div');
    el.className = 'skeleton';
    el.style.height = bar.height;
    el.style.width = bar.width;
    el.style.marginBottom = bar.mb;
    el.setAttribute('aria-hidden', 'true');
    frag.appendChild(el);
  }

  root.appendChild(frag);
}

/**
 * Build the 404 Not Found page using safe DOM APIs.
 * @param {HTMLElement} root — Container to fill.
 */
function renderNotFound(root) {
  root.textContent = '';

  const banner = document.createElement('div');
  banner.className = 'hero-banner';

  const tag = document.createElement('p');
  tag.className = 'hero-tag';
  tag.textContent = '404';

  const h1 = document.createElement('h1');
  h1.className = 'page-title';
  h1.textContent = 'Page not found.';

  const desc = document.createElement('p');
  desc.className = 'hero-description';
  desc.textContent = "The page you're looking for doesn't exist.";

  const actions = document.createElement('div');
  actions.className = 'hero-actions';
  const link = document.createElement('a');
  link.href = '#/';
  link.className = 'btn btn-primary';
  link.textContent = 'Go to dashboard';
  actions.appendChild(link);

  banner.append(tag, h1, desc, actions);
  root.appendChild(banner);
}

/**
 * Build the 403 Forbidden page using safe DOM APIs.
 * @param {HTMLElement} root — Container to fill.
 */
function renderForbidden(root) {
  root.textContent = '';

  const banner = document.createElement('div');
  banner.className = 'hero-banner';

  const tag = document.createElement('p');
  tag.className = 'hero-tag';
  tag.textContent = 'Access denied';

  const h1 = document.createElement('h1');
  h1.className = 'page-title';
  h1.textContent = "You don't have permission to view this page.";

  const actions = document.createElement('div');
  actions.className = 'hero-actions';
  const link = document.createElement('a');
  link.href = '#/';
  link.className = 'btn btn-primary';
  link.textContent = 'Go to dashboard';
  actions.appendChild(link);

  banner.append(tag, h1, actions);
  root.appendChild(banner);
}

/**
 * Build the error page using safe DOM APIs.
 * @param {HTMLElement} root — Container to fill.
 * @param {Error}       err  — The error that caused the view to fail.
 */
function renderError(root, err) {
  root.textContent = '';

  const alert = document.createElement('div');
  alert.className = 'alert alert-danger';
  alert.setAttribute('role', 'alert');

  const label = document.createElement('span');
  label.className = 'alert-label';
  label.textContent = 'Failed to load view.';

  const msg = document.createElement('span');
  msg.textContent = err?.message ?? 'Unknown error';

  alert.append(label, msg);
  root.appendChild(alert);
}

/* ═══════════════════════════════════════════════════════════════════════════
   Router lifecycle
   ═══════════════════════════════════════════════════════════════════════════ */

/** @type {AbortController|null} Cancels the previous view's in-flight work. */
let _activeController = null;

/**
 * Navigate to the current `location.hash`, matching it against the route
 * table and lazy-loading the corresponding view module.
 *
 * @returns {Promise<void>}
 */
async function navigate() {
  const hash = location.hash || '#/';
  const root = ROOT_EL();
  if (!root) return;

  const user = getUser();
  if (!user) {
    window.location.href = '/login';
    return;
  }

  // ── Match route ───────────────────────────────────────────────────
  /** @type {Route|null} */
  let matched = null;
  /** @type {string[]} */
  let params = [];

  for (const route of ROUTES) {
    const m = hash.match(route.pattern);
    if (m) {
      matched = route;
      params = m.slice(1);
      break;
    }
  }

  if (!matched) {
    renderNotFound(root);
    return;
  }

  // ── Role guard ────────────────────────────────────────────────────
  if (matched.roles && !matched.roles.includes(user.role)) {
    renderForbidden(root);
    return;
  }

  // ── Cancel previous view's work ───────────────────────────────────
  if (_activeController) {
    _activeController.abort();
  }
  _activeController = new AbortController();

  // ── Show skeleton while the view chunk loads ──────────────────────
  renderSkeleton(root);

  try {
    const mod = await matched.view();
    // If a new navigation happened while we were loading, bail out
    if (_activeController.signal.aborted) return;
    await mod.render(root, { user, params, signal: _activeController.signal });
  } catch (err) {
    if (err.name === 'AbortError') return;
    console.error('[router] view failed', err);
    renderError(root, err);
  }
}

/**
 * Initialise the hash-based router.
 *
 * Listens for `hashchange` events and performs the initial navigation.
 * Must be called once after the user object is loaded (from `main.js`).
 */
export function startRouter() {
  window.addEventListener('hashchange', navigate);
  navigate();
}

/**
 * Programmatically navigate to a hash route.
 *
 * @param {string} hash — Target hash (e.g. `'#/exams'`).
 */
export function navigateTo(hash) {
  location.hash = hash;
}
