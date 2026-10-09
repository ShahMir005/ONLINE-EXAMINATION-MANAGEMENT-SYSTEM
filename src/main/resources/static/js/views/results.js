/**
 * @fileoverview ExamPro — Assessment Results & Scorecard View (ES module).
 * Results registry with examination filter, live search, column sorting,
 * performance metrics, and question-by-question verified scorecard dialog.
 */

import { get } from '../api.js';
import { toast, el, badge, link, createDataTable, renderSkeleton } from '../ui.js';
import { getUser } from '../state.js';

/**
 * Render the Results Registry into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const user = params.user || getUser();
  const isStudent = user?.role === 'STUDENT';

  renderSkeleton(container, 3);

  let exams = [];
  try {
    exams = await get('/api/exams');
  } catch {
    exams = [];
  }

  let selectedExamId = '';

  async function loadDataAndRender() {
    let data;
    try {
      const url = selectedExamId ? `/api/results?examId=${selectedExamId}` : '/api/results';
      data = await get(url);
    } catch (err) {
      toast.error('Failed to load assessment results: ' + (err.data?.error || err.message));
      data = { results: [], totalAttempts: 0, avgPercentage: 0, maxScore: 0, passRate: 0 };
    }

    const { results = [], totalAttempts = 0, avgPercentage = 0, maxScore = 0, passRate = 0 } = data;
    const normalizedResults = results.map((row) => ({
      ...row,
      studentRegistration: row.studentRegistration ?? row.registrationNumber,
    }));

    container.textContent = '';

    // ── Header ───────────────────────────────────────────────────────────────
    const header = el('div', 'page-header');
    const headerRow = el('div', 'page-header-row');

    const titleGroup = el('div');
    titleGroup.append(
      el('h1', 'page-title', isStudent ? 'Your Assessment Results' : 'Candidate Results Registry'),
      el('p', 'page-description', isStudent
        ? 'Review your recorded test submissions, scores, and verified question-by-question scorecards.'
        : 'Institutional performance records, cohort analytics, and itemised candidate scorecards.')
    );

    const headerActions = el('div', 'page-actions');

    // Filter dropdown for exams
    if (exams.length > 0) {
      const filterGroup = el('div');
      filterGroup.style.display = 'flex';
      filterGroup.style.alignItems = 'center';
      filterGroup.style.gap = '0.5rem';

      const filterLabel = el('label', 'form-label', 'Exam:');
      filterLabel.style.margin = '0';
      filterLabel.style.whiteSpace = 'nowrap';

      const filterSelect = document.createElement('select');
      filterSelect.className = 'form-control';
      filterSelect.style.minWidth = '220px';

      const allOpt = document.createElement('option');
      allOpt.value = '';
      allOpt.textContent = 'All Examinations';
      filterSelect.appendChild(allOpt);

      exams.forEach(e => {
        const opt = document.createElement('option');
        opt.value = String(e.id);
        opt.textContent = e.title;
        if (String(e.id) === String(selectedExamId)) opt.selected = true;
        filterSelect.appendChild(opt);
      });

      filterSelect.addEventListener('change', (e) => {
        selectedExamId = e.target.value;
        loadDataAndRender();
      });

      filterGroup.append(filterLabel, filterSelect);
      headerActions.appendChild(filterGroup);
    }

    headerRow.append(titleGroup, headerActions);
    header.appendChild(headerRow);
    container.appendChild(header);

    // ── Stat Tiles ───────────────────────────────────────────────────────────
    const statsGrid = el('div', 'stats-grid');
    const statDefs = [
      { label: 'Total Submissions', value: String(totalAttempts), sub: isStudent ? 'Your completed attempts' : 'Evaluated attempts' },
      { label: 'Average Score', value: `${avgPercentage}%`, sub: 'Across displayed attempts' },
      { label: 'Highest Score', value: `${maxScore}%`, sub: 'Peak performance' },
      { label: 'Pass Rate', value: `${passRate}%`, sub: '50% passing threshold' },
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

    // ── Results Table ────────────────────────────────────────────────────────
    const card = el('div', 'card');

    if (!results || results.length === 0) {
      const empty = el('div', 'empty-state');
      empty.append(
        el('h3', 'empty-state-title', 'No examination results found'),
        el('p', 'empty-state-body', isStudent
          ? 'You have not completed any examination attempts yet. Head to the catalog to sit an assessment.'
          : 'No candidate submissions match the selected filter.')
      );
      card.appendChild(empty);
    } else {
      const columns = [
        { key: 'attemptId', label: 'Ref', className: 'td-mono', sortable: true, render: (v) => `#${v}` },
        { key: 'examTitle', label: 'Examination Title', className: 'td-strong', sortable: true },
      ];

      if (!isStudent) {
        columns.push({ key: 'studentName', label: 'Candidate', sortable: true });
        columns.push({ key: 'studentRegistration', label: 'Reg No.', sortable: true, render: (v) => badge(v || 'N/A', 'badge-indigo') });
      }

      columns.push({
        key: 'score',
        label: 'Score',
        sortable: true,
        render: (v, row) => `${row.score} / ${row.totalMarks} (${row.percentage}%)`
      });

      columns.push({
        key: 'passed',
        label: 'Result',
        sortable: true,
        render: (p) => badge(p ? 'PASSED' : 'RETAKE', p ? 'badge-emerald' : 'badge-rose')
      });

      columns.push({
        key: 'submittedAtFormatted',
        label: 'Submitted Date',
        className: 'td-sub',
        sortable: true
      });

      columns.push({
        key: 'action',
        label: 'Scorecard',
        sortable: false,
        render: (_, row) => link('View Scorecard', `#/scorecard/${row.attemptId}`, 'btn btn-secondary btn-sm')
      });

      const tableEl = createDataTable({
        columns,
        data: normalizedResults,
        searchKeys: isStudent ? ['examTitle'] : ['examTitle', 'studentName', 'studentRegistration'],
        searchPlaceholder: isStudent ? 'Search results by exam...' : 'Search by exam or candidate name...',
        emptyText: 'No matching assessment results found.',
      });
      card.appendChild(tableEl);
    }

    container.appendChild(card);
  }

  loadDataAndRender();
}
