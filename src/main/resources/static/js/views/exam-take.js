/**
 * @fileoverview ExamPro — Active Examination Sitting View (ES module).
 *
 * Features:
 *  - Calls /api/exams/:id/start to record session and obtain server deadline.
 *  - Server-synced countdown timer with warning/danger thresholds.
 *  - One question per screen with live progress track & question navigation pills.
 *  - Full keyboard navigation (ArrowLeft/ArrowRight, 1-4, A-D, T/F).
 *  - In-memory autosave of candidate answers.
 *  - Confirmation dialog with unanswered question count before submission.
 *  - Automatic submission when server deadline expires.
 *  - HARD RULE: Never reveals correct answers before submission.
 *
 * @module views/exam-take
 */

import { get, post } from '../api.js';
import { toast, confirm, el, badge, button, link, renderSkeleton } from '../ui.js';
import { createTimer, createDurationTimer } from '../timer.js';
import { getUser } from '../state.js';

/**
 * Render the Examination Sitting interface into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const examId = params.params?.[0] || params[0];
  if (!examId) {
    location.hash = '#/exams';
    return;
  }

  const user = params.user || getUser();
  const isStudent = user?.role === 'STUDENT';
  if (!isStudent) {
    renderErrorState(container, new Error('Only students can sit an examination.'));
    return;
  }

  renderSkeleton(container, 1);

  let startData, examQuestions;
  try {
    startData = await post(`/api/exams/${examId}/start`, {});
  } catch (err) {
    renderErrorState(container, err);
    return;
  }

  // Ensure questions list
  examQuestions = startData.questions;
  if (!examQuestions || examQuestions.length === 0) {
    try {
      const fullExam = await get(`/api/exams/${examId}`);
      examQuestions = fullExam.questions || [];
    } catch {
      examQuestions = [];
    }
  }

  if (examQuestions.length === 0) {
    renderErrorState(container, new Error('This examination has no questions configured.'));
    return;
  }

  container.textContent = '';

  // ── Session State ──────────────────────────────────────────────────────────
  const totalQuestions = examQuestions.length;
  let currentIndex = 0;
  const answers = {}; // questionId -> chosenOptionIndex
  let isSubmitting = false;
  let timerHandle = null;

  // ── Session Header Bar ─────────────────────────────────────────────────────
  const sessionHeader = el('div', 'card');
  sessionHeader.style.marginBottom = '1rem';
  sessionHeader.style.padding = '1.25rem 1.75rem';
  sessionHeader.style.position = 'sticky';
  sessionHeader.style.top = '65px';
  sessionHeader.style.zIndex = '30';
  sessionHeader.style.boxShadow = 'var(--shadow-md)';

  const headerFlex = el('div');
  headerFlex.style.display = 'flex';
  headerFlex.style.justifyContent = 'space-between';
  headerFlex.style.alignItems = 'center';
  headerFlex.style.flexWrap = 'wrap';
  headerFlex.style.gap = '1rem';

  const titleArea = el('div');
  const sessionBadge = badge('ACTIVE ASSESSMENT SITTING', 'badge-cyan');
  const examTitleEl = el('h1', '', startData.examTitle || 'Academic Assessment');
  examTitleEl.style.fontFamily = 'var(--font-serif)';
  examTitleEl.style.fontSize = '1.35rem';
  examTitleEl.style.margin = '0.2rem 0 0.1rem 0';
  examTitleEl.style.color = 'var(--text-primary)';

  const metaSub = el('div');
  metaSub.style.display = 'flex';
  metaSub.style.gap = '1rem';
  metaSub.style.fontSize = '0.82rem';
  metaSub.style.color = 'var(--text-muted)';
  const answeredCounter = el('span', '', `Answered: 0 / ${totalQuestions}`);
  metaSub.append(
    el('span', '', `${totalQuestions} ${totalQuestions === 1 ? 'Question' : 'Questions'} Total`),
    el('span', '', `•`),
    answeredCounter
  );
  titleArea.append(sessionBadge, examTitleEl, metaSub);

  // Timer Area
  const timerArea = el('div');
  timerArea.style.display = 'flex';
  timerArea.style.alignItems = 'center';
  timerArea.style.gap = '1.25rem';

  const timerBox = el('div');
  timerBox.style.display = 'flex';
  timerBox.style.flexDirection = 'column';
  timerBox.style.alignItems = 'flex-end';
  const timerLbl = el('span', 'timer-label', 'Time Remaining');
  const timerDisplay = el('span', 'stat-mono', '--:--');
  timerDisplay.style.fontSize = '1.5rem';
  timerDisplay.style.fontWeight = '700';
  timerDisplay.style.color = 'var(--text-primary)';
  timerBox.append(timerLbl, timerDisplay);

  const topSubmitBtn = button('Finish & Submit', 'btn btn-primary', () => confirmAndSubmit());
  timerArea.append(timerBox, topSubmitBtn);

  headerFlex.append(titleArea, timerArea);
  sessionHeader.appendChild(headerFlex);
  container.appendChild(sessionHeader);

  // ── Progress Bar Track ─────────────────────────────────────────────────────
  const progressTrack = el('div', 'exam-progress-track');
  const progressFill = el('div', 'exam-progress-fill');
  progressTrack.appendChild(progressFill);
  container.appendChild(progressTrack);

  // ── Question Jump Pills Grid ───────────────────────────────────────────────
  const jumpBar = el('div');
  jumpBar.style.display = 'flex';
  jumpBar.style.gap = '0.4rem';
  jumpBar.style.flexWrap = 'wrap';
  jumpBar.style.marginBottom = '1.5rem';
  container.appendChild(jumpBar);

  const jumpButtons = [];
  for (let i = 0; i < totalQuestions; i++) {
    const pill = document.createElement('button');
    pill.type = 'button';
    pill.className = 'btn btn-secondary btn-sm';
    pill.textContent = String(i + 1);
    pill.style.minWidth = '36px';
    pill.style.padding = '0.2rem 0.5rem';
    pill.style.fontFamily = 'var(--font-mono)';
    pill.style.fontSize = '0.8rem';

    pill.addEventListener('click', () => {
      currentIndex = i;
      updateScreen();
    });
    jumpButtons.push(pill);
    jumpBar.appendChild(pill);
  }

  function updateProgressAndJumpButtons() {
    const answeredCount = Object.keys(answers).length;
    answeredCounter.textContent = `Answered: ${answeredCount} / ${totalQuestions}`;
    progressFill.style.width = `${Math.round((answeredCount / totalQuestions) * 100)}%`;

    jumpButtons.forEach((btn, idx) => {
      const qId = examQuestions[idx]?.id;
      const isAnswered = answers[qId] !== undefined;
      const isActive = idx === currentIndex;

      btn.classList.toggle('btn-primary', isActive);
      btn.classList.toggle('btn-secondary', !isActive);

      if (isActive) {
        btn.style.borderColor = 'var(--border-strong)';
        btn.style.boxShadow = 'var(--shadow-sm)';
      } else if (isAnswered) {
        btn.style.background = 'var(--status-success-bg)';
        btn.style.borderColor = 'var(--status-success-border)';
        btn.style.color = 'var(--status-success)';
      } else {
        btn.style.background = 'var(--bg-surface)';
        btn.style.borderColor = 'var(--border-subtle)';
        btn.style.color = 'var(--text-secondary)';
      }
    });
  }

  // ── Active Question Card Container ─────────────────────────────────────────
  const questionCardContainer = el('div');
  container.appendChild(questionCardContainer);

  // ── Navigation Action Bar (Bottom) ─────────────────────────────────────────
  const bottomBar = el('div', 'card');
  bottomBar.style.display = 'flex';
  bottomBar.style.justifyContent = 'space-between';
  bottomBar.style.alignItems = 'center';
  bottomBar.style.padding = '1.25rem 2rem';
  bottomBar.style.background = 'var(--bg-subtle)';
  bottomBar.style.marginTop = '1.5rem';

  const prevBtn = button('← Previous [ArrowLeft]', 'btn btn-secondary', () => {
    if (currentIndex > 0) {
      currentIndex--;
      updateScreen();
    }
  });

  const keyboardHint = el('span', 'stat-mono', 'Shortcuts: [←/→] Navigate • [1-4 / A-D] Select');
  keyboardHint.style.fontSize = '0.78rem';
  keyboardHint.style.color = 'var(--text-muted)';

  const nextBtn = button('Next → [ArrowRight]', 'btn btn-primary', () => {
    if (currentIndex < totalQuestions - 1) {
      currentIndex++;
      updateScreen();
    } else {
      confirmAndSubmit();
    }
  });

  bottomBar.append(prevBtn, keyboardHint, nextBtn);
  container.appendChild(bottomBar);

  // ── Screen Renderer (One Question per Screen) ──────────────────────────────
  function updateScreen() {
    const q = examQuestions[currentIndex];
    questionCardContainer.textContent = '';

    // Update progress bar
    updateProgressAndJumpButtons();

    // Update Bottom Buttons
    prevBtn.disabled = currentIndex === 0;
    if (currentIndex === totalQuestions - 1) {
      nextBtn.textContent = 'Review & Submit';
    } else {
      nextBtn.textContent = 'Next → [ArrowRight]';
    }

    // Render Question Block
    const qCard = el('div', 'question-block');

    const qHeader = el('div', 'question-block-header');
    const qMeta = el('div', 'question-block-meta');
    qMeta.append(
      badge(`QUESTION ${currentIndex + 1} OF ${totalQuestions}`, 'badge-indigo'),
      badge(q.type === 'MCQ' ? 'MULTIPLE CHOICE' : 'TRUE / FALSE', q.type === 'MCQ' ? 'badge-cyan' : 'badge-amber')
    );
    const marksSpan = el('span', 'stat-mono', `${q.marks} ${q.marks === 1 ? 'mark' : 'marks'}`);
    marksSpan.style.color = 'var(--text-muted)';
    marksSpan.style.fontSize = '0.85rem';
    qHeader.append(qMeta, marksSpan);
    qCard.appendChild(qHeader);

    // Prompt
    const promptEl = el('p', 'question-prompt', q.prompt);
    qCard.appendChild(promptEl);

    // Choices Group
    const choicesGroup = el('div', 'options-choice-group');
    const choices = q.choices || [];

    choices.forEach((choiceText, cIdx) => {
      const labelCard = el('label', 'choice-label-card');
      const isSelected = answers[q.id] === cIdx;
      if (isSelected) {
        labelCard.classList.add('selected');
      }

      const radio = document.createElement('input');
      radio.type = 'radio';
      radio.name = `exam_q_${q.id}`;
      radio.value = String(cIdx);
      radio.checked = isSelected;
      radio.style.accentColor = 'var(--accent)';

      const letterBox = el('span', 'choice-letter', String.fromCharCode(65 + cIdx));
      const textSpan = el('span', 'choice-text', choiceText);

      labelCard.append(radio, letterBox, textSpan);

      radio.addEventListener('change', () => {
        answers[q.id] = cIdx;
        choicesGroup.querySelectorAll('.choice-label-card').forEach((card) => card.classList.remove('selected'));
        labelCard.classList.add('selected');
        updateProgressAndJumpButtons();
      });

      choicesGroup.appendChild(labelCard);
    });

    qCard.appendChild(choicesGroup);
    questionCardContainer.appendChild(qCard);
  }

  // ── Keyboard Navigation Handler ────────────────────────────────────────────
  function handleKeyDown(e) {
    // Ignore keystrokes in input elements
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    if (e.key === 'ArrowLeft') {
      if (currentIndex > 0) {
        currentIndex--;
        updateScreen();
      }
    } else if (e.key === 'ArrowRight') {
      if (currentIndex < totalQuestions - 1) {
        currentIndex++;
        updateScreen();
      }
    } else {
      const currentQ = examQuestions[currentIndex];
      if (!currentQ) return;
      const choices = currentQ.choices || [];

      let selectedOpt = -1;
      const key = e.key.toUpperCase();

      if (key === '1' || key === 'A') selectedOpt = 0;
      else if (key === '2' || key === 'B') selectedOpt = 1;
      else if (key === '3' || key === 'C') selectedOpt = 2;
      else if (key === '4' || key === 'D') selectedOpt = 3;
      else if (currentQ.type === 'TF') {
        if (key === 'T') selectedOpt = 0;
        if (key === 'F') selectedOpt = 1;
      }

      if (selectedOpt >= 0 && selectedOpt < choices.length) {
        const radio = questionCardContainer.querySelectorAll('input[type="radio"]')[selectedOpt];
        if (radio) {
          radio.checked = true;
          radio.dispatchEvent(new Event('change', { bubbles: true }));
        }
      }
    }
  }

  window.addEventListener('keydown', handleKeyDown);

  // ── Server-Synced Timer Initialisation ─────────────────────────────────────
  let deadlineEpochMs;
  if (startData.deadlineAt) {
    deadlineEpochMs = new Date(startData.deadlineAt).getTime();
  } else {
    deadlineEpochMs = Date.now() + (startData.durationMinutes || 30) * 60 * 1000;
  }

  timerHandle = createTimer({
    element: timerDisplay,
    deadlineMs: deadlineEpochMs,
    serverNowMs: startData.sessionStartedAt ? new Date(startData.sessionStartedAt).getTime() : Date.now(),
    warningThresholdSecs: 300,
    dangerThresholdSecs: 120,
    onExpired: () => {
      toast.info('Session deadline has elapsed! Submitting your answers automatically.');
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      submitSession(true);
    }
  });

  // ── Submission Logic ───────────────────────────────────────────────────────
  async function confirmAndSubmit() {
    if (isSubmitting) return;

    const answeredCount = Object.keys(answers).length;
    const unansweredCount = totalQuestions - answeredCount;

    let message = `You have answered ${answeredCount} out of ${totalQuestions} ${totalQuestions === 1 ? 'question' : 'questions'}.`;
    if (unansweredCount > 0) {
      message += ` Note: ${unansweredCount} question${unansweredCount > 1 ? 's remain' : ' remains'} unanswered and will receive zero marks.`;
    }
    message += ' Once submitted, scoring will be calculated immediately.';

    const ok = await confirm({
      title: 'Submit Examination Assessment',
      body: message,
      confirmText: 'Submit Assessment',
      confirmVariant: 'primary',
    });

    if (!ok) return;
    submitSession(false);
  }

  async function submitSession(isAuto = false) {
    if (isSubmitting) return;
    isSubmitting = true;

    // Stop timer and keyboard listener
    timerHandle?.stop();
    window.removeEventListener('keydown', handleKeyDown);

    topSubmitBtn.disabled = true;
    nextBtn.disabled = true;
    prevBtn.disabled = true;
    topSubmitBtn.textContent = 'Submitting...';

    const payload = {
      studentId: startData.studentId || user?.studentId || undefined,
      answers,
    };

    try {
      const res = await post(`/api/exams/${examId}/submit`, payload);
      toast.success('Examination submitted successfully!');

      if (res && res.attemptId) {
        location.hash = `#/scorecard/${res.attemptId}`;
      } else {
        location.hash = `#/results`;
      }
    } catch (err) {
      isSubmitting = false;
      topSubmitBtn.disabled = false;
      nextBtn.disabled = false;
      prevBtn.disabled = false;
      topSubmitBtn.textContent = 'Finish & Submit';
      window.addEventListener('keydown', handleKeyDown);

      const errMsg = err.data?.error || err.message || 'Submission failed.';
      toast.error(errMsg);
    }
  }

  // Cleanup on route change or abort
  if (params.signal) {
    params.signal.addEventListener('abort', () => {
      timerHandle?.stop();
      window.removeEventListener('keydown', handleKeyDown);
    });
  }

  // Initial screen display
  updateScreen();
}

/**
 * Render error state when exam or session fails to start.
 * @param {HTMLElement} container
 * @param {Error} err
 */
function renderErrorState(container, err) {
  container.textContent = '';
  const card = el('div', 'card');
  card.style.marginTop = '2rem';

  const alert = el('div', 'alert alert-danger');
  alert.append(
    el('span', 'alert-label', 'Examination Session Unavailable: '),
    document.createTextNode(err.data?.error || err.message || 'Could not initialize session.')
  );

  const actions = el('div');
  actions.style.marginTop = '1.5rem';
  actions.appendChild(link('Return to Examinations Catalog', '#/exams', 'btn btn-secondary'));

  card.append(alert, actions);
  container.appendChild(card);
}
