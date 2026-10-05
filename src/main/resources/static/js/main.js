/* ==========================================================================
   ExamPro - Client-side Interactive Engine
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
    initOptionSelection();
    initExamTimer();
    initQuestionBuilder();
    initTableSearch();
});

/**
 * Highlights selected answer cards during exam taking
 */
function initOptionSelection() {
    const radioInputs = document.querySelectorAll('.choice-label-card input[type="radio"]');
    radioInputs.forEach(radio => {
        // If pre-checked on load
        if (radio.checked) {
            radio.closest('.choice-label-card').classList.add('selected');
        }

        radio.addEventListener('change', () => {
            const groupName = radio.name;
            document.querySelectorAll(`input[name="${groupName}"]`).forEach(r => {
                r.closest('.choice-label-card').classList.remove('selected');
            });
            if (radio.checked) {
                radio.closest('.choice-label-card').classList.add('selected');
            }
            updateExamProgress();
        });
    });

    updateExamProgress();
}

/**
 * Calculates and displays exam progress during taking
 */
function updateExamProgress() {
    const progressEl = document.getElementById('exam-progress-text');
    const progressBar = document.getElementById('exam-progress-bar');
    if (!progressEl) return;

    const questionBlocks = document.querySelectorAll('.question-block');
    const total = questionBlocks.length;
    let answered = 0;

    questionBlocks.forEach(block => {
        const checked = block.querySelector('input[type="radio"]:checked');
        if (checked) {
            answered++;
        }
    });

    progressEl.textContent = `${answered} of ${total} answered`;
    if (progressBar && total > 0) {
        const pct = Math.round((answered / total) * 100);
        progressBar.style.width = `${pct}%`;
    }
}

/**
 * Live countdown timer for the exam take page
 */
function initExamTimer() {
    const timerEl = document.getElementById('live-exam-timer');
    if (!timerEl) return;

    const durationMinutes = parseInt(timerEl.getAttribute('data-minutes') || '30', 10);
    let secondsLeft = durationMinutes * 60;

    function formatTime(secs) {
        const m = Math.floor(secs / 60);
        const s = secs % 60;
        return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    }

    timerEl.textContent = formatTime(secondsLeft);

    const interval = setInterval(() => {
        secondsLeft--;
        if (secondsLeft <= 0) {
            clearInterval(interval);
            timerEl.textContent = "00:00 - Time's Up!";
            timerEl.style.color = '#f43f5e';
            alert("Exam duration has concluded. Please submit your answers now.");
        } else {
            timerEl.textContent = formatTime(secondsLeft);
            if (secondsLeft < 300) {
                timerEl.style.color = '#f43f5e'; // red alert under 5 mins
            }
        }
    }, 1000);
}

/**
 * Dynamic Question Builder for Create Exam Form (MCQ + True/False)
 */
