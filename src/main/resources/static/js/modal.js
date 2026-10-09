/**
 * modal.js — ExamPro confirmation modal dialog helper.
 *
 * Provides a Promise-based confirmModal() function for destructive or critical actions.
 */

export function confirmModal({
  title = 'Please confirm',
  body = 'Are you sure you want to proceed?',
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  confirmClass = 'btn-danger',
} = {}) {
  return new Promise((resolve) => {
    const overlay = document.getElementById('modal-overlay');
    const titleEl = document.getElementById('modal-title');
    const bodyEl = document.getElementById('modal-body');
    const confirmBtn = document.getElementById('modal-confirm');
    const cancelBtn = document.getElementById('modal-cancel');

    if (!overlay || !confirmBtn || !cancelBtn) {
      resolve(window.confirm(`${title}\n\n${body}`));
      return;
    }

    titleEl.textContent = title;
    bodyEl.textContent = body;
    confirmBtn.textContent = confirmText;
    confirmBtn.className = `btn ${confirmClass}`;
    cancelBtn.textContent = cancelText;

    overlay.removeAttribute('hidden');
    confirmBtn.focus();

    const cleanup = () => {
      overlay.setAttribute('hidden', '');
      confirmBtn.removeEventListener('click', onConfirm);
      cancelBtn.removeEventListener('click', onCancel);
      document.removeEventListener('keydown', onKeyDown);
      overlay.removeEventListener('click', onBackdropClick);
    };

    const onConfirm = () => {
      cleanup();
      resolve(true);
    };

    const onCancel = () => {
      cleanup();
      resolve(false);
    };

    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        onCancel();
      }
    };

    const onBackdropClick = (e) => {
      if (e.target === overlay) {
        onCancel();
      }
    };

    confirmBtn.addEventListener('click', onConfirm);
    cancelBtn.addEventListener('click', onCancel);
    document.addEventListener('keydown', onKeyDown);
    overlay.addEventListener('click', onBackdropClick);
  });
}
