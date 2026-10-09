/**
 * @fileoverview ExamPro — Examinations Catalog View (ES module).
 * Features data table with live search, column sorting, inspection modal,
 * role-specific actions, empty states, and loading skeleton.
 */

import { get, del } from '../api.js';
import { toast, confirm, el, badge, button, link, createDataTable, renderSkeleton } from '../ui.js';
import { getUser } from '../state.js';

/**
 * Render the Examinations Catalog into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const user = params.user || getUser();
  const isTeacherOrAdmin = user?.role === 'ADMIN' || user?.role === 'TEACHER';
  const isStudent = user?.role === 'STUDENT';

  renderSkeleton(container, 2);

  let exams = [];
  try {
    exams = await get('/api/exams');
  } catch (err) {
    toast.error('Failed to load examinations catalog: ' + (err.data?.error || err.message));
    exams = [];
  }

  container.textContent = '';

  // ── Header ─────────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  titleGroup.append(
    el('h1', 'page-title', 'Examination Catalog'),
    el('p', 'page-description', 'Curated academic assessment specifications, test structures, and examination sittings.')
  );

  const headerActions = el('div', 'page-actions');
  if (isTeacherOrAdmin) {
    headerActions.appendChild(link('+ Author New Exam', '#/exams/new', 'btn btn-primary'));
  }
  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Main Card ──────────────────────────────────────────────────────────────
  const card = el('div', 'card');

  if (!exams || exams.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No examinations found in catalog'),
      el('p', 'empty-state-body', isTeacherOrAdmin
        ? 'Author your first examination assessment with multiple-choice or true/false questions.'
        : 'There are currently no examinations scheduled for sitting.')
    );
    if (isTeacherOrAdmin) {
      empty.appendChild(link('+ Author New Exam', '#/exams/new', 'btn btn-primary btn-sm'));
    }
    card.appendChild(empty);
  } else {
    const columns = [
      { key: 'id', label: 'Ref', className: 'td-mono', sortable: true, render: (v) => `#${v}` },
      { key: 'title', label: 'Examination Title', className: 'td-strong', sortable: true },
      { key: 'questionCount', label: 'Questions', sortable: true, render: (v) => `${v} ${v === 1 ? 'question' : 'questions'}` },
      { key: 'totalMarks', label: 'Marks', sortable: true, render: (v) => `${v} ${v === 1 ? 'mark' : 'marks'} total` },
      {
        key: 'durationMinutes',
        label: 'Duration',
        sortable: true,
        render: (v) => badge(`${v} MINS`, 'badge-indigo')
      },
      {
        key: 'actions',
        label: 'Actions',
        sortable: false,
        className: 'td-right',
        render: (_, row) => {
          const actionGroup = el('div', 'td-actions');
          actionGroup.style.justifyContent = 'flex-end';

          if (isStudent) {
            actionGroup.appendChild(link('Sit Exam', `#/exams/${row.id}/take`, 'btn btn-primary btn-sm'));
          }

          const inspectBtn = button('Inspect', 'btn btn-secondary btn-sm', () => inspectExam(row.id));
          actionGroup.appendChild(inspectBtn);

          if (isTeacherOrAdmin) {
            const deleteBtn = button('Delete', 'btn btn-danger btn-sm', async () => {
              const ok = await confirm({
                title: 'Delete Examination',
                body: `Are you sure you want to permanently delete "${row.title}" (#${row.id})? All candidate answers and past submissions will also be removed.`,
                confirmText: 'Delete Exam',
                confirmVariant: 'danger',
              });
              if (!ok) return;

              try {
                await del(`/api/exams/${row.id}`);
                toast.success(`Examination "${row.title}" has been deleted.`);
                render(container, params);
              } catch (err) {
                toast.error('Failed to delete examination: ' + (err.data?.error || err.message));
              }
            });
            actionGroup.appendChild(deleteBtn);
          }

          return actionGroup;
        }
      }
    ];

    const tableEl = createDataTable({
      columns,
      data: exams,
      searchKeys: ['title'],
      searchPlaceholder: 'Search by exam title...',
      emptyText: 'No examinations match your search criteria.',
    });
    card.appendChild(tableEl);
  }

  container.appendChild(card);

  // ── Inspection Modal ───────────────────────────────────────────────────────
  const inspectModal = el('div', 'modal-overlay');
  inspectModal.setAttribute('role', 'dialog');
  inspectModal.setAttribute('aria-modal', 'true');
  inspectModal.hidden = true;

  const modalBox = el('div', 'modal');
  modalBox.style.maxWidth = '680px';
  modalBox.style.maxHeight = '85vh';
  modalBox.style.overflowY = 'auto';

  const modalTitle = el('h2', 'modal-title', 'Examination Details');
  const modalMeta = el('p', 'modal-body');
  const modalContent = el('div');
  modalContent.style.display = 'flex';
  modalContent.style.flexDirection = 'column';
  modalContent.style.gap = '1rem';
  modalContent.style.margin = '1.5rem 0';

  const modalActions = el('div', 'modal-actions');
  const closeBtn = button('Close', 'btn btn-secondary', () => {
    inspectModal.hidden = true;
  });
  modalActions.appendChild(closeBtn);

  modalBox.append(modalTitle, modalMeta, modalContent, modalActions);
  inspectModal.appendChild(modalBox);
  container.appendChild(inspectModal);

  async function inspectExam(examId) {
    try {
      const exam = await get(`/api/exams/${examId}`);
      modalTitle.textContent = exam.title;
      modalMeta.textContent = `${exam.questionCount} questions • ${exam.totalMarks} marks total • ${exam.durationMinutes} minutes duration`;
      modalContent.textContent = '';

      if (!exam.questions || exam.questions.length === 0) {
        modalContent.appendChild(el('p', 'td-empty', 'No question items configured for this examination.'));
      } else {
        exam.questions.forEach((q, idx) => {
          const qCard = el('div', 'card');
          qCard.style.padding = '1.25rem';
          qCard.style.marginBottom = '0';
          qCard.style.background = 'var(--bg-canvas)';

          const qHeader = el('div');
          qHeader.style.display = 'flex';
          qHeader.style.justifyContent = 'space-between';
          qHeader.style.marginBottom = '0.5rem';
          qHeader.append(
            badge(q.type, 'badge-indigo'),
            el('span', 'stat-mono', `${q.marks} Mark${q.marks > 1 ? 's' : ''}`)
          );

          const qPrompt = el('p', 'td-strong', `${idx + 1}. ${q.prompt}`);
          qPrompt.style.marginBottom = '0.75rem';

          const choicesList = el('div');
          choicesList.style.display = 'flex';
          choicesList.style.flexDirection = 'column';
          choicesList.style.gap = '0.4rem';

          if (q.choices && q.choices.length > 0) {
            q.choices.forEach((c, cIdx) => {
              const choiceRow = el('div');
              choiceRow.style.padding = '0.5rem 0.75rem';
              choiceRow.style.borderRadius = 'var(--radius-xs)';
              choiceRow.style.border = '1px solid var(--border-subtle)';
              choiceRow.style.background = 'var(--bg-surface)';
              choiceRow.style.display = 'flex';
              choiceRow.style.justifyContent = 'space-between';
              choiceRow.style.alignItems = 'center';

              const letter = String.fromCharCode(65 + cIdx);
              choiceRow.append(el('span', '', `${letter}. ${c}`));

              // Show correct key badge only for admin/teacher inspection (never student)
              if (isTeacherOrAdmin && q.correctOptionIndex === cIdx) {
                choiceRow.style.background = 'var(--status-success-bg)';
                choiceRow.style.borderColor = 'var(--status-success-border)';
                choiceRow.appendChild(badge('CORRECT', 'badge-emerald'));
              }
              choicesList.appendChild(choiceRow);
            });
          }

          qCard.append(qHeader, qPrompt, choicesList);
          modalContent.appendChild(qCard);
        });
      }

      inspectModal.hidden = false;
    } catch (err) {
      toast.error('Could not load examination details: ' + (err.data?.error || err.message));
    }
  }
}
