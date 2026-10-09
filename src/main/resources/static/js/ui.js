/**
 * @fileoverview ExamPro — UI helpers (ES module).
 *
 * Provides pure-DOM utilities shared across all view modules:
 *
 *  - **Toast notifications** — non-blocking status messages.
 *  - **Confirm modal**       — Promise-based destructive-action guard.
 *  - **Table builder**       — builds `<table>` from column definitions + row data.
 *  - **Safe text rendering** — `textContent`-only helpers that prevent XSS.
 *
 * Every function creates DOM nodes programmatically — no `innerHTML` with
 * user-supplied data is ever used.
 *
 * @module ui
 *
 * @example
 *   import { toast, confirm, buildTable, setText } from './ui.js';
 *
 *   toast.success('Exam created successfully.');
 *   const ok = await confirm({ title: 'Delete exam?', body: 'This cannot be undone.' });
 *   const table = buildTable({ columns, rows, emptyText: 'No exams found.' });
 *   root.appendChild(table);
 */

/* ═══════════════════════════════════════════════════════════════════════════
   Toast notifications
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Ensure the fixed toast container exists in the DOM.
 * @returns {HTMLElement} The `.toast-container` element.
 */
function getToastContainer() {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);
  }
  return container;
}

/**
 * Show a toast notification.
 *
 * @param {string} message    — Text to display (set via `textContent`, never HTML).
 * @param {'success'|'error'|'info'} [type='info'] — Visual style variant.
 * @param {number} [durationMs=4500] — Auto-dismiss time in ms; `0` = manual close only.
 */
function showToast(message, type = 'info', durationMs = 4500) {
  const container = getToastContainer();

  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.setAttribute('role', 'status');

  const span = document.createElement('span');
  span.className = 'toast-message';
  span.textContent = message;

  const closeBtn = document.createElement('button');
  closeBtn.className = 'toast-close btn-icon';
  closeBtn.setAttribute('aria-label', 'Dismiss notification');
  closeBtn.textContent = '×';

  const dismiss = () => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(0.5rem)';
    setTimeout(() => el.remove(), 200);
  };

  closeBtn.addEventListener('click', dismiss);
  el.append(span, closeBtn);
  container.appendChild(el);

  if (durationMs > 0) {
    setTimeout(dismiss, durationMs);
  }
}

/**
 * Namespaced toast helpers.
 *
 * @example
 *   toast.success('Saved!');
 *   toast.error('Something went wrong.');
 *   toast.info('Exam will begin shortly.');
 */
export const toast = Object.freeze({
  /**
   * Show a success toast.
   * @param {string} msg — Message text.
   * @param {number} [ms=4500] — Auto-dismiss time.
   */
  success: (msg, ms) => showToast(msg, 'success', ms),

  /**
   * Show an error toast.
   * @param {string} msg — Message text.
   * @param {number} [ms=6000] — Auto-dismiss time (longer for errors).
   */
  error:   (msg, ms = 6000) => showToast(msg, 'error', ms),

  /**
   * Show an informational toast.
   * @param {string} msg — Message text.
   * @param {number} [ms=4500] — Auto-dismiss time.
   */
  info:    (msg, ms) => showToast(msg, 'info', ms),
});

/* ═══════════════════════════════════════════════════════════════════════════
   Confirm modal
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} ConfirmOptions
 * @property {string} [title='Please confirm']  — Modal heading.
 * @property {string} [body='Are you sure?']     — Description text.
 * @property {string} [confirmText='Confirm']    — Confirm button label.
 * @property {string} [cancelText='Cancel']      — Cancel button label.
 * @property {string} [confirmClass='btn-danger'] — CSS class for the confirm button.
 * @property {'primary'|'danger'} [confirmVariant] — Backward-compatible button style shortcut.
 */

/** The currently visible confirmation. A new prompt always closes the older one. */
let activeConfirmation = null;

/**
 * Show a confirmation modal and return a Promise that resolves to `true`
 * (confirmed) or `false` (cancelled / Escape / backdrop click).
 *
 * Falls back to `window.confirm()` if the modal DOM structure is missing.
 *
 * @param {ConfirmOptions} [opts={}]
 * @returns {Promise<boolean>}
 */
