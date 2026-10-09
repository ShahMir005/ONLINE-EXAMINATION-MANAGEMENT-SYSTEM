/**
 * @fileoverview ExamPro — HTTP API client (ES module).
 *
 * Every fetch() in the SPA goes through this module so that:
 *  - The XSRF-TOKEN cookie is read and attached as X-XSRF-TOKEN header.
 *  - 401 responses redirect to /login (session expired).
 *  - 403 responses throw an ApiError with status 403 (role/ownership denial).
 *  - Network and server errors surface as thrown {@link ApiError} objects.
 *
 * @module api
 *
 * @example
 *   import { get, post, del } from './api.js';
 *   const exams = await get('/api/exams');
 *   await post('/api/exams', { title: 'Midterm', durationMinutes: 60 });
 *   await del('/api/exams/42');
 */

/* ═══════════════════════════════════════════════════════════════════════════
   ApiError
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Structured error thrown by the fetch wrapper.
 * Carries the HTTP status and, when available, the JSON error body from the
 * server (typically `{ message, field? }`).
 */
export class ApiError extends Error {
  /**
   * @param {number} status  — HTTP status code (e.g. 403, 500).
   * @param {string} message — Human-readable error summary.
   * @param {Object|null} [data=null] — Parsed JSON body from the server, if any.
   */
  constructor(status, message, data = null) {
    super(message);
    /** @type {number} HTTP status code. */
    this.status = status;
    /** @type {Object|null} Parsed JSON error body, if any. */
    this.data = data;
    this.name = 'ApiError';
  }
}

/* ═══════════════════════════════════════════════════════════════════════════
   Cookie helper
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Read a cookie value by name from `document.cookie`.
 *
 * @param {string} name — Cookie name (e.g. `'XSRF-TOKEN'`).
 * @returns {string|null} Decoded cookie value, or `null` if not found.
 */
function getCookie(name) {
  const match = document.cookie.match(
    new RegExp('(?:^|; )' + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '=([^;]*)')
  );
  return match ? decodeURIComponent(match[1]) : null;
}

/* ═══════════════════════════════════════════════════════════════════════════
   Core fetch wrapper
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Internal fetch wrapper that attaches CSRF and handles HTTP errors.
 *
 * @param {string} method — HTTP method (`GET`, `POST`, `PUT`, `DELETE`).
 * @param {string} url    — Request URL (e.g. `/api/exams`).
 * @param {Object} [body] — JSON-serialisable request body (omitted for GET/DELETE).
 * @returns {Promise<Object|null>} Parsed JSON response, or `null` for 204 No Content.
 * @throws {ApiError} On any non-2xx response (except 401 which redirects).
 */
async function request(method, url, body) {
  /** @type {Record<string, string>} */
  const headers = { 'Accept': 'application/json' };

  // Attach CSRF token from the cookie written by CookieCsrfTokenRepository
  const csrf = getCookie('XSRF-TOKEN');
  if (csrf) {
    headers['X-XSRF-TOKEN'] = csrf;
  }

  /** @type {RequestInit} */
  const opts = { method, headers, credentials: 'same-origin' };

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }

  /** @type {Response} */
  let res;
  try {
    res = await fetch(url, opts);
  } catch (networkErr) {
    throw new ApiError(0, 'Network error — please check your connection.');
  }

  // ── 401 Unauthorized → session expired, redirect to login ───────────
  if (res.status === 401) {
    window.location.href = '/login';
    // Return a never-resolving promise so callers don't execute further
    return new Promise(() => {});
  }

  // ── 403 Forbidden → role or ownership denial ────────────────────────
  if (res.status === 403) {
    let data = null;
    try { data = await res.json(); } catch { /* empty body is fine */ }
    throw new ApiError(403, data?.message ?? 'You do not have permission for this action.', data);
  }

  // ── Other non-2xx errors ────────────────────────────────────────────
  if (!res.ok) {
    let data = null;
    try { data = await res.json(); } catch { /* ignore */ }
    throw new ApiError(res.status, data?.message ?? `Request failed (${res.status})`, data);
  }

  // ── 204 No Content (e.g. DELETE) ────────────────────────────────────
  if (res.status === 204) return null;

  return res.json();
}

/* ═══════════════════════════════════════════════════════════════════════════
   Public convenience methods
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Send a GET request.
 * @param {string} url — API endpoint.
 * @returns {Promise<Object|null>}
 */
export const get = (url) => request('GET', url);

/**
 * Send a POST request with a JSON body.
 * @param {string} url  — API endpoint.
 * @param {Object} [body] — Request payload.
 * @returns {Promise<Object|null>}
 */
export const post = (url, body) => request('POST', url, body);

/**
 * Send a PUT request with a JSON body.
 * @param {string} url  — API endpoint.
 * @param {Object} [body] — Request payload.
 * @returns {Promise<Object|null>}
 */
export const put = (url, body) => request('PUT', url, body);

/**
 * Send a DELETE request.
 * @param {string} url — API endpoint.
 * @returns {Promise<Object|null>}
 */
export const del = (url) => request('DELETE', url);

/* ═══════════════════════════════════════════════════════════════════════════
   Auth helpers
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Fetch the currently authenticated user.
 *
 * @returns {Promise<{username: string, role: string, studentId?: number}|null>}
 *   The user object, or `null` if the session has expired / is unauthenticated.
 */
export async function getMe() {
  try {
    return await get('/api/me');
  } catch (e) {
    if (e.status === 401) return null;
    throw e;
  }
}

/**
 * Log out the current user (POST /api/auth/logout) and redirect to /login.
 * @returns {Promise<void>}
 */
export async function logout() {
  try {
    await post('/api/auth/logout');
  } catch {
    /* best-effort — redirect regardless */
  }
  window.location.href = '/login';
}