function initQuestionBuilder() {
    const questionsContainer = document.getElementById('questions-container');
    const addMcqBtn = document.getElementById('add-mcq-btn');
    const addTfBtn = document.getElementById('add-tf-btn');
    const examForm = document.getElementById('create-exam-form');

    if (!questionsContainer || !examForm) return;

    function reindexQuestions() {
        const questionCards = questionsContainer.querySelectorAll('.question-item-card');
        questionCards.forEach((card, qIndex) => {
            card.dataset.index = qIndex;
            const numTag = card.querySelector('.question-number-display');
            if (numTag) {
                numTag.textContent = `Question #${qIndex + 1}`;
            }

            // Update field names for Spring MVC databinding: questions[qIndex].*
            const typeInput = card.querySelector('.q-type-input');
            if (typeInput) typeInput.name = `questions[${qIndex}].type`;

            const promptInput = card.querySelector('.q-prompt-input');
            if (promptInput) promptInput.name = `questions[${qIndex}].prompt`;

            const marksInput = card.querySelector('.q-marks-input');
            if (marksInput) marksInput.name = `questions[${qIndex}].marks`;

            const qType = typeInput ? typeInput.value : 'MCQ';

            if (qType === 'MCQ') {
                const optionInputs = card.querySelectorAll('.q-option-input');
                optionInputs.forEach((optInput, optIndex) => {
                    optInput.name = `questions[${qIndex}].options[${optIndex}]`;
                });

                const correctRadios = card.querySelectorAll('.q-correct-radio');
                correctRadios.forEach(radio => {
                    radio.name = `questions[${qIndex}].correctOption`;
                });
            } else if (qType === 'TF') {
                const tfRadios = card.querySelectorAll('.q-tf-radio');
                tfRadios.forEach(radio => {
                    radio.name = `questions[${qIndex}].tfCorrect`;
                });
            }
        });

        updateSummaryCounts();
    }

    function updateSummaryCounts() {
        const countTag = document.getElementById('total-questions-count');
        const marksTag = document.getElementById('total-marks-count');
        if (!countTag) return;

        const cards = questionsContainer.querySelectorAll('.question-item-card');
        countTag.textContent = cards.length;

        let totalMarks = 0;
        cards.forEach(card => {
            const marksInput = card.querySelector('.q-marks-input');
            if (marksInput) {
                totalMarks += parseInt(marksInput.value || '0', 10);
            }
        });
        if (marksTag) marksTag.textContent = totalMarks;
    }

    if (addMcqBtn) {
        addMcqBtn.addEventListener('click', () => {
            const index = questionsContainer.querySelectorAll('.question-item-card').length;
            const card = document.createElement('div');
            card.className = 'question-item-card';
            card.innerHTML = `
                <div class="question-card-top">
                    <span class="question-number-tag question-number-display">Question #${index + 1}</span>
                    <div style="display:flex; align-items:center; gap:0.75rem;">
                        <span class="badge badge-indigo">Multiple Choice (MCQ)</span>
                        <button type="button" class="btn btn-sm btn-outline-danger remove-question-btn" title="Remove question">✕</button>
                    </div>
                </div>
                <input type="hidden" class="q-type-input" value="MCQ" />
                <div class="form-group">
                    <label class="form-label">Question Prompt</label>
                    <textarea class="form-control q-prompt-input" rows="2" placeholder="e.g. Which Java keyword implements inheritance?" required></textarea>
                </div>
                <div class="form-row" style="margin-bottom: 1rem;">
                    <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label">Marks</label>
                        <input type="number" class="form-control q-marks-input" value="5" min="1" max="100" required />
                    </div>
                    <div class="form-group" style="margin-bottom:0; display:flex; flex-direction:column; justify-content:center;">
                        <span style="font-size:0.85rem; color:var(--text-muted);">Select the radio button next to the correct answer choice below.</span>
                    </div>
                </div>
                <label class="form-label">Options (MCQ Choices):</label>
                <div class="options-builder-grid">
                    <div class="option-input-wrapper">
                        <input type="radio" class="correct-selector-radio q-correct-radio" value="0" checked title="Correct Answer" />
                        <input type="text" class="form-control q-option-input" placeholder="Option A" required />
                    </div>
                    <div class="option-input-wrapper">
                        <input type="radio" class="correct-selector-radio q-correct-radio" value="1" title="Correct Answer" />
                        <input type="text" class="form-control q-option-input" placeholder="Option B" required />
                    </div>
                    <div class="option-input-wrapper">
                        <input type="radio" class="correct-selector-radio q-correct-radio" value="2" title="Correct Answer" />
                        <input type="text" class="form-control q-option-input" placeholder="Option C" />
                    </div>
                    <div class="option-input-wrapper">
                        <input type="radio" class="correct-selector-radio q-correct-radio" value="3" title="Correct Answer" />
                        <input type="text" class="form-control q-option-input" placeholder="Option D" />
                    </div>
                </div>
            `;
            questionsContainer.appendChild(card);
            reindexQuestions();
        });
    }

    if (addTfBtn) {
        addTfBtn.addEventListener('click', () => {
            const index = questionsContainer.querySelectorAll('.question-item-card').length;
            const card = document.createElement('div');
            card.className = 'question-item-card';
            card.innerHTML = `
                <div class="question-card-top">
                    <span class="question-number-tag question-number-display">Question #${index + 1}</span>
                    <div style="display:flex; align-items:center; gap:0.75rem;">
                        <span class="badge badge-cyan">True / False</span>
                        <button type="button" class="btn btn-sm btn-outline-danger remove-question-btn" title="Remove question">✕</button>
                    </div>
                </div>
                <input type="hidden" class="q-type-input" value="TF" />
                <div class="form-group">
                    <label class="form-label">Statement / Question Prompt</label>
                    <textarea class="form-control q-prompt-input" rows="2" placeholder="e.g. Java supports multiple inheritance of classes." required></textarea>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label class="form-label">Marks</label>
                        <input type="number" class="form-control q-marks-input" value="2" min="1" max="100" required />
                    </div>
                    <div class="form-group">
                        <label class="form-label">Correct Answer</label>
                        <div style="display:flex; gap:1.5rem; margin-top:0.5rem;">
                            <label style="display:flex; align-items:center; gap:0.4rem; cursor:pointer;">
                                <input type="radio" class="q-tf-radio" value="true" checked /> True
                            </label>
                            <label style="display:flex; align-items:center; gap:0.4rem; cursor:pointer;">
                                <input type="radio" class="q-tf-radio" value="false" /> False
                            </label>
                        </div>
                    </div>
                </div>
            `;
            questionsContainer.appendChild(card);
            reindexQuestions();
        });
    }

    // Delegate question removal
    questionsContainer.addEventListener('click', e => {
        if (e.target.closest('.remove-question-btn')) {
            const card = e.target.closest('.question-item-card');
            if (questionsContainer.querySelectorAll('.question-item-card').length <= 1) {
                alert("An exam must have at least one question.");
                return;
            }
            card.remove();
            reindexQuestions();
        }
    });

    // Re-index before submitting form
    examForm.addEventListener('submit', () => {
        reindexQuestions();
    });

    // Recalculate marks on change
    questionsContainer.addEventListener('input', e => {
        if (e.target.classList.contains('q-marks-input')) {
            updateSummaryCounts();
        }
    });

    reindexQuestions();
}

/**
 * Filter tables by search term
 */
function initTableSearch() {
    const searchInput = document.getElementById('table-search-input');
    if (!searchInput) return;

    searchInput.addEventListener('input', () => {
        const query = searchInput.value.toLowerCase().trim();
        const rows = document.querySelectorAll('.data-table tbody tr');
        rows.forEach(row => {
            const text = row.textContent.toLowerCase();
            row.style.display = text.includes(query) ? '' : 'none';
        });
    });
}
