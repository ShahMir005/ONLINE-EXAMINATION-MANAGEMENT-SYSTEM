/**
 * nav.js — ExamPro SPA navigation renderer.
 *
 * Renders the navbar links and auth area based on the user's role.
 * Called once after auth resolves in app.js.
 *
 * Role-based nav:
 *   ADMIN   → Dashboard, Exams, Students, Results, Users
 *   TEACHER → Dashboard, Exams, Results
 *   STUDENT → Dashboard, Exams, Results
 */

import { post } from './api.js';

const NAV_BY_ROLE = {
  ADMIN:   ['#/', '#/exams', '#/students', '#/results', '#/admin/users'],
  TEACHER: ['#/', '#/exams', '#/results'],
  STUDENT: ['#/', '#/exams', '#/results'],
};

const LABELS = {
  '#/':            'Dashboard',
  '#/exams':       'Exams',
  '#/students':    'Students',
  '#/results':     'Results',
  '#/admin/users': 'Users',
};

export function renderNav(user) {
  const linksEl = document.getElementById('nav-links');
  const ctaEl   = document.getElementById('nav-cta');
  if (!linksEl || !ctaEl) return;

  const links = NAV_BY_ROLE[user.role] ?? NAV_BY_ROLE.STUDENT;

  linksEl.innerHTML = links.map(href => `
    <li class="nav-item" data-route="${href}">
      <a href="${href}">${LABELS[href] ?? href}</a>
    </li>
  `).join('');

  ctaEl.innerHTML = `
    <div class="nav-user-row">
      <span class="nav-user-name">
        Signed in as <span class="nav-user-email">${escHtml(user.username)}</span>
      </span>
      <button class="btn btn-secondary btn-sm" id="nav-logout-btn" type="button">
        Sign out
      </button>
    </div>
  `;

  document.getElementById('nav-logout-btn')?.addEventListener('click', async () => {
    try { await post('/api/auth/logout'); } catch { /* ignore */ }
    location.href = '/login';
  });

  // Keep active link in sync with current route
  window.addEventListener('hashchange', () => highlightActive());
  highlightActive();
}

function highlightActive() {
  const hash = location.hash || '#/';
  document.querySelectorAll('#nav-links .nav-item').forEach(li => {
    const route = li.dataset.route;
    li.classList.toggle('active', hash === route || (route !== '#/' && hash.startsWith(route)));
  });
}

function escHtml(str) {
  return str.replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
}
