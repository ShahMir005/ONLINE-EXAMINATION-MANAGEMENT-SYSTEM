# ExamPro Design System

## Guiding Principles
- **Tone**: calm, academic, trustworthy. Plain student‑friendly words. Never name technologies in UI text.
- **Shape**: radius 4–8 px, thin 1 px borders, one subtle shadow. No gradients, no glass, no emoji.
- **Layout**: max‑width 1100 px, 8 px grid, one primary action per screen, works at 360 px.
- **A11y**: contrast ≥ 4.5:1, visible `:focus-visible` rings (2 px accent), keyboard navigable.
- **Code rules**: all styling via classes from `css/*.css` using CSS custom properties. No inline styles. No inline script logic.

---

## Design Tokens (`css/tokens.css`)

### Typography
| Token | Value |
|---|---|
| `--font-serif` | `'Newsreader', Georgia, serif` |
| `--font-sans` | `'Plus Jakarta Sans', -apple-system, sans-serif` |
| `--font-mono` | `'JetBrains Mono', Menlo, monospace` |

### Colour palette — Archival Paper & Carbon Ink
| Token | Hex | Usage |
|---|---|---|
| `--bg-canvas` | `#fcfbf8` | page background |
| `--bg-surface` | `#ffffff` | cards, nav |
| `--bg-subtle` | `#f5f3ec` | table headers, hover states |
| `--bg-hover` | `#edeae0` | active nav, hovered rows |
| `--text-primary` | `#141517` | headings, labels |
| `--text-secondary` | `#4a4e57` | body copy |
| `--text-muted` | `#737782` | captions, placeholders |
| `--text-dim` | `#9b9ea6` | disabled states |
| `--text-white` | `#ffffff` | text on dark/accent |
| `--border-subtle` | `#e5e1d7` | card/row borders |
| `--border-medium` | `#c8c3b7` | input borders |
| `--border-strong` | `#141517` | hero top‑bar, section dividers |
| `--accent` | `#b84223` | primary CTA, links on hover |
| `--accent-hover` | `#993319` | pressed primary button |
| `--accent-subtle` | `#fbf2ee` | badge background, selected choice |
| `--accent-border` | `#e8bfb3` | accent badge border |

### Status colours
| Token | Value |
|---|---|
| `--status-success` | `#1b5e39` |
| `--status-success-bg` | `#eff8f2` |
| `--status-success-border` | `#bce3cb` |
| `--status-danger` | `#a32a2a` |
| `--status-danger-bg` | `#fdf2f2` |
| `--status-danger-border` | `#f0bebe` |
| `--status-warning` | `#925b08` |
| `--status-warning-bg` | `#fef8ed` |
| `--status-warning-border` | `#fae1b8` |
| `--status-info` | `#1e3a8a` |
| `--status-info-bg` | `#eff6ff` |
| `--status-info-border` | `#bfdbfe` |

### Geometry
| Token | Value |
|---|---|
| `--radius-xs` | `2px` |
| `--radius-sm` | `4px` |
| `--radius-md` | `6px` |
| `--radius-lg` | `8px` |
| `--radius-full` | `9999px` |
| `--shadow-sm` | `0 1px 2px rgba(20,21,23,.04)` |
| `--shadow-md` | `0 2px 6px rgba(20,21,23,.05), 0 1px 3px rgba(20,21,23,.03)` |

### Transitions
| Token | Value |
|---|---|
| `--transition-fast` | `0.12s ease-out` |
| `--transition-normal` | `0.2s ease-out` |

---

## CSS File Map
| File | Responsibility |
|---|---|
| `css/tokens.css` | `:root` custom properties only |
| `css/layout.css` | Body, navbar, container, footer, page‑header, stats‑grid |
| `css/components.css` | Buttons, alerts, cards, tables, badges, forms, modals, toasts, skeletons |
| `css/exam.css` | Exam‑session header, timer, question blocks, choice cards, submit bar, scorecard, how‑steps |
| `css/main.css` | `@import` aggregator + Google Fonts import |

