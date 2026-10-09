/**
 * toast.js — ExamPro toast notification helper.
 *
 * Appends temporary alert toasts to #toast-container.
 */

export function showToast(message, type = 'info', durationMs = 4500) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type === 'error' ? 'danger' : type}`;
  toast.setAttribute('role', 'status');

  const text = document.createElement('span');
  text.className = 'toast-message';
  text.textContent = message;

  const closeBtn = document.createElement('button');
  closeBtn.className = 'toast-close';
  closeBtn.setAttribute('aria-label', 'Close notification');
  closeBtn.innerHTML = '&times;';

  const removeToast = () => {
    toast.classList.add('toast-fade-out');
    setTimeout(() => toast.remove(), 200);
  };

  closeBtn.addEventListener('click', removeToast);

  toast.appendChild(text);
  toast.appendChild(closeBtn);
  container.appendChild(toast);

  if (durationMs > 0) {
    setTimeout(removeToast, durationMs);
  }
}
