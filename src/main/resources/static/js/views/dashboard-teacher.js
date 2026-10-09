/**
 * @fileoverview ExamPro — Teacher Dashboard View (ES module).
 * 21st.dev style cards, assessment authoring metrics, curriculum overview,
 * and student submissions with live search and column sorting.
 */

import { get } from '../api.js';
import { el, badge, link, createDataTable, renderSkeleton } from '../ui.js';
import { getUser } from '../state.js';

/**
 * Render the Teacher Dashboard into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const user = params.user || getUser();
  renderSkeleton(container, 3);

  let stats, exams;
  try {
    [stats, exams] = await Promise.all([
      get('/api/stats').catch(() => ({ totalExams: 0, totalQuestions: 0, totalAttempts: 0, passRate: 0, avgPercentage: 0, recentAttempts: [] })),
      get('/api/exams').catch(() => [])
    ]);
  } catch (err) {
    stats = { totalExams: 0, totalQuestions: 0, totalAttempts: 0, passRate: 0, avgPercentage: 0, recentAttempts: [] };
    exams = [];
  }

  container.textContent = '';

  // ── Header ─────────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  const roleBadge = badge('FACULTY PORTAL • TEACHER', 'badge-amber');
  const pageTitle = el('h1', 'page-title', `Welcome, ${user?.displayName || user?.username || 'Faculty Instructor'}`);
  const pageDesc = el('p', 'page-description', 'Curriculum authoring suite, test specification management, and candidate grading oversight.');
  titleGroup.append(roleBadge, pageTitle, pageDesc);

  const headerActions = el('div', 'page-actions');
  const createExamBtn = link('+ Author New Exam', '#/exams/new', 'btn btn-primary');
  const viewResultsBtn = link('Review Cohort Results', '#/results', 'btn btn-secondary');
  headerActions.append(createExamBtn, viewResultsBtn);

  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Stat Tiles ─────────────────────────────────────────────────────────────
  const statsGrid = el('div', 'stats-grid');
  const statDefs = [
    { label: 'Curriculum Exams', value: String(exams.length || stats.totalExams || 0), sub: 'Active modules' },
    { label: 'Questions Authored', value: String(stats.totalQuestions || 0), sub: 'Across syllabus' },
    { label: 'Submissions Evaluated', value: String(stats.totalAttempts || 0), sub: 'Completed candidate attempts' },
    { label: 'Cohort Pass Rate', value: `${stats.passRate || 0}%`, sub: '50% passing threshold' },
    { label: 'Cohort Average Score', value: `${stats.avgPercentage || 0}%`, sub: 'Mean percentage' },
  ];

  statDefs.forEach(({ label, value, sub }) => {
    const tile = el('div', 'stat-card');
    tile.append(
      el('span', 'stat-label', label),
      el('span', 'stat-value', value),
      el('span', 'stat-sub', sub)
    );
    statsGrid.appendChild(tile);
  });
  container.appendChild(statsGrid);

  // ── Examinations Catalog Overview ──────────────────────────────────────────
  const catalogSection = el('div', 'card');
  const catalogHeader = el('div', 'card-header');
  catalogHeader.append(
    el('h2', 'card-title', 'Curriculum Examinations Overview'),
    el('p', 'card-subtitle', 'Standardized assessments currently live in the examination catalog')
  );
  catalogSection.appendChild(catalogHeader);

  if (!exams || exams.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No examinations authored yet'),
      el('p', 'empty-state-body', 'Begin by defining your first assessment specification, question types, and mark scheme.'),
      link('+ Author New Exam', '#/exams/new', 'btn btn-primary btn-sm')
    );
    catalogSection.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'id', label: 'Ref', className: 'td-mono', sortable: true, render: (v) => `#${v}` },
        { key: 'title', label: 'Title', className: 'td-strong', sortable: true },
        { key: 'questionCount', label: 'Questions', sortable: true, render: (v) => `${v} questions` },
        { key: 'totalMarks', label: 'Total Marks', sortable: true, render: (v) => `${v} marks` },
        {
          key: 'durationMinutes',
          label: 'Duration',
          sortable: true,
          render: (v) => badge(`${v} MINS`, 'badge-indigo')
        },
        {
          key: 'action',
          label: 'Action',
          sortable: false,
          className: 'td-right',
          render: (v, row) => link('Inspect', `#/exams`, 'btn btn-secondary btn-sm')
        }
      ],
      data: exams,
      searchKeys: ['title'],
      searchPlaceholder: 'Search curriculum exams...',
      emptyText: 'No matching examinations found.',
    });
    catalogSection.appendChild(tableEl);
  }
  container.appendChild(catalogSection);

  // ── Recent Candidate Submissions ───────────────────────────────────────────
  const recentSection = el('div', 'card');
  const recentHeader = el('div', 'card-header');
  recentHeader.append(
    el('h2', 'card-title', 'Recent Student Activity'),
    el('p', 'card-subtitle', 'Submissions received from candidate test sittings')
  );
  recentSection.appendChild(recentHeader);

  const attempts = stats.recentAttempts || [];
  if (attempts.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No submissions recorded yet'),
      el('p', 'empty-state-body', 'Candidate attempts will be registered here immediately upon test submission.')
    );
    recentSection.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'examTitle', label: 'Exam', className: 'td-strong', sortable: true },
        { key: 'studentName', label: 'Candidate', sortable: true },
        {
          key: 'score',
          label: 'Score',
          sortable: true,
          render: (val, row) => `${row.score} / ${row.totalMarks} (${row.percentage}%)`
        },
        {
          key: 'passed',
          label: 'Result',
          sortable: true,
          render: (val, row) => badge(row.passed ? 'PASSED' : 'RETAKE', row.passed ? 'badge-emerald' : 'badge-rose')
        },
        { key: 'submittedAtFormatted', label: 'Date', className: 'td-sub', sortable: true },
        {
          key: 'action',
          label: 'Action',
          sortable: false,
          render: (_, row) => link('Scorecard', row.attemptId ? `#/scorecard/${row.attemptId}` : '#/results', 'btn btn-secondary btn-sm')
        }
      ],
      data: attempts,
      searchKeys: ['examTitle', 'studentName'],
      searchPlaceholder: 'Search submissions...',
      emptyText: 'No matching submissions found.',
    });
    recentSection.appendChild(tableEl);
  }
  container.appendChild(recentSection);
}
