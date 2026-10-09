/**
 * @fileoverview ExamPro — Client-side state store (ES module).
 *
 * Holds the authenticated user object and a simple key→value cache for API
 * responses. No reactivity framework — view modules read state directly and
 * the router re-renders on navigation.
 *
 * @module state
 *
 * @example
 *   import { setUser, getUser, cache } from './state.js';
 *
 *   // At boot (main.js)
 *   setUser(await getMe());
 *
 *   // In a view module
 *   const user = getUser();
 *   const exams = cache.get('exams') ?? await fetchAndCache();
 */

/* ═══════════════════════════════════════════════════════════════════════════
   Current user
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} AppUser
 * @property {string}  username  — Login email / identifier.
 * @property {string}  role      — One of `'ADMIN'`, `'TEACHER'`, `'STUDENT'`.
 * @property {number}  [studentId] — Present only when role is `STUDENT`.
 */

/** @type {AppUser|null} */
let _user = null;

/**
 * Store the authenticated user after `/api/me` resolves.
 * Called once at boot from `main.js`.
 *
 * @param {AppUser} user — The user object from `/api/me`.
 */
export function setUser(user) {
  _user = Object.freeze({ ...user });
}

/**
 * Get the current authenticated user.
 *
 * @returns {AppUser|null} The user object, or `null` if not yet loaded.
 */
export function getUser() {
  return _user;
}

/**
 * Check whether the current user has one of the given roles.
 *
 * @param {...string} roles — Allowed roles (e.g. `'ADMIN'`, `'TEACHER'`).
 * @returns {boolean} `true` if the user's role is in the list.
 *
 * @example
 *   if (hasRole('ADMIN', 'TEACHER')) { showCreateButton(); }
 */
export function hasRole(...roles) {
  return _user != null && roles.includes(_user.role);
}

/**
 * Clear the stored user (e.g. on logout).
 */
export function clearUser() {
  _user = null;
}

/* ═══════════════════════════════════════════════════════════════════════════
   Simple key→value API response cache
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} CacheEntry
 * @property {*}      value     — Cached value.
 * @property {number} timestamp — `Date.now()` when the entry was set.
 */

/** @type {Map<string, CacheEntry>} */
const _store = new Map();

/** Default time-to-live in milliseconds (5 minutes). */
const DEFAULT_TTL_MS = 5 * 60 * 1000;

/**
 * Namespaced cache API for storing API responses in memory.
 *
 * Entries expire after a configurable TTL and can be invalidated manually
 * by key or by prefix (e.g. `cache.invalidate('exams')` clears all
 * keys that start with `'exams'`).
 */
export const cache = Object.freeze({
  /**
   * Retrieve a cached value if it exists and has not expired.
   *
   * @param {string} key    — Cache key (e.g. `'exams'`, `'exam:42'`).
   * @param {number} [ttl=DEFAULT_TTL_MS] — Max age in ms; defaults to 5 min.
   * @returns {*|undefined} The cached value, or `undefined` if missing/expired.
   */
  get(key, ttl = DEFAULT_TTL_MS) {
    const entry = _store.get(key);
    if (!entry) return undefined;
    if (Date.now() - entry.timestamp > ttl) {
      _store.delete(key);
      return undefined;
    }
    return entry.value;
  },

  /**
   * Store a value in the cache.
   *
   * @param {string} key   — Cache key.
   * @param {*}      value — Value to cache (should be JSON-safe).
   */
  set(key, value) {
    _store.set(key, { value, timestamp: Date.now() });
  },

  /**
   * Remove all entries whose key starts with the given prefix.
   * If no prefix is given, the entire cache is cleared.
   *
   * @param {string} [prefix] — Key prefix to match (e.g. `'exam'` matches
   *   `'exams'`, `'exam:42'`, etc.). Omit to clear everything.
   */
  invalidate(prefix) {
    if (prefix === undefined) {
      _store.clear();
      return;
    }
    for (const key of _store.keys()) {
      if (key.startsWith(prefix)) {
        _store.delete(key);
      }
    }
  },

  /**
   * Check whether a non-expired entry exists for the given key.
   *
   * @param {string} key — Cache key.
   * @param {number} [ttl=DEFAULT_TTL_MS] — Max age in ms.
   * @returns {boolean}
   */
  has(key, ttl = DEFAULT_TTL_MS) {
    return this.get(key, ttl) !== undefined;
  },
});