export function confirm(options = {}) {
  const {
    title = 'Please confirm',
    body = 'Are you sure you want to proceed?',
    confirmText = 'Confirm',
    cancelText = 'Cancel',
    confirmClass,
    confirmVariant,
  } = options;

  const resolvedConfirmClass = confirmClass
    || (confirmVariant === 'primary' ? 'btn-primary' : 'btn-danger');

  // Do not leave an old dialog blocking the page if a second action is started.
  activeConfirmation?.close(false);

  return new Promise((resolve) => {
    const returnFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const overlay = el('div', 'modal-overlay');
    const dialog = el('div', 'modal');
    const titleEl = el('h2', 'modal-title', title);
    const bodyEl = el('p', 'modal-body', body);
    const actions = el('div', 'modal-actions');
    const cancelBtn = button(cancelText, 'btn btn-secondary');
    const confirmBtn = button(confirmText, `btn ${resolvedConfirmClass}`);

    dialog.setAttribute('role', 'alertdialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.setAttribute('aria-labelledby', 'confirmation-title');
    dialog.setAttribute('aria-describedby', 'confirmation-message');
    titleEl.id = 'confirmation-title';
    bodyEl.id = 'confirmation-message';
    actions.append(cancelBtn, confirmBtn);
    dialog.append(titleEl, bodyEl, actions);
    overlay.appendChild(dialog);

    let closed = false;
    const close = (confirmed) => {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKey);
      overlay.removeEventListener('click', onBackdrop);
      overlay.remove();
      if (activeConfirmation?.close === close) activeConfirmation = null;
      returnFocus?.focus();
      resolve(confirmed);
    };
    const onConfirm = (event) => {
      event.preventDefault();
      event.stopPropagation();
      close(true);
    };
    const onCancel = (event) => {
      event.preventDefault();
      event.stopPropagation();
      close(false);
    };
    const onKey = (event) => {
      if (event.key === 'Escape') close(false);
    };
    const onBackdrop = (event) => {
      if (event.target === overlay) close(false);
    };

    confirmBtn.addEventListener('click', onConfirm);
    cancelBtn.addEventListener('click', onCancel);
    document.addEventListener('keydown', onKey);
    overlay.addEventListener('click', onBackdrop);
    activeConfirmation = { close };
    document.body.appendChild(overlay);
    confirmBtn.focus();
  });
}

/* ═══════════════════════════════════════════════════════════════════════════
   Table builder
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * @typedef {Object} ColumnDef
 * @property {string}  key       — Property name on the row object.
 * @property {string}  label     — Column header text.
 * @property {string}  [className] — Extra CSS class for `<td>`.
 * @property {function(*, Object): (string|HTMLElement)} [render]
 *   Custom cell renderer. Receives `(cellValue, rowObject)` and must return
 *   either a plain string (set via `textContent`) or an `HTMLElement`.
 */

/**
 * Build a complete `<div.table-responsive>` containing a `<table.data-table>`
 * from column definitions and row data.
 *
 * All text is set via `textContent` — never `innerHTML` — to prevent XSS.
 *
 * @param {Object}      opts
 * @param {ColumnDef[]} opts.columns    — Column definitions.
 * @param {Object[]}    opts.rows       — Array of row objects.
 * @param {string}      [opts.emptyText='No data available.'] — Message when rows is empty.
 * @param {string}      [opts.tableId]  — Optional `id` attribute for the `<table>`.
 * @returns {HTMLElement} The `.table-responsive` wrapper element.
 *
 * @example
 *   const el = buildTable({
 *     columns: [
 *       { key: 'title', label: 'Exam', className: 'td-primary' },
 *       { key: 'questions', label: 'Questions', className: 'td-center' },
 *     ],
 *     rows: exams,
 *     emptyText: 'No exams yet.',
 *   });
 *   container.appendChild(el);
 */
export function buildTable({ columns, rows, emptyText = 'No data available.', tableId }) {
  const wrapper = document.createElement('div');
  wrapper.className = 'table-responsive';

  const table = document.createElement('table');
  table.className = 'data-table';
  if (tableId) table.id = tableId;

  // ── thead ─────────────────────────────────────────────────────────
  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');
  for (const col of columns) {
    const th = document.createElement('th');
    th.textContent = col.label;
    headerRow.appendChild(th);
  }
  thead.appendChild(headerRow);
  table.appendChild(thead);

  // ── tbody ─────────────────────────────────────────────────────────
  const tbody = document.createElement('tbody');

  if (rows.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.className = 'td-empty';
    td.colSpan = columns.length;
    td.textContent = emptyText;
    tr.appendChild(td);
    tbody.appendChild(tr);
  } else {
    for (const row of rows) {
      const tr = document.createElement('tr');

      for (const col of columns) {
        const td = document.createElement('td');
        if (col.className) td.className = col.className;

        const value = row[col.key];

        if (col.render) {
          const rendered = col.render(value, row);
          if (rendered instanceof HTMLElement) {
            td.appendChild(rendered);
          } else {
            td.textContent = rendered ?? '';
          }
        } else {
          td.textContent = value ?? '';
        }

        tr.appendChild(td);
      }

      tbody.appendChild(tr);
    }
  }

  table.appendChild(tbody);
  wrapper.appendChild(table);
  return wrapper;
}

