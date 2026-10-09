/**
 * @fileoverview ExamPro — SPA entry point re-export.
 *
 * This file exists for backward compatibility: `app.html` may reference
 * either `app.js` or `main.js` as the `<script type="module" src="...">`.
 * Both resolve to the same boot sequence defined in {@link module:main}.
 *
 * @module app
 * @see module:main
 */

// Re-export the boot side-effect from main.js.
// Importing main.js executes boot() automatically.
import './main.js';
