/**
 * login.js — ExamPro login page logic.
 *
 * Fixes / features:
 *  1. CSRF token injected from XSRF-TOKEN cookie.
 *  2. Role segmented control — Student selected by default,
 *     keyboard-accessible, updates email placeholder and the hidden
 *     role field sent with the form.
 *  3. Sign-in error banner shown ONLY when ?error is in the URL;
 *     hidden again as soon as the user edits any field.
 *  4. Field validation errors shown only after the user has
 *     touched (blurred) a field OR after the first submit attempt.
 *  5. Password show/hide toggle.
 *  6. Local demo-account buttons that fill the form with sample accounts.
 *
 * No external dependencies. Pure ES module.
 */

/* ── Helpers ──────────────────────────────────────────────────────────────── */
function getCookie(name) {
  const match = document.cookie.match(
    new RegExp('(?:^|; )' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '=([^;]*)')
  );
  return match ? decodeURIComponent(match[1]) : null;
}

function $(id) { return document.getElementById(id); }

/* ── CSRF ─────────────────────────────────────────────────────────────────── */
const csrfField = $('csrf-field');
if (csrfField) {
  const token = getCookie('XSRF-TOKEN');
  if (token) csrfField.value = token;
}

/* ── Error banner — show only on ?error, hide on edit ────────────────────── */
const params   = new URLSearchParams(location.search);
const errorBanner = $('login-error');

if (params.has('error') && errorBanner) {
  errorBanner.hidden = false;
}

/* ── Role segmented control ───────────────────────────────────────────────── */
const ROLE_PLACEHOLDERS = {
  student: 'student@institution.edu',
  teacher: 'teacher@institution.edu',
  admin:   'administrator@institution.edu',
};

// Initialise from URL param; default to student
const urlRole = (params.get('role') || 'student').toLowerCase();
let currentRole = Object.keys(ROLE_PLACEHOLDERS).includes(urlRole) ? urlRole : 'student';

const roleButtons   = document.querySelectorAll('.role-seg-btn');
const roleField     = $('role-field');
const usernameInput = $('username');

function selectRole(role) {
  currentRole = role;

  roleButtons.forEach(btn => {
    const isActive = btn.dataset.role === role;
    btn.setAttribute('aria-pressed', String(isActive));
  });

  if (roleField)     roleField.value = role;
  if (usernameInput) usernameInput.placeholder = ROLE_PLACEHOLDERS[role] ?? 'you@institution.edu';
}

// Apply initial selection
selectRole(currentRole);

// Click handler
roleButtons.forEach(btn => {
  btn.addEventListener('click', () => selectRole(btn.dataset.role));
});

// Keyboard: left/right arrows navigate between segments
const roleGroup = $('role-group');
if (roleGroup) {
  roleGroup.addEventListener('keydown', e => {
    const btns = Array.from(roleButtons);
    const idx  = btns.indexOf(document.activeElement);
    if (idx === -1) return;

    if (e.key === 'ArrowRight') {
      e.preventDefault();
      const next = btns[(idx + 1) % btns.length];
      next.focus();
      selectRole(next.dataset.role);
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      const prev = btns[(idx - 1 + btns.length) % btns.length];
      prev.focus();
      selectRole(prev.dataset.role);
    }
  });
}

/* ── Password show / hide toggle ─────────────────────────────────────────── */
const passwordInput  = $('password');
const passwordToggle = $('password-toggle');

if (passwordInput && passwordToggle) {
  const eyeShow = passwordToggle.querySelector('.eye-show');
  const eyeHide = passwordToggle.querySelector('.eye-hide');

  passwordToggle.addEventListener('click', () => {
    const isHidden = passwordInput.type === 'password';
    passwordInput.type = isHidden ? 'text' : 'password';

    // Swap icons
    if (eyeShow) eyeShow.hidden = isHidden;
    if (eyeHide) eyeHide.hidden = !isHidden;

    passwordToggle.setAttribute('aria-label', isHidden ? 'Hide password' : 'Show password');

    // Return focus to input so the user can keep typing
    passwordInput.focus();
  });
}

/* ── Validation (deferred: only after touch or submit attempt) ────────────── */
const form     = $('login-form');
const emailEl  = $('username');
const passEl   = $('password');
const emailErr = $('username-error');
const passErr  = $('password-error');

// Track which fields have been visited (blurred at least once)
const touched = { email: false, pass: false };

function validateEmail() {
  const bad = !emailEl.value.trim() || !emailEl.validity.valid;
  emailEl.classList.toggle('is-error', bad);
  if (emailErr) emailErr.hidden = !bad;
  return !bad;
}

function validatePass() {
  const bad = !passEl.value;
  passEl.classList.toggle('is-error', bad);
  if (passErr) passErr.hidden = !bad;
  return !bad;
}

// Only validate on blur once the field has been touched
emailEl?.addEventListener('blur', () => {
  touched.email = true;
  validateEmail();
});

passEl?.addEventListener('blur', () => {
  touched.pass = true;
  validatePass();
});

// Clear error state while typing; also hide the sign-in failed banner
emailEl?.addEventListener('input', () => {
  if (touched.email) validateEmail();
  if (errorBanner) errorBanner.hidden = true;
});

passEl?.addEventListener('input', () => {
  if (touched.pass) validatePass();
  if (errorBanner) errorBanner.hidden = true;
});

// Full validation on submit (marks all fields as touched)
form?.addEventListener('submit', e => {
  touched.email = true;
  touched.pass  = true;

  const emailOk = validateEmail();
  const passOk  = validatePass();

  if (!emailOk || !passOk) {
    e.preventDefault();
    if (!emailOk) emailEl.focus();
    else           passEl.focus();
  }
});

/* ── Forgot-password hint ─────────────────────────────────────────────────── */
const forgotBtn  = $('forgot-password-btn');
const infoBanner = $('login-info');
const infoMsg    = $('login-info-msg');

forgotBtn?.addEventListener('click', () => {
  if (infoBanner && infoMsg) {
    infoMsg.textContent = 'Contact your administrator to reset your password, '
      + 'or run: mvn exec:java -Dexec.args="--reset-password <email> <newPassword>"';
    infoBanner.hidden = false;
  }
});

/* ── Local demo-account buttons ───────────────────────────────────────────── */
const DEV_CREDS = {
  admin:   { email: 'admin@exampro.local',   password: 'Admin@123' },
  teacher: { email: 'teacher@exampro.local', password: 'Teacher@123' },
  student: { email: 'student@exampro.local', password: 'Student@123' },
};

document.querySelectorAll('[data-demo-role]').forEach(btn => {
  btn.addEventListener('click', () => {
    const role = btn.dataset.demoRole;
    const credentials = DEV_CREDS[role];
    if (!credentials) return;
    selectRole(role);
    if (emailEl) emailEl.value = credentials.email;
    if (passEl)  passEl.value  = credentials.password;

    // Clear stale validation errors, including errors left after browser autofill.
    touched.email = true;
    touched.pass = true;
    validateEmail();
    validatePass();
    if (errorBanner) errorBanner.hidden = true;
    $('login-submit')?.focus();
  });
});
