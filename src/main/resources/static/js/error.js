/**
 * error.js — Populates error details from URL query parameter safely.
 */
const params = new URLSearchParams(window.location.search);
const msg = params.get('message');
if (msg) {
  const el = document.getElementById('error-message');
  if (el) el.textContent = decodeURIComponent(msg);
}
