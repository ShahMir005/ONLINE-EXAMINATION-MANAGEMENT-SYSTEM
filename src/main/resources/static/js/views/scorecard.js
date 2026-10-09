/**
 * @fileoverview ExamPro — Scorecard Review View (ES module).
 *
 * Features:
 *  - Radial SVG score ring with live stroke animation & outcome badge.
 *  - Comprehensive institutional metadata grid (candidate, ref, date, score).
 *  - Question-by-question review (authoritative correct answer revealed ONLY after submission).
 *  - Print action utilizing @media print styles from print.css.
 *  - Return links to dashboard and performance registry.
 *
 * @module views/scorecard
 */

import { get } from '../api.js';
import { toast, el, badge, button, link, renderSkeleton } from '../ui.js';

/**
 * Render the Scorecard View into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const attemptId = params.params?.[0] || params[0];
  if (!attemptId) {
    location.hash = '#/results';
    return;
  }

  renderSkeleton(container, 2);

  let detail;
  try {
    detail = await get(`/api/results/${attemptId}`);
  } catch (err) {
    // Also try /api/attempts/:id
    try {
      detail = await get(`/api/attempts/${attemptId}`);
    } catch {
      renderNotFoundState(container, err);
      return;
    }
  }

  container.textContent = '';

  const totalMarks = detail.totalMarks || 0;
  const score = detail.score || 0;
  const percentage = detail.percentage !== undefined ? detail.percentage : (totalMarks > 0 ? Math.round((score * 100) / totalMarks) : 0);
  const passed = detail.passed !== undefined ? detail.passed : (percentage >= 50);

  // ── Breadcrumb & Top Bar ───────────────────────────────────────────────────
  const topNav = el('div');
  topNav.style.display = 'flex';
  topNav.style.justifyContent = 'space-between';
  topNav.style.alignItems = 'center';
  topNav.style.marginBottom = '1.5rem';
  topNav.style.flexWrap = 'wrap';
  topNav.style.gap = '1rem';

  const breadcrumbs = el('div');
  breadcrumbs.style.display = 'flex';
  breadcrumbs.style.alignItems = 'center';
  breadcrumbs.style.gap = '0.5rem';
  breadcrumbs.style.fontSize = '0.85rem';
  breadcrumbs.append(
    link('Dashboard', '#/', 'btn btn-secondary btn-sm'),
    el('span', '', '/'),
    link('All Results', '#/results', 'btn btn-secondary btn-sm'),
    el('span', '', `/ Scorecard #${attemptId}`)
  );

  const printBtn = button('🖨 Print Scorecard', 'btn btn-secondary btn-sm', () => window.print());
  topNav.append(breadcrumbs, printBtn);
  container.appendChild(topNav);

  // ── Score Hero Card (Archival & 21st.dev inspired) ──────────────────────────
  const heroCard = el('div', `score-hero-card ${passed ? 'pass-card' : 'fail-card'}`);
  heroCard.style.padding = '2.5rem 2rem';
  heroCard.style.textAlign = 'center';

  // SVG Radial Score Ring
  const radius = 64;
  const circumference = 2 * Math.PI * radius; // ~402.12
  const strokeDashoffset = circumference - (circumference * Math.min(100, Math.max(0, percentage))) / 100;
  const strokeColor = passed ? 'var(--status-success)' : 'var(--status-danger)';

  const ringWrapper = el('div');
  ringWrapper.style.position = 'relative';
  ringWrapper.style.width = '160px';
  ringWrapper.style.height = '160px';
  ringWrapper.style.margin = '0 auto 1.5rem auto';

  const svgNS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(svgNS, 'svg');
  svg.setAttribute('width', '160');
  svg.setAttribute('height', '160');
  svg.setAttribute('viewBox', '0 0 160 160');

  // Background track circle
  const bgCircle = document.createElementNS(svgNS, 'circle');
  bgCircle.setAttribute('cx', '80');
  bgCircle.setAttribute('cy', '80');
  bgCircle.setAttribute('r', String(radius));
  bgCircle.setAttribute('fill', 'none');
  bgCircle.setAttribute('stroke', 'var(--border-subtle)');
  bgCircle.setAttribute('stroke-width', '10');
  svg.appendChild(bgCircle);

  // Foreground progress circle
  const fgCircle = document.createElementNS(svgNS, 'circle');
  fgCircle.setAttribute('cx', '80');
  fgCircle.setAttribute('cy', '80');
  fgCircle.setAttribute('r', String(radius));
  fgCircle.setAttribute('fill', 'none');
  fgCircle.setAttribute('stroke', strokeColor);
  fgCircle.setAttribute('stroke-width', '10');
  fgCircle.setAttribute('stroke-dasharray', String(circumference));
  fgCircle.setAttribute('stroke-dashoffset', String(strokeDashoffset));
  fgCircle.setAttribute('stroke-linecap', 'round');
  fgCircle.setAttribute('transform', 'rotate(-90 80 80)');
  svg.appendChild(fgCircle);

  ringWrapper.appendChild(svg);

  // Inside Ring Text
  const ringInner = el('div');
  ringInner.style.position = 'absolute';
  ringInner.style.inset = '0';
  ringInner.style.display = 'flex';
  ringInner.style.flexDirection = 'column';
  ringInner.style.alignItems = 'center';
  ringInner.style.justifyContent = 'center';

  const pctText = el('span', 'score-number', `${percentage}%`);
  const scoreSub = el('span', 'score-max', `${score} / ${totalMarks} Marks`);
  ringInner.append(pctText, scoreSub);
  ringWrapper.appendChild(ringInner);

  heroCard.appendChild(ringWrapper);

  // Status Badge and Title
  const statusBadge = badge(passed ? 'OFFICIAL RESULT: PASSED' : 'OFFICIAL RESULT: RETAKE REQUIRED', passed ? 'badge-emerald' : 'badge-rose');
  statusBadge.style.fontSize = '0.85rem';
  statusBadge.style.padding = '0.35rem 0.8rem';
  heroCard.appendChild(statusBadge);

  const heroTitle = el('h1', 'page-title', detail.examTitle || 'Examination Scorecard');
  heroTitle.style.marginTop = '1rem';
  heroCard.appendChild(heroTitle);

  // Institutional Meta Grid
  const metaGrid = el('div', 'scorecard-meta-grid');

  const metaItems = [
    { label: 'Candidate Name', value: detail.studentName || 'Examinee' },
    { label: 'Registration Ref', value: detail.studentRegistration || detail.registrationNumber || 'N/A' },
    { label: 'Assessment Ref', value: `#${attemptId}` },
    { label: 'Sitting Timestamp', value: detail.submittedAtFormatted || 'Recorded Session' },
    { label: 'Passing Benchmark', value: '50% Minimum' },
    { label: 'Performance Tier', value: percentage >= 80 ? 'Distinction' : (percentage >= 60 ? 'Merit' : (passed ? 'Pass' : 'Unsuccessful')) },
  ];

  metaItems.forEach(item => {
    const box = el('div', 'meta-box');
    box.append(
      el('span', 'meta-box-label', item.label),
      el('span', 'meta-box-value', item.value)
    );
    metaGrid.appendChild(box);
  });

  heroCard.appendChild(metaGrid);
  container.appendChild(heroCard);

  // ── Per-Question Review Section ────────────────────────────────────────────
  const reviewSection = el('div');
  reviewSection.style.marginTop = '2.5rem';

  const sectionHead = el('div');
  sectionHead.style.marginBottom = '1.5rem';
  const secTitle = el('h2', 'card-title', 'Detailed Question Breakdown');
  const secDesc = el('p', 'page-description', 'Review individual responses, awarded marks, and verified answer keys.');
  sectionHead.append(secTitle, secDesc);
  reviewSection.appendChild(sectionHead);

  const qResults = detail.questionResults || [];

  if (qResults.length === 0) {
    const emptyBox = el('div', 'card');
    emptyBox.appendChild(el('p', 'td-empty', 'No question-level response breakdown was recorded for this sitting.'));
    reviewSection.appendChild(emptyBox);
  } else {
    qResults.forEach((q, idx) => {
      const isCorrect = Boolean(q.correct);
      const isUnanswered = q.selectedOption === null || q.selectedOption === undefined || q.selectedOption < 0;

      const qCard = el('div', `result-question-card ${isCorrect ? 'correct' : 'incorrect'}`);

      // Question card header
      const qHead = el('div', 'result-question-header');
      const qMetaLeft = el('div');
      qMetaLeft.style.display = 'flex';
      qMetaLeft.style.alignItems = 'center';
      qMetaLeft.style.gap = '0.75rem';

      qMetaLeft.append(
        badge(`QUESTION ${idx + 1}`, 'badge-indigo'),
        badge(q.type === 'MCQ' ? 'MULTIPLE CHOICE' : 'TRUE / FALSE', 'badge-indigo')
      );

      const qMetaRight = el('div');
      qMetaRight.style.display = 'flex';
      qMetaRight.style.alignItems = 'center';
      qMetaRight.style.gap = '0.75rem';

      const marksAwardedText = `${q.awardedMarks ?? (isCorrect ? q.marks : 0)} / ${q.marks || q.maxMarks || 1} Marks`;
      qMetaRight.append(
        el('span', 'stat-mono', marksAwardedText),
        badge(isCorrect ? 'CORRECT' : (isUnanswered ? 'UNANSWERED' : 'INCORRECT'), isCorrect ? 'badge-emerald' : 'badge-rose')
      );

      qHead.append(qMetaLeft, qMetaRight);
      qCard.appendChild(qHead);

      // Question prompt
      const promptEl = el('p', 'question-prompt', q.prompt);
      promptEl.style.fontSize = '1.05rem';
      promptEl.style.margin = '0.75rem 0 1rem 0';
      qCard.appendChild(promptEl);

      // Answers Comparison Grid
      const compGrid = el('div', 'answers-comparison-grid');

      // Candidate Selection Pill
      const userPill = el('div', `answer-pill ${isCorrect ? 'chosen-correct' : 'chosen-wrong'}`);
      const userLabel = el('span', 'field-label', 'Your Answer:');
      const userText = el('p', 'td-strong', q.selectedText || (isUnanswered ? 'None (Unanswered)' : `Option ${q.selectedOption}`));
      userPill.append(userLabel, userText);
      compGrid.appendChild(userPill);

      // Correct Key Pill (Shown only now, after submission!)
      const correctPill = el('div', 'answer-pill');
      correctPill.style.background = 'var(--bg-subtle)';
      correctPill.style.borderColor = 'var(--status-success-border)';
      const correctLabel = el('span', 'field-label', 'Authoritative Correct Key:');
      correctLabel.style.color = 'var(--status-success)';

      let correctDisplay = q.correctText || (q.correctOption !== undefined ? `Option ${q.correctOption}` : '');
      if (!correctDisplay && q.choices && q.correctOptionIndex !== undefined) {
        correctDisplay = q.choices[q.correctOptionIndex];
      }
      if (!correctDisplay && isCorrect) {
        correctDisplay = q.selectedText;
      }
      if (!correctDisplay) {
        correctDisplay = 'Verified by Examiner';
      }

      const correctText = el('p', 'td-strong', correctDisplay);
      correctText.style.color = 'var(--status-success)';
      correctPill.append(correctLabel, correctText);
      compGrid.appendChild(correctPill);

      qCard.appendChild(compGrid);
      reviewSection.appendChild(qCard);
    });
  }

  container.appendChild(reviewSection);

  // ── Bottom Action Links ────────────────────────────────────────────────────
  const footerCard = el('div', 'card');
  footerCard.style.display = 'flex';
  footerCard.style.justifyContent = 'space-between';
  footerCard.style.alignItems = 'center';
  footerCard.style.marginTop = '2rem';
  footerCard.style.flexWrap = 'wrap';
  footerCard.style.gap = '1rem';

  const footerText = el('p', 'form-hint', 'Verified and recorded in the institutional assessment ledger.');
  const footerBtns = el('div');
  footerBtns.style.display = 'flex';
  footerBtns.style.gap = '0.75rem';

  footerBtns.append(
    link('Return to Dashboard', '#/', 'btn btn-secondary'),
    link('View All Results', '#/results', 'btn btn-primary')
  );

  footerCard.append(footerText, footerBtns);
  container.appendChild(footerCard);
}

/**
 * Render not found state when scorecard cannot be retrieved.
 * @param {HTMLElement} container
 * @param {Error} err
 */
function renderNotFoundState(container, err) {
  container.textContent = '';
  const card = el('div', 'card');
  card.style.marginTop = '2rem';

  const alert = el('div', 'alert alert-danger');
  alert.append(
    el('span', 'alert-label', 'Scorecard Not Found: '),
    document.createTextNode(err.data?.error || err.message || 'Unable to load examination scorecard.')
  );

  const actions = el('div');
  actions.style.marginTop = '1.5rem';
  actions.appendChild(link('Return to Results Registry', '#/results', 'btn btn-secondary'));

  card.append(alert, actions);
  container.appendChild(card);
}
