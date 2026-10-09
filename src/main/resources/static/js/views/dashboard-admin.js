/**
 * @fileoverview ExamPro — Admin Dashboard View (ES module).
 * 21st.dev style data cards, system governance stat tiles, quick navigation,
 * and recent submissions inspection with search and sort.
 */

import { get, post } from '../api.js';
import { toast, confirm, el, badge, button, link, createDataTable, renderSkeleton } from '../ui.js';
import { getUser } from '../state.js';

/**
 * Render the Admin Dashboard into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const user = params.user || getUser();
  renderSkeleton(container, 4);

  let stats;
  try {
    stats = await get('/api/stats');
  } catch (err) {
    stats = {
      totalExams: 0,
      totalQuestions: 0,
      totalStudents: 0,
      totalAttempts: 0,
      avgPercentage: 0,
      passRate: 0,
      totalUsers: 0,
      recentAttempts: [],
    };
  }

  container.textContent = '';

  // ── Page Header ────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  const roleBadge = badge('SYSTEM GOVERNANCE • ADMIN', 'badge-rose');
  const pageTitle = el('h1', 'page-title', `Welcome, ${user?.displayName || user?.username || 'Administrator'}`);
  const pageDesc = el('p', 'page-description', 'Institutional control center for managing examination curricula, candidate rosters, faculty credentials, and assessment records.');
  titleGroup.append(roleBadge, pageTitle, pageDesc);

  const headerActions = el('div', 'page-actions');
  const createExamBtn = link('+ Author Exam', '#/exams/new', 'btn btn-primary');
  const seedBtn = button('Reset Sample Data', 'btn btn-secondary', async () => {
    const ok = await confirm({
      title: 'Reset Sample Data',
      body: 'This will reset the database with standard baseline exams, candidates, and submissions. Custom test records will be cleared.',
      confirmText: 'Reset Sample Data',
      confirmVariant: 'danger',
    });
    if (!ok) return;

    try {
      await post('/api/demo/seed');
      toast.success('Sample examination data has been restored.');
      render(container, params);
    } catch (err) {
      toast.error('Failed to reset sample data: ' + (err.data?.error || err.message));
    }
  });

  headerActions.append(createExamBtn, seedBtn);
  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Stat Tiles (21st.dev Inspired) ─────────────────────────────────────────
  const statsGrid = el('div', 'stats-grid');

  const statDefs = [
    { label: 'Exams Configured', value: String(stats.totalExams ?? 0), sub: `${stats.totalQuestions ?? 0} syllabus questions` },
    { label: 'Candidate Roster', value: String(stats.totalStudents ?? 0), sub: 'Enrolled students' },
    { label: 'Submissions Logged', value: String(stats.totalAttempts ?? 0), sub: 'Completed sessions' },
    { label: 'Pass Rate', value: `${stats.passRate ?? 0}%`, sub: '50% passing threshold' },
    { label: 'Average Score', value: `${stats.avgPercentage ?? 0}%`, sub: 'Across all cohorts' },
    { label: 'User Accounts', value: String(stats.totalUsers ?? stats.totalStudents ?? 0), sub: 'Admin, Faculty & Candidates' },
  ];

  statDefs.forEach(({ label, value, sub }) => {
    const tile = el('div', 'stat-card');
    const lbl = el('span', 'stat-label', label);
    const val = el('span', 'stat-value', value);
    const sb = el('span', 'stat-sub', sub);
    tile.append(lbl, val, sb);
    statsGrid.appendChild(tile);
  });
  container.appendChild(statsGrid);

  // ── Quick Administration Hub ───────────────────────────────────────────────
  const navSection = el('div', 'card');
  const navHeader = el('div', 'card-header');
  navHeader.append(
    el('h2', 'card-title', 'Administrative Directory'),
    el('p', 'card-subtitle', 'Direct management consoles across all core modules')
  );
  navSection.appendChild(navHeader);

  const hubGrid = el('div', 'stats-grid');
  hubGrid.style.marginBottom = '0';

  const hubs = [
    { title: 'User Accounts', desc: 'Control login credentials, change passwords, and manage activation statuses.', href: '#/admin/users', label: 'Manage Accounts' },
    { title: 'Candidate Directory', desc: 'Maintain student records, registration numbers, and identity profiles.', href: '#/students', label: 'Manage Students' },
    { title: 'Examination Catalog', desc: 'Inspect curricula, configure mark weights, or author new assessments.', href: '#/exams', label: 'View Catalog' },
    { title: 'Performance Registry', desc: 'Review completed candidate submissions, scores, and itemised scorecards.', href: '#/results', label: 'Inspect Results' },
  ];

  hubs.forEach(h => {
    const card = el('div', 'stat-tile');
    card.append(
      el('span', 'stat-tile-label', h.title),
      el('p', 'activity-text', h.desc),
      link(h.label, h.href, 'btn btn-secondary btn-sm')
    );
    hubGrid.appendChild(card);
  });

  navSection.appendChild(hubGrid);
  container.appendChild(navSection);

  // ── Recent Submissions Activity Table ──────────────────────────────────────
  const recentSection = el('div', 'card');
  const recentHeader = el('div', 'card-header');
  recentHeader.append(
    el('h2', 'card-title', 'Recent Examination Submissions'),
    el('p', 'card-subtitle', 'Real-time assessment submissions with search and column sorting')
  );
  recentSection.appendChild(recentHeader);

  const recentAttempts = stats.recentAttempts || [];
  if (recentAttempts.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No submissions recorded yet'),
      el('p', 'empty-state-body', 'Candidate examination submissions will automatically appear here once tests are completed.')
    );
    recentSection.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'examTitle', label: 'Examination', className: 'td-strong', sortable: true },
        { key: 'studentName', label: 'Candidate', sortable: true },
        {
          key: 'score',
          label: 'Score',
          sortable: true,
          render: (val, row) => `${row.score} / ${row.totalMarks} (${row.percentage}%)`
        },
        {
          key: 'passed',
          label: 'Status',
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
      data: recentAttempts,
      searchKeys: ['examTitle', 'studentName'],
      searchPlaceholder: 'Search recent submissions...',
      emptyText: 'No matching submissions found.',
    });
    recentSection.appendChild(tableEl);
  }

  container.appendChild(recentSection);
}
