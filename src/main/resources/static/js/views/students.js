/**
 * @fileoverview ExamPro — Student Roster Administration View (ES module).
 * Candidate directory data table with search and sort, registration modal
 * with inline validation, and delete guard.
 */

import { get, post, del } from '../api.js';
import { toast, confirm, el, badge, button, createDataTable, renderSkeleton } from '../ui.js';

/**
 * Render the Students Administration View into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  renderSkeleton(container, 1);

  let students = [];
  try {
    students = await get('/api/students');
  } catch (err) {
    toast.error('Failed to load candidate roster: ' + (err.data?.error || err.message));
    students = [];
  }

  container.textContent = '';

  // ── Header ─────────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  titleGroup.append(
    el('h1', 'page-title', 'Candidate Directory'),
    el('p', 'page-description', 'Institutional student enrollment records, registration numbers, and assessment credentials.')
  );

  const headerActions = el('div', 'page-actions');
  const addStudentBtn = button('+ Register Candidate', 'btn btn-primary', () => {
    openRegisterModal();
  });
  headerActions.appendChild(addStudentBtn);

  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Main Card & Data Table ─────────────────────────────────────────────────
  const card = el('div', 'card');

  if (!students || students.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No candidates enrolled in registry'),
      el('p', 'empty-state-body', 'Register new candidate students to grant examination sitting access and track performance records.')
    );
    empty.appendChild(button('+ Register Candidate', 'btn btn-primary btn-sm', () => openRegisterModal()));
    card.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'id', label: 'Ref', className: 'td-mono', sortable: true, render: (v) => `#${v}` },
        { key: 'name', label: 'Full Name', className: 'td-strong', sortable: true },
        { key: 'email', label: 'Institutional Email', sortable: true },
        {
          key: 'registrationNumber',
          label: 'Registration No.',
          sortable: true,
          render: (v) => badge(v, 'badge-indigo')
        },
        {
          key: 'action',
          label: 'Action',
          sortable: false,
          className: 'td-right',
          render: (_, row) => button('Remove', 'btn btn-danger btn-sm', async () => {
            const ok = await confirm({
              title: 'Remove Candidate Record',
              body: `Are you sure you want to remove ${row.name} (#${row.id})? Their login account and past assessment submissions will also be deleted.`,
              confirmText: 'Remove Candidate',
              confirmVariant: 'danger',
            });
            if (!ok) return;

            try {
              await del(`/api/students/${row.id}`);
              toast.success(`Candidate ${row.name} was removed.`);
              render(container, params);
            } catch (err) {
              toast.error('Could not remove candidate: ' + (err.data?.error || err.message));
            }
          })
        }
      ],
      data: students,
      searchKeys: ['name', 'email', 'registrationNumber'],
      searchPlaceholder: 'Search by name, email, or registration number...',
      emptyText: 'No matching candidates found in directory.',
    });
    card.appendChild(tableEl);
  }

  container.appendChild(card);

  // ── Register Student Modal ─────────────────────────────────────────────────
  const modalOverlay = el('div', 'modal-overlay');
  modalOverlay.setAttribute('role', 'dialog');
  modalOverlay.setAttribute('aria-modal', 'true');
  modalOverlay.hidden = true;

  const modalBox = el('div', 'modal');
  modalBox.style.maxWidth = '520px';

  modalBox.append(
    el('h2', 'modal-title', 'Register Candidate'),
    el('p', 'modal-body', 'Create a new institutional candidate profile and provision login credentials.')
  );

  const regForm = document.createElement('form');
  regForm.noValidate = true;

  // Name
  const nameGroup = el('div', 'form-group');
  const nameLabel = el('label', 'form-label', 'Full Name *');
  const nameInput = document.createElement('input');
  nameInput.className = 'form-control';
  nameInput.placeholder = 'e.g. Johnathan Doe';
  nameInput.required = true;
  const nameError = el('span', 'form-error-msg', 'Please enter candidate full name.');
  nameError.hidden = true;
  nameGroup.append(nameLabel, nameInput, nameError);

  // Email
  const emailGroup = el('div', 'form-group');
  const emailLabel = el('label', 'form-label', 'Institutional Email *');
  const emailInput = document.createElement('input');
  emailInput.type = 'email';
  emailInput.className = 'form-control';
  emailInput.placeholder = 'candidate@institution.edu';
  emailInput.required = true;
  const emailError = el('span', 'form-error-msg', 'Please enter a valid email address.');
  emailError.hidden = true;
  emailGroup.append(emailLabel, emailInput, emailError);

  // Reg No
  const regNumGroup = el('div', 'form-group');
  const regNumLabel = el('label', 'form-label', 'Registration Number *');
  const regNumInput = document.createElement('input');
  regNumInput.className = 'form-control';
  regNumInput.placeholder = 'e.g. STU-2026-001';
  regNumInput.required = true;
  const regNumError = el('span', 'form-error-msg', 'Please enter candidate registration number.');
  regNumError.hidden = true;
  regNumGroup.append(regNumLabel, regNumInput, regNumError);

  const modalActions = el('div', 'modal-actions');
  const cancelBtn = button('Cancel', 'btn btn-secondary', () => {
    modalOverlay.hidden = true;
  });
  const saveBtn = document.createElement('button');
  saveBtn.type = 'submit';
  saveBtn.className = 'btn btn-primary';
  saveBtn.textContent = 'Register Candidate';

  modalActions.append(cancelBtn, saveBtn);
  regForm.append(nameGroup, emailGroup, regNumGroup, modalActions);
  modalBox.appendChild(regForm);
  modalOverlay.appendChild(modalBox);
  container.appendChild(modalOverlay);

  function openRegisterModal() {
    nameInput.value = '';
    emailInput.value = '';
    regNumInput.value = '';
    nameInput.classList.remove('is-error');
    emailInput.classList.remove('is-error');
    regNumInput.classList.remove('is-error');
    nameError.hidden = true;
    emailError.hidden = true;
    regNumError.hidden = true;
    modalOverlay.hidden = false;
    nameInput.focus();
  }

  regForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    let valid = true;
    const nameVal = nameInput.value.trim();
    if (!nameVal) {
      nameInput.classList.add('is-error');
      nameError.hidden = false;
      nameInput.focus();
      valid = false;
    } else {
      nameInput.classList.remove('is-error');
      nameError.hidden = true;
    }

    const emailVal = emailInput.value.trim();
    if (!emailVal || !emailVal.includes('@')) {
      emailInput.classList.add('is-error');
      emailError.hidden = false;
      if (valid) emailInput.focus();
      valid = false;
    } else {
      emailInput.classList.remove('is-error');
      emailError.hidden = true;
    }

    const regVal = regNumInput.value.trim();
    if (!regVal) {
      regNumInput.classList.add('is-error');
      regNumError.hidden = false;
      if (valid) regNumInput.focus();
      valid = false;
    } else {
      regNumInput.classList.remove('is-error');
      regNumError.hidden = true;
    }

    if (!valid) return;

    saveBtn.disabled = true;
    saveBtn.textContent = 'Registering...';

    try {
      const res = await post('/api/students', {
        name: nameVal,
        email: emailVal,
        registrationNumber: regVal,
      });

      modalOverlay.hidden = true;

      if (res && res.oneTimePassword) {
        await confirm({
          title: 'Candidate Account Provisioned',
          body: `One-Time Login Password for ${nameVal}: ${res.oneTimePassword}\n\nPlease record and share this one-time credential with the candidate. For security, it will not be displayed again.`,
          confirmText: 'Acknowledged',
          confirmVariant: 'primary',
        });
      }

      toast.success(`Candidate "${nameVal}" registered successfully.`);
      render(container, params);
    } catch (err) {
      saveBtn.disabled = false;
      saveBtn.textContent = 'Register Candidate';
      toast.error('Failed to register candidate: ' + (err.data?.error || err.message));
    }
  });
}
