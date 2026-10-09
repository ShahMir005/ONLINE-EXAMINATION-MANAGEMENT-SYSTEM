/**
 * @fileoverview ExamPro — Exam Creation View (ES module).
 * Interactive form builder with inline validation, dynamic question specs,
 * multiple choice and true/false question types, and accessible controls.
 */

import { post } from '../api.js';
import { toast, el, badge, button, link } from '../ui.js';

/**
 * Render the Exam Creation builder into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  container.textContent = '';

  let questions = [
    {
      type: 'MCQ',
      prompt: '',
      marks: 5,
      choices: ['', '', '', ''],
      correctOptionIndex: 0,
      tfCorrect: true,
    }
  ];

  // ── Header ─────────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  titleGroup.append(
    el('h1', 'page-title', 'Author New Examination'),
    el('p', 'page-description', 'Configure assessment parameters, author questions, specify answer keys, and set mark weights.')
  );

  const headerActions = el('div', 'page-actions');
  headerActions.appendChild(link('Cancel', '#/exams', 'btn btn-secondary'));
  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Form Wrapper ───────────────────────────────────────────────────────────
  const form = document.createElement('form');
  form.noValidate = true;

  // General Parameters Card
  const paramCard = el('div', 'card');
  paramCard.appendChild(el('h2', 'card-title', 'Examination Parameters'));

  const grid = el('div');
  grid.style.display = 'grid';
  grid.style.gridTemplateColumns = 'repeat(auto-fit, minmax(280px, 1fr))';
  grid.style.gap = '1.5rem';
  grid.style.marginTop = '1.25rem';

  // Title field
  const titleGroupEl = el('div', 'form-group');
  const titleLabel = el('label', 'form-label', 'Examination Title *');
  titleLabel.htmlFor = 'exam-title';
  const titleInput = document.createElement('input');
  titleInput.id = 'exam-title';
  titleInput.className = 'form-control';
  titleInput.placeholder = 'e.g. Distributed Computing & Cloud Infrastructure';
  titleInput.required = true;
  const titleError = el('span', 'form-error-msg', 'Please enter a title for the examination.');
  titleError.hidden = true;
  titleGroupEl.append(titleLabel, titleInput, titleError);

  // Duration field
  const durationGroupEl = el('div', 'form-group');
  const durationLabel = el('label', 'form-label', 'Duration (Minutes) *');
  durationLabel.htmlFor = 'exam-duration';
  const durationInput = document.createElement('input');
  durationInput.id = 'exam-duration';
  durationInput.type = 'number';
  durationInput.className = 'form-control';
  durationInput.value = '30';
  durationInput.min = '1';
  durationInput.max = '300';
  durationInput.required = true;
  const durationError = el('span', 'form-error-msg', 'Please enter a whole-number duration between 1 and 300 minutes.');
  durationError.hidden = true;
  durationGroupEl.append(durationLabel, durationInput, durationError);

  grid.append(titleGroupEl, durationGroupEl);
  paramCard.appendChild(grid);
  form.appendChild(paramCard);

  // ── Questions Section ──────────────────────────────────────────────────────
  const qSectionHeader = el('div');
  qSectionHeader.style.display = 'flex';
  qSectionHeader.style.justifyContent = 'space-between';
  qSectionHeader.style.alignItems = 'center';
  qSectionHeader.style.margin = '2rem 0 1rem 0';
  qSectionHeader.style.flexWrap = 'wrap';
  qSectionHeader.style.gap = '1rem';

  const qHeading = el('div');
  const qTitle = el('h2', 'card-title', 'Questions Specification');
  const qSubtitle = el('p', 'card-subtitle', 'Define assessment questions, choices, and authoritative answer keys.');
  qHeading.append(qTitle, qSubtitle);

  const qActions = el('div');
  qActions.style.display = 'flex';
  qActions.style.gap = '0.5rem';

  const addMcqBtn = button('+ Add Multiple Choice', 'btn btn-secondary btn-sm', () => {
    questions.push({
      type: 'MCQ',
      prompt: '',
      marks: 5,
      choices: ['', '', '', ''],
      correctOptionIndex: 0,
      tfCorrect: true,
    });
    renderQuestionsList();
  });

  const addTfBtn = button('+ Add True / False', 'btn btn-secondary btn-sm', () => {
    questions.push({
      type: 'TF',
      prompt: '',
      marks: 2,
      choices: ['True', 'False'],
      correctOptionIndex: 0,
      tfCorrect: true,
    });
    renderQuestionsList();
  });

  qActions.append(addMcqBtn, addTfBtn);
  qSectionHeader.append(qHeading, qActions);
  form.appendChild(qSectionHeader);

  const questionsContainer = el('div');
  questionsContainer.style.display = 'flex';
  questionsContainer.style.flexDirection = 'column';
  questionsContainer.style.gap = '1.5rem';
  questionsContainer.style.marginBottom = '2.5rem';
  form.appendChild(questionsContainer);

  function renderQuestionsList() {
    questionsContainer.textContent = '';

    questions.forEach((q, qIndex) => {
      const qCard = el('div', 'card');
      qCard.style.padding = '1.75rem';
      qCard.style.marginBottom = '0';

      const cardHead = el('div');
      cardHead.style.display = 'flex';
      cardHead.style.justifyContent = 'space-between';
      cardHead.style.alignItems = 'center';
      cardHead.style.marginBottom = '1.25rem';

      const typeBadge = badge(`Q${qIndex + 1} • ${q.type}`, q.type === 'MCQ' ? 'badge-indigo' : 'badge-amber');

      const removeBtn = button('Remove Question', 'btn btn-secondary btn-sm', () => {
        if (questions.length <= 1) {
          toast.error('An examination must contain at least one question.');
          return;
        }
        questions.splice(qIndex, 1);
        renderQuestionsList();
      });

      cardHead.append(typeBadge, removeBtn);
      qCard.appendChild(cardHead);

      // Prompt input
      const pGroup = el('div', 'form-group');
      const pLabel = el('label', 'form-label', 'Question Statement *');
      const pInput = document.createElement('textarea');
      pInput.className = 'form-control';
      pInput.rows = 2;
      pInput.placeholder = 'Enter the question prompt or problem statement...';
      pInput.value = q.prompt;
      pInput.addEventListener('input', (e) => {
        q.prompt = e.target.value;
      });

      const pError = el('span', 'form-error-msg', 'Question statement cannot be blank.');
      pError.hidden = true;
      pGroup.append(pLabel, pInput, pError);
      qCard.appendChild(pGroup);

      // Marks input
      const marksRow = el('div', 'form-group');
      marksRow.style.maxWidth = '200px';
      const mLabel = el('label', 'form-label', 'Marks Weighting *');
      const mInput = document.createElement('input');
      mInput.type = 'number';
      mInput.className = 'form-control';
      mInput.min = '1';
      mInput.value = String(q.marks);
      mInput.addEventListener('input', (e) => {
        q.marks = parseInt(e.target.value, 10) || 1;
      });
      marksRow.append(mLabel, mInput);
      qCard.appendChild(marksRow);

      // Choices Builder
      if (q.type === 'MCQ') {
        const choicesSection = el('div');
        choicesSection.style.marginTop = '1rem';
        choicesSection.appendChild(el('label', 'form-label', 'Choices & Correct Answer Key (select radio for correct key):'));

        const choiceList = el('div');
        choiceList.style.display = 'flex';
        choiceList.style.flexDirection = 'column';
        choiceList.style.gap = '0.6rem';

        q.choices.forEach((cText, cIdx) => {
          const choiceRow = el('div');
          choiceRow.style.display = 'flex';
          choiceRow.style.alignItems = 'center';
          choiceRow.style.gap = '0.75rem';

          const radio = document.createElement('input');
          radio.type = 'radio';
          radio.name = `correct_q_${qIndex}`;
          radio.checked = q.correctOptionIndex === cIdx;
          radio.style.accentColor = 'var(--accent)';
          radio.addEventListener('change', () => {
            q.correctOptionIndex = cIdx;
          });

          const letter = el('span', 'stat-mono', `${String.fromCharCode(65 + cIdx)}.`);
          letter.style.width = '20px';

          const cInput = document.createElement('input');
          cInput.type = 'text';
          cInput.className = 'form-control';
          cInput.placeholder = `Option ${String.fromCharCode(65 + cIdx)} description...`;
          cInput.value = cText;
          cInput.style.flex = '1';
          cInput.addEventListener('input', (e) => {
            q.choices[cIdx] = e.target.value;
          });

          choiceRow.append(radio, letter, cInput);
          choiceList.appendChild(choiceRow);
        });

        choicesSection.appendChild(choiceList);
        qCard.appendChild(choicesSection);
      } else {
        // True / False options
        const tfSection = el('div');
        tfSection.style.marginTop = '1rem';
        tfSection.appendChild(el('label', 'form-label', 'Authoritative Correct Answer:'));

        const tfRow = el('div');
        tfRow.style.display = 'flex';
        tfRow.style.gap = '1.5rem';

        const trueLabel = el('label', 'form-label');
        trueLabel.style.display = 'flex';
        trueLabel.style.alignItems = 'center';
        trueLabel.style.gap = '0.5rem';
        trueLabel.style.cursor = 'pointer';
        const trueRadio = document.createElement('input');
        trueRadio.type = 'radio';
        trueRadio.name = `tf_q_${qIndex}`;
        trueRadio.checked = q.correctOptionIndex === 0;
        trueRadio.style.accentColor = 'var(--accent)';
        trueRadio.addEventListener('change', () => {
          q.correctOptionIndex = 0;
          q.tfCorrect = true;
        });
        trueLabel.append(trueRadio, document.createTextNode('True'));

        const falseLabel = el('label', 'form-label');
        falseLabel.style.display = 'flex';
        falseLabel.style.alignItems = 'center';
        falseLabel.style.gap = '0.5rem';
        falseLabel.style.cursor = 'pointer';
        const falseRadio = document.createElement('input');
        falseRadio.type = 'radio';
        falseRadio.name = `tf_q_${qIndex}`;
        falseRadio.checked = q.correctOptionIndex === 1;
        falseRadio.style.accentColor = 'var(--accent)';
        falseRadio.addEventListener('change', () => {
          q.correctOptionIndex = 1;
          q.tfCorrect = false;
        });
        falseLabel.append(falseRadio, document.createTextNode('False'));

        tfRow.append(trueLabel, falseLabel);
        tfSection.appendChild(tfRow);
        qCard.appendChild(tfSection);
      }

      questionsContainer.appendChild(qCard);
    });
  }

  renderQuestionsList();

  // ── Submit Bar ─────────────────────────────────────────────────────────────
  const submitBar = el('div', 'card');
  submitBar.style.display = 'flex';
  submitBar.style.justifyContent = 'space-between';
  submitBar.style.alignItems = 'center';
  submitBar.style.padding = '1.5rem 2rem';

  const barInfo = el('div');
  barInfo.append(
    el('span', 'td-strong', 'Ready to publish assessment?'),
    el('p', 'form-hint', 'Once published, candidate sittings may commence according to schedule.')
  );

  const barActions = el('div');
  barActions.style.display = 'flex';
  barActions.style.gap = '1rem';

  const cancelBtn = link('Cancel', '#/exams', 'btn btn-secondary');
  const publishBtn = document.createElement('button');
  publishBtn.type = 'submit';
  publishBtn.className = 'btn btn-primary btn-lg';
  publishBtn.textContent = 'Publish Examination';

  barActions.append(cancelBtn, publishBtn);
  submitBar.append(barInfo, barActions);
  form.appendChild(submitBar);

  // ── Form Validation & Submission ───────────────────────────────────────────
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    let valid = true;
    const titleVal = titleInput.value.trim();
    if (!titleVal) {
      titleInput.classList.add('is-error');
      titleError.hidden = false;
      titleInput.focus();
      valid = false;
    } else {
      titleInput.classList.remove('is-error');
      titleError.hidden = true;
    }

    const durVal = Number(durationInput.value);
    if (!Number.isInteger(durVal) || durVal < 1 || durVal > 300) {
      durationInput.classList.add('is-error');
      durationError.hidden = false;
      if (valid) durationInput.focus();
      valid = false;
    } else {
      durationInput.classList.remove('is-error');
      durationError.hidden = true;
    }

    if (questions.length === 0) {
      toast.error('An examination must contain at least one question.');
      return;
    }

    for (let i = 0; i < questions.length; i++) {
      const q = questions[i];
      if (!q.prompt.trim()) {
        toast.error(`Question #${i + 1} has an empty statement.`);
        return;
      }
      if (q.type === 'MCQ') {
        const nonEmpty = q.choices.filter(c => c.trim().length > 0);
        if (nonEmpty.length < 2) {
          toast.error(`Question #${i + 1} requires at least two non-empty choices.`);
          return;
        }
        if (!q.choices[q.correctOptionIndex]?.trim()) {
          toast.error(`Question #${i + 1} must have a non-empty correct answer selected.`);
          return;
        }
      }
    }

    if (!valid) return;

    publishBtn.disabled = true;
    publishBtn.textContent = 'Publishing...';

    const payload = {
      title: titleVal,
      durationMinutes: durVal,
      questions: questions.map(q => {
        const choices = q.type === 'TF' ? ['True', 'False'] : q.choices.map(c => c.trim()).filter(Boolean);
        const correctOptionIndex = q.type === 'TF'
          ? q.correctOptionIndex
          : q.choices.slice(0, q.correctOptionIndex).filter(c => c.trim()).length;
        return {
          prompt: q.prompt.trim(),
          type: q.type,
          marks: q.marks > 0 ? q.marks : 1,
          choices,
          correctOptionIndex,
          tfCorrect: q.tfCorrect,
        };
      }),
    };

    try {
      const created = await post('/api/exams', payload);
      toast.success(`Examination "${created.title}" published successfully!`);
      location.hash = '#/exams';
    } catch (err) {
      publishBtn.disabled = false;
      publishBtn.textContent = 'Publish Examination';
      toast.error('Failed to create examination: ' + (err.data?.error || err.message));
    }
  });

  container.appendChild(form);
}