---

## Component Catalogue

### Buttons
- `.btn` – base (min‑height 44 px, `var(--font-sans)`, 0.88 rem, weight 600)
- `.btn-primary` – accent fill
- `.btn-secondary` – surface + border
- `.btn-sm` – min‑height 36 px
- `.btn-lg` – min‑height 48 px
- All buttons have visible `:focus-visible` ring

### Inputs
- `.form-control` – min‑height 44 px, accent focus ring
- `.form-label` – 0.85 rem 600 weight
- `.form-error-msg` – danger colour, shown when `.form-control.is-error`
- `.form-hint` – muted helper text

### Cards
- `.card` – white, subtle border, `var(--shadow-sm)`, radius‑sm, 2 rem padding
- `.card-header` – flex space‑between, bottom border
- `.card-title` – serif 1.4 rem 600

### Tables
- `.data-table` – collapsed, sticky `thead`, 0.9 rem
- `.td-strong` / `.td-sub` / `.td-empty` – cell helpers

### Badges
- `.badge` – mono 0.72 rem uppercase; colour variants: `badge-indigo`, `badge-emerald`, `badge-amber`, `badge-rose`, `badge-cyan`

### Alerts (inline page messages)
- `.alert-success` / `.alert-danger` / `.alert-info`

### Toast (JS‑driven)
- `.toast-container` appended to body; individual `.toast-success` / `.toast-error` / `.toast-info`

### Skeleton loaders
- `.skeleton` – animated shimmer block; use while async data loads

### Confirm modal
- `.modal-overlay > .modal` – for destructive actions

### Navigation
- `.navbar` – sticky top, white, subtle bottom border
- `.nav-links` – flex list; `.nav-item.active` underlined with accent
- `.nav-cta` – right‑side auth area

---

## Routing (SPA hash‑router, `js/router.js`)
| Hash | View module | Auth required | Roles |
|---|---|---|---|
| `#/` | `views/home.js` | yes | ALL |
| `#/exams` | `views/exams.js` | yes | ALL |
| `#/exams/new` | `views/exam-create.js` | yes | ADMIN, TEACHER |
| `#/exams/:id/take` | `views/exam‑take.js` | yes | STUDENT, ADMIN |
| `#/results` | `views/results.js` | yes | ALL |
| `#/students` | `views/students.js` | yes | ADMIN |
| `#/admin/users` | `views/users.js` | yes | ADMIN |

---

## API Contract (`/api/**`)
All endpoints return `application/json`.  
Authentication via existing Spring Security session cookie.  
CSRF token sent in `X-XSRF-TOKEN` header (obtained from `XSRF‑TOKEN` cookie).

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/api/auth/me` | — | 200 `{username,role,studentId?}` or 401 |
| POST | `/api/auth/logout` | any | 200, session invalidated |
| GET | `/api/exams` | ALL | list, no correct answers |
| GET | `/api/exams/{id}` | ALL | detail; STUDENT never sees correct answers |
| POST | `/api/exams` | ADMIN, TEACHER | create |
| DELETE | `/api/exams/{id}` | ADMIN, TEACHER | delete |
| POST | `/api/exams/{id}/submit` | STUDENT, ADMIN | returns `{attemptId,score,totalMarks,passed}` |
| GET | `/api/students` | ADMIN | list |
| POST | `/api/students` | ADMIN | create |
| DELETE | `/api/students/{id}` | ADMIN | delete |
| GET | `/api/results` | ALL | ownership enforced in controller |
| GET | `/api/results/{attemptId}` | ALL | ownership enforced |
| GET | `/api/users` | ADMIN | login accounts list |
| POST | `/api/users` | ADMIN | create login account |
| POST | `/api/stats` | ALL | dashboard counts |
| POST | `/demo/seed` | ADMIN | reset demo data |
