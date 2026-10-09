/**
 * @fileoverview ExamPro — Student Dashboard View (ES module).
 * 21st.dev style cards, active assessment sittings, and candidate scorecard review.
 */

import { get } from '../api.js';
import { el, badge, link, createDataTable, renderSkeleton } from '../ui.js';
import { getUser } from '../state.js';

/**
 * Render the Student Dashboard into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const user = params.user || getUser();
  renderSkeleton(container, 4);

  let stats, exams;
  try {
    [stats, exams] = await Promise.all([
      get('/api/stats').catch(() => ({ totalExams: 0, totalAttempts: 0, avgPercentage: 0, passRate: 0, recentAttempts: [] })),
      get('/api/exams').catch(() => [])
    ]);
  } catch (err) {
    stats = { totalExams: 0, totalAttempts: 0, avgPercentage: 0, passRate: 0, recentAttempts: [] };
    exams = [];
  }

  container.textContent = '';

  // ── Header ─────────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  const roleBadge = badge('STUDENT CANDIDATE PORTAL', 'badge-cyan');
  const pageTitle = el('h1', 'page-title', `Welcome, ${user?.displayName || user?.username || 'Candidate'}`);
  const pageDesc = el('p', 'page-description', 'Access scheduled assessment modules, sit timed examinations, and review verified academic scorecards.');
  titleGroup.append(roleBadge, pageTitle, pageDesc);

  const headerActions = el('div', 'page-actions');
  const browseBtn = link('Browse All Exams', '#/exams', 'btn btn-primary');
  const myResultsBtn = link('My Scorecards', '#/results', 'btn btn-secondary');
  headerActions.append(browseBtn, myResultsBtn);

  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Stat Tiles ─────────────────────────────────────────────────────────────
  const statsGrid = el('div', 'stats-grid');
  const statDefs = [
    { label: 'Available Assessments', value: String(exams.length || stats.totalExams || 0), sub: 'Ready to sit' },
    { label: 'Completed Attempts', value: String(stats.totalAttempts || 0), sub: 'Recorded submissions' },
    { label: 'Average Score', value: `${stats.avgPercentage || 0}%`, sub: 'Overall performance' },
    { label: 'Assessment Pass Rate', value: `${stats.passRate || 0}%`, sub: 'Passing threshold 50%' },
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

  // ── Available Examinations Grid ────────────────────────────────────────────
  const examsSection = el('div', 'card');
  const examsHeader = el('div', 'card-header');
  examsHeader.append(
    el('h2', 'card-title', 'Available Examinations'),
    el('p', 'card-subtitle', 'Standardized assessments scheduled for your academic program')
  );
  examsSection.appendChild(examsHeader);

  if (!exams || exams.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No examinations currently available'),
      el('p', 'empty-state-body', 'Your instructors have not scheduled any assessment sessions at this time. Please check back later.')
    );
    examsSection.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'title', label: 'Examination Title', className: 'td-strong', sortable: true },
        { key: 'questionCount', label: 'Questions', sortable: true, render: (v) => `${v} Questions` },
        { key: 'totalMarks', label: 'Weighting', sortable: true, render: (v) => `${v} Marks` },
        {
          key: 'durationMinutes',
          label: 'Duration',
          sortable: true,
          render: (v) => badge(`${v} MINS`, 'badge-indigo')
        },
        {
          key: 'id',
          label: 'Sitting Action',
          sortable: false,
          className: 'td-right',
          render: (id) => link('Start Exam →', `#/exams/${id}/take`, 'btn btn-primary btn-sm')
        }
      ],
      data: exams,
      searchKeys: ['title'],
      searchPlaceholder: 'Search available exams...',
      emptyText: 'No matching examinations found.',
    });
    examsSection.appendChild(tableEl);
  }
  container.appendChild(examsSection);

  // ── My Recent Submissions / Scorecards ─────────────────────────────────────
  const resultsSection = el('div', 'card');
  const resultsHeader = el('div', 'card-header');
  resultsHeader.append(
    el('h2', 'card-title', 'Your Completed Assessments'),
    el('p', 'card-subtitle', 'Past attempts and verified performance scorecards')
  );
  resultsSection.appendChild(resultsHeader);

  const attempts = stats.recentAttempts || [];
  if (attempts.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No examination attempts completed yet'),
      el('p', 'empty-state-body', 'Once you complete an examination sitting, your instant score breakdown and verified scorecard will appear here.')
    );
    resultsSection.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'examTitle', label: 'Examination', className: 'td-strong', sortable: true },
        {
          key: 'score',
          label: 'Achieved Score',
          sortable: true,
          render: (v, row) => `${row.score} / ${row.totalMarks} (${row.percentage}%)`
        },
        {
          key: 'passed',
          label: 'Result',
          sortable: true,
          render: (v, row) => badge(row.passed ? 'PASSED' : 'RETAKE', row.passed ? 'badge-emerald' : 'badge-rose')
        },
        { key: 'submittedAtFormatted', label: 'Submission Date', className: 'td-sub', sortable: true },
        {
          key: 'action',
          label: 'Action',
          sortable: false,
          render: (_, row) => link('Scorecard', row.attemptId ? `#/scorecard/${row.attemptId}` : '#/results', 'btn btn-secondary btn-sm')
        }
      ],
      data: attempts,
      searchKeys: ['examTitle'],
      searchPlaceholder: 'Search your past attempts...',
      emptyText: 'No matching attempts found.',
    });
    resultsSection.appendChild(tableEl);
  }
  container.appendChild(resultsSection);
}
