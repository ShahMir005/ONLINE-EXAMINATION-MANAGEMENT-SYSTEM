/**
 * @fileoverview ExamPro — Home / Dashboard Dispatcher View (ES module).
 * Routes to the role-specific dashboard (Admin, Teacher, Student).
 */

import { getUser } from '../state.js';

/**
 * Dispatch to the role-specific dashboard view.
 * @param {HTMLElement} container
 * @param {Object} [params]
 */
export async function render(container, params = {}) {
  const user = params.user || getUser();

  if (user?.role === 'ADMIN') {
    const mod = await import('./dashboard-admin.js');
    return mod.render(container, params);
  }

  if (user?.role === 'TEACHER') {
    const mod = await import('./dashboard-teacher.js');
    return mod.render(container, params);
  }

  const mod = await import('./dashboard-student.js');
  return mod.render(container, params);
}
