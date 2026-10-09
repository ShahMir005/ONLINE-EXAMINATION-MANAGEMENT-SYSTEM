/**
 * @fileoverview ExamPro — Countdown timer synced to server time (ES module).
 *
 * The timer accounts for clock drift between client and server by accepting
 * a server-provided deadline timestamp. It updates a DOM element every second
 * and fires callbacks at configurable warning thresholds.
 *
 * @module timer
 *
 * @example
 *   import { createTimer } from './timer.js';
 *
 *   const timer = createTimer({
 *     element: document.getElementById('live-exam-timer'),
 *     deadlineMs: serverDeadlineEpochMs,     // from /api/exams/:id/start
 *     onTick:    (secsLeft) => updateProgress(secsLeft),
 *     onExpired: ()         => autoSubmit(),
 *     warningThresholdSecs: 300,              // 5 minutes
 *     dangerThresholdSecs:  60,               // 1 minute
 *   });
 *
 *   // Later, to stop early:
 *   timer.stop();
 */

/* ═══════════════════════════════════════════════════════════════════════════
   Formatting helper
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Format seconds into `MM:SS` or `HH:MM:SS` (when ≥ 1 hour).
 *
 * @param {number} totalSecs — Non-negative seconds remaining.
 * @returns {string} Formatted time string.
 */
export function formatTime(totalSecs) {
  const secs = Math.max(0, Math.floor(totalSecs));
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = secs % 60;

  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');

  return h > 0
    ? `${String(h).padStart(2, '0')}:${mm}:${ss}`
    : `${mm}:${ss}`;
}

/* ═══════════════════════════════════════════════════════════════════════════
   Server-time drift calculation
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Compute the offset between local clock and server clock.
 *
 * Call this once at boot (or when starting an exam) with the server's
 * current epoch-ms value (e.g. returned by `/api/exams/:id/start`).
 * All subsequent `secondsRemaining()` calls use this offset so the
 * countdown stays accurate even if the client's clock is wrong.
 *
 * @param {number} serverNowMs — Server's `System.currentTimeMillis()`.
 * @returns {number} Offset in ms (`serverNow - localNow`).
 *   Add this to `Date.now()` to approximate the server's current time.
 */
export function computeDrift(serverNowMs) {
  return serverNowMs - Date.now();
}

/**
 * Calculate seconds remaining until a deadline, corrected for drift.
 *
 * @param {number} deadlineMs — Server-side deadline epoch-ms.
 * @param {number} driftMs    — Offset from {@link computeDrift}.
 * @returns {number} Seconds remaining (floored, min 0).
 */
export function secondsRemaining(deadlineMs, driftMs) {
  const serverNow = Date.now() + driftMs;
  return Math.max(0, Math.floor((deadlineMs - serverNow) / 1000));
}

/* ═══════════════════════════════════════════════════════════════════════════
   Timer factory
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} TimerOptions
 * @property {HTMLElement}      element    — The DOM element whose `textContent`
 *   is updated with the formatted countdown.
 * @property {number}           deadlineMs — Server-side deadline as epoch-ms.
 * @property {number}           [serverNowMs=Date.now()] — Server's current
 *   time at the moment the deadline was received; used to compute drift.
 * @property {function(number): void} [onTick]    — Called every second with
 *   the number of seconds remaining.
 * @property {function(): void} [onExpired] — Called once when time reaches 0.
 * @property {number}           [warningThresholdSecs=300] — Seconds at which
 *   the `.warning` class is added to the element.
 * @property {number}           [dangerThresholdSecs=60]  — Seconds at which
 *   the `.danger` class is added to the element.
 */

/**
 * @typedef {Object} TimerHandle
 * @property {function(): void}   stop            — Cancel the timer.
 * @property {function(): number} getSecondsLeft  — Current seconds remaining.
 * @property {function(): boolean} isRunning      — Whether the timer is active.
 */

/**
 * Create and start a countdown timer.
 *
 * The timer ticks every second, updates the given DOM element's text content,
 * applies CSS classes at configurable thresholds, and fires callbacks.
 *
 * @param {TimerOptions} opts — Timer configuration.
 * @returns {TimerHandle} Handle to query or stop the timer.
 */
export function createTimer({
  element,
  deadlineMs,
  serverNowMs = Date.now(),
  onTick,
  onExpired,
  warningThresholdSecs = 300,
  dangerThresholdSecs = 60,
}) {
  const driftMs = computeDrift(serverNowMs);

  /** @type {number|null} */
  let intervalId = null;
  let running = true;
  let expired = false;

  /**
   * Perform one tick: update text, apply threshold classes, fire callbacks.
   */
  function tick() {
    const secs = secondsRemaining(deadlineMs, driftMs);

    // Update the element's text (safe — textContent only)
    if (element) {
      element.textContent = secs > 0 ? formatTime(secs) : "00:00";

      // Threshold classes (timer-pill styles in pages.css)
      element.classList.toggle('warning', secs > dangerThresholdSecs && secs <= warningThresholdSecs);
      element.classList.toggle('danger', secs <= dangerThresholdSecs && secs > 0);
    }

    if (onTick) {
      onTick(secs);
    }

    if (secs <= 0 && !expired) {
      expired = true;
      stop();
      if (onExpired) {
        onExpired();
      }
    }
  }

  /**
   * Stop the timer and clean up the interval.
   */
  function stop() {
    if (intervalId != null) {
      clearInterval(intervalId);
      intervalId = null;
    }
    running = false;
  }

  // ── Initial tick + start interval ───────────────────────────────────
  tick();
  if (!expired) {
    intervalId = setInterval(tick, 1000);
  }

  return Object.freeze({
    stop,
    /** @returns {number} */
    getSecondsLeft: () => secondsRemaining(deadlineMs, driftMs),
    /** @returns {boolean} */
    isRunning: () => running,
  });
}

/* ═══════════════════════════════════════════════════════════════════════════
   Simple duration-based timer (fallback when no server deadline)
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Create a timer from a duration in minutes (no server-sync).
 *
 * Convenience wrapper around {@link createTimer} for cases where the server
 * returns a duration rather than an absolute deadline (e.g. Thymeleaf pages).
 *
 * @param {Object} opts
 * @param {HTMLElement} opts.element — DOM element to update.
 * @param {number}      opts.durationMinutes — Exam length in minutes.
 * @param {function(number): void} [opts.onTick]    — Per-second callback.
 * @param {function(): void}       [opts.onExpired] — Expiry callback.
 * @returns {TimerHandle}
 */
export function createDurationTimer({ element, durationMinutes, onTick, onExpired }) {
  const now = Date.now();
  return createTimer({
    element,
    deadlineMs: now + durationMinutes * 60 * 1000,
    serverNowMs: now,
    onTick,
    onExpired,
  });
}
