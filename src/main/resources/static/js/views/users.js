/**
 * @fileoverview ExamPro — User Account Administration View (ES module).
 * Role-based access credentials management, account activation statuses,
 * password modification modal, data table with search and sort.
 */

import { get, post, del } from '../api.js';
import { toast, confirm, el, badge, button, createDataTable, renderSkeleton } from '../ui.js';
import { getUser } from '../state.js';

/**
 * Render User Account Management into container.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const currentUser = params.user || getUser();
  const isCurrentUser = (user) => Boolean(currentUser?.username)
    && user.email?.toLowerCase() === currentUser.username.toLowerCase();

  renderSkeleton(container, 4);

  let users = [];
  try {
    users = await get('/api/users');
  } catch (err) {
    toast.error('Failed to load user accounts: ' + (err.data?.error || err.message));
    users = [];
  }

  container.textContent = '';

  const adminCount = users.filter(u => u.role === 'ADMIN').length;
  const teacherCount = users.filter(u => u.role === 'TEACHER').length;
  const studentCount = users.filter(u => u.role === 'STUDENT').length;

  // ── Header ─────────────────────────────────────────────────────────────────
  const header = el('div', 'page-header');
  const headerRow = el('div', 'page-header-row');

  const titleGroup = el('div');
  titleGroup.append(
    el('h1', 'page-title', 'User Accounts Administration'),
    el('p', 'page-description', 'Institutional access governance, role allocations, password management, and account activation.')
  );

  const headerActions = el('div', 'page-actions');
  const createUserBtn = button('+ Create User Account', 'btn btn-primary', () => {
    openCreateModal();
  });
  headerActions.appendChild(createUserBtn);

  headerRow.append(titleGroup, headerActions);
  header.appendChild(headerRow);
  container.appendChild(header);

  // ── Summary Stat Tiles ─────────────────────────────────────────────────────
  const statsGrid = el('div', 'stats-grid');
  const statDefs = [
    { label: 'Total Accounts', value: String(users.length), sub: 'Configured credentials' },
    { label: 'Administrators', value: String(adminCount), sub: 'Full platform governance' },
    { label: 'Faculty / Teachers', value: String(teacherCount), sub: 'Curriculum & test authors' },
    { label: 'Enrolled Candidates', value: String(studentCount), sub: 'Active examinees' },
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

  // ── Main Card & Data Table ─────────────────────────────────────────────────
  const card = el('div', 'card');

  if (!users || users.length === 0) {
    const empty = el('div', 'empty-state');
    empty.append(
      el('h3', 'empty-state-title', 'No user accounts found'),
      el('p', 'empty-state-body', 'Create user accounts to grant administrative, faculty, or student access to ExamPro.')
    );
    empty.appendChild(button('+ Create User Account', 'btn btn-primary btn-sm', () => openCreateModal()));
    card.appendChild(empty);
  } else {
    const tableEl = createDataTable({
      columns: [
        { key: 'id', label: 'Ref', className: 'td-mono', sortable: true, render: (v) => `#${v}` },
        {
          key: 'fullName',
          label: 'Full Name',
          className: 'td-strong',
          sortable: true,
          render: (name, row) => {
            if (!isCurrentUser(row)) return name;
            const label = el('span');
            label.append(document.createTextNode(`${name} `), badge('You', 'badge-indigo'));
            return label;
          }
        },
        { key: 'email', label: 'Email Address', sortable: true },
        {
          key: 'role',
          label: 'System Role',
          sortable: true,
          render: (r) => {
            const v = r === 'ADMIN' ? 'badge-rose' : (r === 'TEACHER' ? 'badge-amber' : 'badge-cyan');
            return badge(r, v);
          }
        },
        {
          key: 'enabled',
          label: 'Status',
          sortable: true,
          render: (en) => badge(en ? 'ACTIVE' : 'SUSPENDED', en ? 'badge-emerald' : 'badge-rose')
        },
        {
          key: 'actions',
          label: 'Actions',
          sortable: false,
          className: 'td-right',
          render: (_, row) => {
            const grp = el('div', 'td-actions');
            grp.style.justifyContent = 'flex-end';

            // Change Password
            const pwBtn = button('Password', 'btn btn-secondary btn-sm', () => {
              openPasswordModal(row.id, row.fullName);
            });

            grp.appendChild(pwBtn);

            if (!isCurrentUser(row)) {
              const toggleBtn = button(row.enabled ? 'Suspend' : 'Activate', 'btn btn-secondary btn-sm', async () => {
                try {
                  const res = await post(`/api/users/${row.id}/toggle-status`, {});
                  toast.success(`Account status updated to ${res.enabled ? 'Active' : 'Suspended'}.`);
                  render(container, params);
                } catch (err) {
                  toast.error('Failed to update status: ' + (err.data?.error || err.message));
                }
              });

              const delBtn = button('Delete', 'btn btn-danger btn-sm', async () => {
                const ok = await confirm({
                  title: 'Delete User Account',
                  body: `Are you sure you want to permanently delete the account for "${row.fullName}" (${row.email})?`,
                  confirmText: 'Delete Account',
                  confirmVariant: 'danger',
                });
                if (!ok) return;

                try {
                  await del(`/api/users/${row.id}`);
                  toast.success(`Account for ${row.fullName} was deleted.`);
                  render(container, params);
                } catch (err) {
                  toast.error('Could not delete account: ' + (err.data?.error || err.message));
                }
              });

              grp.append(toggleBtn, delBtn);
            }
            return grp;
          }
        }
      ],
      data: users,
      searchKeys: ['fullName', 'email', 'role'],
      searchPlaceholder: 'Search users by name, email, or role...',
      emptyText: 'No matching user accounts found.',
    });
    card.appendChild(tableEl);
  }

  container.appendChild(card);

  // ── Create User Modal ──────────────────────────────────────────────────────
  const createModal = el('div', 'modal-overlay');
  createModal.setAttribute('role', 'dialog');
  createModal.setAttribute('aria-modal', 'true');
  createModal.hidden = true;

  const createBox = el('div', 'modal');
  createBox.style.maxWidth = '520px';
  createBox.append(
    el('h2', 'modal-title', 'Create User Account'),
    el('p', 'modal-body', 'Configure credentials and role permissions for a new system user.')
  );

  const cForm = document.createElement('form');
  cForm.noValidate = true;

  // Full name
  const nameGroup = el('div', 'form-group');
  nameGroup.appendChild(el('label', 'form-label', 'Full Name *'));
  const nameInput = document.createElement('input');
  nameInput.className = 'form-control';
  nameInput.placeholder = 'e.g. Professor Alan Turing';
  nameInput.required = true;
  const nameErr = el('span', 'form-error-msg', 'Full name is required.');
  nameErr.hidden = true;
  nameGroup.append(nameInput, nameErr);

  // Email
  const emailGroup = el('div', 'form-group');
  emailGroup.appendChild(el('label', 'form-label', 'Email Address *'));
  const emailInput = document.createElement('input');
  emailInput.type = 'email';
  emailInput.className = 'form-control';
  emailInput.placeholder = 'user@institution.edu';
  emailInput.required = true;
  const emailErr = el('span', 'form-error-msg', 'Valid email address is required.');
  emailErr.hidden = true;
  emailGroup.append(emailInput, emailErr);

  // Role select
  const roleGroup = el('div', 'form-group');
  roleGroup.appendChild(el('label', 'form-label', 'Role Allocation *'));
  const roleSelect = document.createElement('select');
  roleSelect.className = 'form-control';
  const roles = [
    { value: 'STUDENT', label: 'STUDENT — Candidate Assessment Sitting' },
    { value: 'TEACHER', label: 'TEACHER — Curriculum Authoring & Review' },
    { value: 'ADMIN', label: 'ADMIN — Full Institutional Governance' },
  ];
  roles.forEach(r => {
    const opt = document.createElement('option');
    opt.value = r.value;
    opt.textContent = r.label;
    roleSelect.appendChild(opt);
  });
  roleGroup.appendChild(roleSelect);

  // Registration Number (student only)
  const regGroup = el('div', 'form-group');
  regGroup.appendChild(el('label', 'form-label', 'Registration Number (Students Only)'));
  const regInput = document.createElement('input');
  regInput.className = 'form-control';
  regInput.placeholder = 'e.g. STU-2026-088';
  regGroup.appendChild(regInput);

  roleSelect.addEventListener('change', () => {
    regGroup.hidden = roleSelect.value !== 'STUDENT';
  });

  // Password
  const passGroup = el('div', 'form-group');
  passGroup.appendChild(el('label', 'form-label', 'Initial Password * (minimum 6 characters)'));
  const passInput = document.createElement('input');
  passInput.type = 'password';
  passInput.className = 'form-control';
  passInput.placeholder = '••••••••';
  passInput.required = true;
  const passErr = el('span', 'form-error-msg', 'Password must be at least 6 characters long.');
  passErr.hidden = true;
  passGroup.append(passInput, passErr);

  const cActions = el('div', 'modal-actions');
  const cCancelBtn = button('Cancel', 'btn btn-secondary', () => { createModal.hidden = true; });
  const cSaveBtn = document.createElement('button');
  cSaveBtn.type = 'submit';
  cSaveBtn.className = 'btn btn-primary';
  cSaveBtn.textContent = 'Create Account';
  cActions.append(cCancelBtn, cSaveBtn);

  cForm.append(nameGroup, emailGroup, roleGroup, regGroup, passGroup, cActions);
  createBox.appendChild(cForm);
  createModal.appendChild(createBox);
  container.appendChild(createModal);

  function openCreateModal() {
    nameInput.value = '';
    emailInput.value = '';
    regInput.value = '';
    passInput.value = '';
    roleSelect.value = 'STUDENT';
    regGroup.hidden = false;
    nameInput.classList.remove('is-error');
    emailInput.classList.remove('is-error');
    passInput.classList.remove('is-error');
    nameErr.hidden = true;
    emailErr.hidden = true;
    passErr.hidden = true;
    createModal.hidden = false;
    nameInput.focus();
  }

  cForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    let valid = true;
    const nameVal = nameInput.value.trim();
    if (!nameVal) {
      nameInput.classList.add('is-error');
      nameErr.hidden = false;
      nameInput.focus();
      valid = false;
    } else {
      nameInput.classList.remove('is-error');
      nameErr.hidden = true;
    }

    const emailVal = emailInput.value.trim();
    if (!emailVal || !emailVal.includes('@')) {
      emailInput.classList.add('is-error');
      emailErr.hidden = false;
      if (valid) emailInput.focus();
      valid = false;
    } else {
      emailInput.classList.remove('is-error');
      emailErr.hidden = true;
    }

    const passVal = passInput.value.trim();
    if (passVal.length < 6) {
      passInput.classList.add('is-error');
      passErr.hidden = false;
      if (valid) passInput.focus();
      valid = false;
    } else {
      passInput.classList.remove('is-error');
      passErr.hidden = true;
    }

    if (!valid) return;

    cSaveBtn.disabled = true;
    cSaveBtn.textContent = 'Creating...';

    try {
      await post('/api/users', {
        fullName: nameVal,
        email: emailVal,
        role: roleSelect.value,
        registrationNumber: regInput.value.trim() || undefined,
        password: passVal,
      });

      createModal.hidden = true;
      toast.success(`User account for "${nameVal}" created successfully.`);
      render(container, params);
    } catch (err) {
      cSaveBtn.disabled = false;
      cSaveBtn.textContent = 'Create Account';
      toast.error('Failed to create account: ' + (err.data?.error || err.message));
    }
  });

  // ── Password Reset Modal ───────────────────────────────────────────────────
  const pwModal = el('div', 'modal-overlay');
  pwModal.setAttribute('role', 'dialog');
  pwModal.setAttribute('aria-modal', 'true');
  pwModal.hidden = true;

  const pwBox = el('div', 'modal');
  pwBox.style.maxWidth = '460px';
  const pwTitle = el('h2', 'modal-title', 'Change Account Password');
  const pwDesc = el('p', 'modal-body');
  pwBox.append(pwTitle, pwDesc);

  const pwForm = document.createElement('form');
  pwForm.noValidate = true;

  let activeUserId = null;

  const npGroup = el('div', 'form-group');
  npGroup.appendChild(el('label', 'form-label', 'New Password * (minimum 6 characters)'));
  const npInput = document.createElement('input');
  npInput.type = 'password';
  npInput.className = 'form-control';
  npInput.placeholder = '••••••••';
  npInput.required = true;
  const npErr = el('span', 'form-error-msg', 'Password must be at least 6 characters long.');
  npErr.hidden = true;
  npGroup.append(npInput, npErr);

  const cpGroup = el('div', 'form-group');
  cpGroup.appendChild(el('label', 'form-label', 'Confirm New Password *'));
  const cpInput = document.createElement('input');
  cpInput.type = 'password';
  cpInput.className = 'form-control';
  cpInput.placeholder = '••••••••';
  cpInput.required = true;
  const cpErr = el('span', 'form-error-msg', 'Passwords do not match.');
  cpErr.hidden = true;
  cpGroup.append(cpInput, cpErr);

  const pwActions = el('div', 'modal-actions');
  const pwCancel = button('Cancel', 'btn btn-secondary', () => { pwModal.hidden = true; });
  const pwSave = document.createElement('button');
  pwSave.type = 'submit';
  pwSave.className = 'btn btn-primary';
  pwSave.textContent = 'Update Password';
  pwActions.append(pwCancel, pwSave);

  pwForm.append(npGroup, cpGroup, pwActions);
  pwBox.appendChild(pwForm);
  pwModal.appendChild(pwBox);
  container.appendChild(pwModal);

  function openPasswordModal(id, name) {
    activeUserId = id;
    pwDesc.textContent = `Set a new secure password for user ${name}.`;
    npInput.value = '';
    cpInput.value = '';
    npInput.classList.remove('is-error');
    cpInput.classList.remove('is-error');
    npErr.hidden = true;
    cpErr.hidden = true;
    pwModal.hidden = false;
    npInput.focus();
  }

  pwForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    let valid = true;
    const np = npInput.value.trim();
    const cp = cpInput.value.trim();

    if (np.length < 6) {
      npInput.classList.add('is-error');
      npErr.hidden = false;
      npInput.focus();
      valid = false;
    } else {
      npInput.classList.remove('is-error');
      npErr.hidden = true;
    }

    if (np !== cp) {
      cpInput.classList.add('is-error');
      cpErr.hidden = false;
      if (valid) cpInput.focus();
      valid = false;
    } else {
      cpInput.classList.remove('is-error');
      cpErr.hidden = true;
    }

    if (!valid || !activeUserId) return;

    pwSave.disabled = true;
    pwSave.textContent = 'Updating...';

    try {
      await post(`/api/users/${activeUserId}/change-password`, { newPassword: np });
      pwModal.hidden = true;
      toast.success('Account password updated successfully.');
    } catch (err) {
      toast.error('Failed to update password: ' + (err.data?.error || err.message));
    } finally {
      pwSave.disabled = false;
      pwSave.textContent = 'Update Password';
    }
  });
}