/* ═══════════════════════════════════════════════════════════════════════════
   Safe text / DOM helpers
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * Safely set the text content of an element found by `id`.
 * Does nothing if the element doesn't exist.
 *
 * @param {string} id   — Element ID.
 * @param {string} text — Text to set (via `textContent`, never `innerHTML`).
 * @returns {HTMLElement|null} The element, or `null` if not found.
 */
export function setText(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text;
  return el;
}

/**
 * Create a DOM element with a tag, optional classes, and safe text content.
 *
 * @param {string}   tag        — HTML tag name (e.g. `'span'`, `'div'`).
 * @param {string}   [className] — CSS class(es) to add.
 * @param {string}   [text]     — Text content (set via `textContent`).
 * @returns {HTMLElement}
 *
 * @example
 *   const badge = el('span', 'badge badge-emerald', 'Active');
 */
export function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

/**
 * Create a badge `<span>` element.
 *
 * @param {string} text      — Badge label.
 * @param {string} [variant='badge-indigo'] — Badge colour class.
 * @returns {HTMLElement}
 */
export function badge(text, variant = 'badge-indigo') {
  return el('span', `badge ${variant}`, text);
}

/**
 * Create a link `<a>` element.
 *
 * @param {string} text     — Link text (set via `textContent`).
 * @param {string} href     — Destination URL or hash.
 * @param {string} [className] — CSS class(es).
 * @returns {HTMLAnchorElement}
 */
export function link(text, href, className) {
  const a = /** @type {HTMLAnchorElement} */ (document.createElement('a'));
  a.textContent = text;
  a.href = href;
  if (className) a.className = className;
  return a;
}

/**
 * Create a button `<button>` element.
 *
 * @param {string}   text      — Button label.
 * @param {string}   [className='btn btn-secondary btn-sm'] — CSS classes.
 * @param {function} [onClick] — Click handler.
 * @returns {HTMLButtonElement}
 */
export function button(text, className = 'btn btn-secondary btn-sm', onClick) {
  const btn = /** @type {HTMLButtonElement} */ (document.createElement('button'));
  btn.type = 'button';
  btn.textContent = text;
  btn.className = className;
  if (onClick) btn.addEventListener('click', onClick);
  return btn;
}

/**
 * Clear a container element and append one or more children.
 *
 * @param {HTMLElement}     container — Parent element to clear.
 * @param {...(HTMLElement|string)} children — Elements or text to append.
 */
export function render(container, ...children) {
  container.textContent = '';
  for (const child of children) {
    if (typeof child === 'string') {
      container.appendChild(document.createTextNode(child));
    } else {
      container.appendChild(child);
    }
  }
}

/**
 * Render a skeleton placeholder loader container.
 *
 * @param {HTMLElement} container
 * @param {number} [cardCount=3]
 */
export function renderSkeleton(container, cardCount = 3) {
  container.textContent = '';
  const wrapper = document.createElement('div');
  wrapper.className = 'skeleton-group';
  wrapper.setAttribute('aria-hidden', 'true');

  const title = document.createElement('div');
  title.className = 'skeleton skeleton-title';
  wrapper.appendChild(title);

  const text = document.createElement('div');
  text.className = 'skeleton skeleton-text';
  wrapper.appendChild(text);

  const grid = document.createElement('div');
  grid.className = 'stats-grid';
  grid.style.marginBottom = '2rem';
  for (let i = 0; i < cardCount; i++) {
    const card = document.createElement('div');
    card.className = 'skeleton skeleton-card';
    grid.appendChild(card);
  }
  wrapper.appendChild(grid);

  const tableCard = document.createElement('div');
  tableCard.className = 'skeleton skeleton-card';
  tableCard.style.height = '240px';
  wrapper.appendChild(tableCard);

  container.appendChild(wrapper);
}

/**
 * Create an interactive data table with live search and column sorting.
 *
 * @param {Object} opts
 * @param {Array<{key: string, label: string, sortable?: boolean, className?: string, render?: function(any, Object): (HTMLElement|string)}>} opts.columns
 * @param {Array<Object>} opts.data
 * @param {Array<string>} [opts.searchKeys]
 * @param {string} [opts.searchPlaceholder='Search records...']
 * @param {string} [opts.emptyText='No matching records found.']
 * @param {HTMLElement|null} [opts.actionsElement]
 * @returns {HTMLElement}
 */
export function createDataTable({
  columns,
  data = [],
  searchKeys = [],
  searchPlaceholder = 'Search records...',
  emptyText = 'No matching records found.',
  actionsElement = null,
}) {
  const container = document.createElement('div');
  container.className = 'interactive-table-wrapper';

  const toolbar = document.createElement('div');
  toolbar.className = 'table-toolbar';

  const searchInput = document.createElement('input');
  searchInput.type = 'search';
  searchInput.className = 'form-control table-search';
  searchInput.placeholder = searchPlaceholder;
  toolbar.appendChild(searchInput);

  if (actionsElement) {
    toolbar.appendChild(actionsElement);
  }
  container.appendChild(toolbar);

  const tableWrapper = document.createElement('div');
  tableWrapper.className = 'table-responsive';

  const table = document.createElement('table');
  table.className = 'data-table';

  let sortKey = null;
  let sortAsc = true;
  let query = '';

  const thead = document.createElement('thead');
  const headerRow = document.createElement('tr');

  const headers = [];
  columns.forEach(col => {
    const th = document.createElement('th');
    th.textContent = col.label;
    if (col.className) th.className = col.className;

    if (col.sortable !== false && col.key) {
      th.classList.add('th-sortable');
      const indicator = document.createElement('span');
      indicator.className = 'sort-indicator';
      indicator.textContent = '↕';
      th.appendChild(indicator);

      th.addEventListener('click', () => {
        if (sortKey === col.key) {
          sortAsc = !sortAsc;
        } else {
          sortKey = col.key;
          sortAsc = true;
        }
        updateHeaders();
        renderRows();
      });
    }
    headers.push({ th, col });
    headerRow.appendChild(th);
  });
  thead.appendChild(headerRow);
  table.appendChild(thead);

  function updateHeaders() {
    headers.forEach(({ th, col }) => {
      const ind = th.querySelector('.sort-indicator');
      if (!ind) return;
      if (sortKey === col.key) {
        ind.textContent = sortAsc ? '↑' : '↓';
        ind.style.opacity = '1';
      } else {
        ind.textContent = '↕';
        ind.style.opacity = '0.5';
      }
    });
  }

  const tbody = document.createElement('tbody');
  table.appendChild(tbody);
  tableWrapper.appendChild(table);
  container.appendChild(tableWrapper);

  function renderRows() {
    tbody.textContent = '';

    let rows = [...data];
    if (query.trim()) {
      const q = query.toLowerCase();
      rows = rows.filter(item => {
        const keys = searchKeys.length > 0 ? searchKeys : columns.map(c => c.key).filter(Boolean);
        return keys.some(k => {
          const val = item[k];
          return val !== null && val !== undefined && String(val).toLowerCase().includes(q);
        });
      });
    }

    if (sortKey) {
      rows.sort((a, b) => {
        let va = a[sortKey];
        let vb = b[sortKey];
        if (typeof va === 'string') va = va.toLowerCase();
        if (typeof vb === 'string') vb = vb.toLowerCase();
        if (va < vb) return sortAsc ? -1 : 1;
        if (va > vb) return sortAsc ? 1 : -1;
        return 0;
      });
    }

    if (rows.length === 0) {
      const tr = document.createElement('tr');
      const td = document.createElement('td');
      td.className = 'td-empty';
      td.colSpan = columns.length;
      td.textContent = emptyText;
      tr.appendChild(td);
      tbody.appendChild(tr);
      return;
    }

    rows.forEach(row => {
      const tr = document.createElement('tr');
      columns.forEach(col => {
        const td = document.createElement('td');
        if (col.className) td.className = col.className;
        const val = row[col.key];
        if (col.render) {
          const res = col.render(val, row);
          if (res instanceof HTMLElement) {
            td.appendChild(res);
          } else {
            td.textContent = res ?? '';
          }
        } else {
          td.textContent = val ?? '';
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
  }

  searchInput.addEventListener('input', (e) => {
    query = e.target.value;
    renderRows();
  });

  renderRows();
  return container;
}
