/*
 * Approval matrices: the grid, and one dialog that is the matrix's form in every mode - New, Edit
 * and View (App.viewMode, the same form read-only).
 */
(() => {
    'use strict';

    const API = '/api/setup/approval-matrices';
    const esc = s => App.esc(s);

    document.addEventListener('DOMContentLoaded', () => {
        const dialog = document.getElementById('matrixDialog');
        if (!dialog) return;
        const form = document.getElementById('matrixForm');
        const f = name => form.elements.namedItem(name);
        const levelTable = document.getElementById('levelTable');
        const levelBodies = () => [...levelTable.tBodies];
        const canEdit = !!document.getElementById('canEdit');
        const canDelete = !!document.getElementById('canDelete');
        const typeFilter = document.getElementById('amTypeFilter');

        let options = { teams: [], timeoutActions: [] };
        const optionsReady = App.api(`${API}/options`).then(o => { options = o; }).catch(App.fail);
        let matrix = null;
        let dirty = false;

        // ------------------------------------------------------------------ grid

        const grid = new App.Grid({
            url: API,
            table: document.getElementById('matrixGrid'),
            search: document.getElementById('amSearch'),
            pager: document.getElementById('amPager'),
            sort: { column: 'documentType', dir: 'asc' },
            params: () => ({ type: typeFilter.value }),
            emptyText: 'No approval matrices yet: every document type is approved by anyone with Approve on its screen.',
            emptyIcon: 'workflow',
            columns: [
                r => `<span class="font-medium text-gray-900 dark:text-white">${esc(r.documentTypeLabel)}</span>`,
                r => r.marketingTeamId ? `<span class="badge-brand">${esc(r.scope)}</span>` : '<span class="badge-gray">Business unit</span>',
                r => esc(r.name),
                r => `<ol class="space-y-1 text-xs">${r.levels.map(l => `<li><span class="tabular-nums text-gray-400">${l.sequence}.</span>
                        ${esc(l.approver)} <span class="text-gray-500">· ${esc(l.band)}</span>
                        ${l.timing ? `<span class="mt-0.5 flex items-center gap-1 text-amber-700 dark:text-amber-400">${App.icon('clock', 'h-3 w-3')}${esc(l.timing)}</span>` : ''}</li>`).join('')}</ol>`,
                r => r.active ? '<span class="badge-green badge-dot">Active</span>' : '<span class="badge-gray badge-dot">Inactive</span>',
                r => App.rowActions(App.recordButtons(r.id, canEdit),
                    canDelete ? App.rowButton('Delete', 'trash', `data-delete="${r.id}"`, 'text-red-700') : '')
            ],
            onRowClick: row => open(row.id, true)
        });
        grid.reload(true);
        typeFilter.addEventListener('change', () => grid.reload(true));

        document.getElementById('matrixGrid').addEventListener('click', async event => {
            const v = event.target.closest('[data-view]');
            if (v) { open(Number(v.dataset.view), true); return; }
            const e = event.target.closest('[data-edit]');
            if (e) { open(Number(e.dataset.edit), false); return; }
            const d = event.target.closest('[data-delete]');
            if (!d) return;
            const row = grid.rows.find(r => String(r.id) === d.dataset.delete);
            if (!await App.confirm({ title: `Delete “${row ? row.name : 'this matrix'}”?`,
                    message: 'Only a matrix nothing was ever submitted under can be deleted; otherwise deactivate it.',
                    confirmText: 'Delete', danger: true })) return;
            try {
                await App.api(`${API}/${d.dataset.delete}`, { method: 'DELETE' });
                App.toast('Matrix deleted.', 'success');
                grid.reload();
            } catch (error) { App.fail(error); }
        });
        document.querySelector('[data-action="matrix-new"]')?.addEventListener('click', () => open(null, false));

        // ------------------------------------------------------------------ levels

        // A saved level's approver as the picker's one pre-selected option - labelled without a request.
        const saved = (id, text) => id ? `<option value="${esc(id)}" selected>${esc(text || '#' + id)}</option>` : '';

        /*
         * Role or person: searched and paged on the server as you type (App.RemoteSelect, the same picker
         * as Booking's buyer), so a list of hundreds of users or roles is never shipped into the page.
         */
        // A saved limit in its largest whole unit: 2880 → 2 days, 90 → 90 minutes.
        const UNITS = [[1440, 'days'], [60, 'hours'], [1, 'minutes']];
        function splitLimit(minutes) {
            if (!minutes) return { value: '', unit: 60 };
            const [unit] = UNITS.find(([u]) => minutes % u === 0);
            return { value: minutes / unit, unit };
        }

        /*
         * One level = one <tbody data-level>: the signature on the first row, its time frame on the
         * second - how long it may wait, and what happens then (escalating picks its own role or person).
         */
        function levelRow(level) {
            const kind = level && level.userId ? 'user' : 'role';
            const limit = splitLimit(level?.timeLimitMinutes);
            const action = level?.timeoutAction || 'REMIND';
            const escKind = level && level.escalateUserId ? 'user' : 'role';
            const actions = (options.timeoutActions || []).map(a =>
                `<option value="${esc(a.id)}"${a.id === action ? ' selected' : ''}>${esc(a.text)}</option>`).join('');
            return `<tbody data-level class="border-t border-gray-100 first-of-type:border-t-0 dark:border-gray-800">
            <tr>
                <td class="!pl-5 tabular-nums text-gray-500" data-seq></td>
                <td><select class="field" data-kind aria-label="Signed by">
                    <option value="role"${kind === 'role' ? ' selected' : ''}>A role</option>
                    <option value="user"${kind === 'user' ? ' selected' : ''}>One person</option></select></td>
                <td>
                    <div data-role-wrap${kind === 'role' ? '' : ' hidden'}>
                        <select class="field" data-role data-remote="${API}/roles" data-placeholder="Search a role…"
                                data-empty-text="No active role matches">${saved(level?.roleId, level?.roleName)}</select></div>
                    <div data-user-wrap${kind === 'user' ? '' : ' hidden'}>
                        <select class="field" data-user data-remote="${API}/users" data-placeholder="Search a person by name or username…"
                                data-empty-text="No user matches">${saved(level?.userId, level?.userName)}</select></div>
                </td>
                <td><input type="number" min="0" step="0.01" class="field text-right tabular-nums" data-min placeholder="Any" aria-label="From amount" value="${level?.minAmount ?? ''}"></td>
                <td><input type="number" min="0" step="0.01" class="field text-right tabular-nums" data-max placeholder="Any" aria-label="Up to amount" value="${level?.maxAmount ?? ''}"></td>
                <td><button type="button" class="btn-icon btn-sm hover:text-red-700" data-level-remove aria-label="Remove level">${App.icon('trash')}</button></td>
            </tr>
            <tr class="!border-t-0">
                <td class="!pl-5 !pt-0 align-top text-gray-400">${App.icon('clock', 'mt-2.5')}</td>
                <td colspan="5" class="!pt-0">
                    <div class="flex flex-wrap items-end gap-x-4 gap-y-2 rounded-lg bg-gray-50 px-3 py-2.5 dark:bg-gray-800/50">
                        <div>
                            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">Time frame</span>
                            <div class="flex items-center gap-1.5">
                                <input type="number" min="1" step="1" class="field w-24 text-right tabular-nums" data-limit
                                       placeholder="None" aria-label="Time limit" value="${esc(limit.value)}">
                                <select class="field w-auto" data-limit-unit aria-label="Time limit unit">
                                    ${UNITS.map(([u, t]) => `<option value="${u}"${u === limit.unit ? ' selected' : ''}>${t}</option>`).join('')}
                                </select>
                            </div>
                        </div>
                        <div>
                            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">When the time runs out</span>
                            <select class="field w-auto min-w-[13rem]" data-timeout aria-label="When the time runs out">${actions}</select>
                        </div>
                        <div class="min-w-[18rem] flex-1" data-esc-wrap${action === 'ESCALATE' ? '' : ' hidden'}>
                            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">Escalate to <span class="req">*</span></span>
                            <div class="flex items-center gap-1.5">
                                <select class="field w-auto" data-esc-kind aria-label="Escalate to">
                                    <option value="role"${escKind === 'role' ? ' selected' : ''}>A role</option>
                                    <option value="user"${escKind === 'user' ? ' selected' : ''}>One person</option></select>
                                <div class="min-w-0 flex-1" data-esc-role-wrap${escKind === 'role' ? '' : ' hidden'}>
                                    <select class="field" data-esc-role data-remote="${API}/roles" data-placeholder="Search a role…"
                                            data-empty-text="No active role matches">${saved(level?.escalateRoleId, level?.escalateRoleName)}</select></div>
                                <div class="min-w-0 flex-1" data-esc-user-wrap${escKind === 'user' ? '' : ' hidden'}>
                                    <select class="field" data-esc-user data-remote="${API}/users" data-placeholder="Search a person…"
                                            data-empty-text="No user matches">${saved(level?.escalateUserId, level?.escalateUserName)}</select></div>
                            </div>
                        </div>
                        <p class="w-full text-xs text-gray-500" data-timing-hint></p>
                    </div>
                </td>
            </tr>
            </tbody>`;
        }

        const HINTS = {
            REMIND: 'Reminded at three quarters of the time; once it runs out, approvers and maker are told it is overdue and it keeps waiting.',
            ESCALATE: 'Once it runs out, the level is handed to the role or person below, who decides it from then on.',
            AUTO_APPROVE: 'Once it runs out, the system signs this level; at the last level the document is approved.',
            AUTO_RETURN: 'Once it runs out, the document goes back to its maker as a draft, as a Return would.',
            AUTO_REJECT: 'Once it runs out, the document is rejected, as a Reject would.'
        };

        /** A level's time frame row in step with its fields: no limit means nothing can run out. */
        function syncTiming(body) {
            const hasLimit = body.querySelector('[data-limit]').value !== '';
            const timeout = body.querySelector('[data-timeout]');
            if (!hasLimit) timeout.value = 'REMIND';
            timeout.disabled = !hasLimit;
            body.querySelector('[data-esc-wrap]').hidden = timeout.value !== 'ESCALATE';
            const escKind = body.querySelector('[data-esc-kind]').value;
            body.querySelector('[data-esc-role-wrap]').hidden = escKind !== 'role';
            body.querySelector('[data-esc-user-wrap]').hidden = escKind !== 'user';
            body.querySelector('[data-timing-hint]').textContent = hasLimit
                ? HINTS[timeout.value] || '' : 'No time frame: the level waits for as long as it takes.';
        }

        function renumber() {
            levelBodies().forEach((body, i) => { body.querySelector('[data-seq]').textContent = i + 1; });
        }

        function addLevel(level) {
            levelTable.insertAdjacentHTML('beforeend', levelRow(level));
            const body = levelTable.tBodies[levelTable.tBodies.length - 1];
            App.remoteSelects(body);
            syncTiming(body);
        }

        function setLevels(levels) {
            levelBodies().forEach(b => b.remove());
            (levels && levels.length ? levels : [null]).forEach(addLevel);
            renumber();
        }

        function readLevels() {
            return levelBodies().map(body => {
                const byUser = body.querySelector('[data-kind]').value === 'user';
                const id = (byUser ? body.querySelector('[data-user]') : body.querySelector('[data-role]')).value;
                const num = sel => { const v = body.querySelector(sel).value; return v === '' ? null : Number(v); };
                const limit = num('[data-limit]');
                const action = body.querySelector('[data-timeout]').value || 'REMIND';
                const escByUser = body.querySelector('[data-esc-kind]').value === 'user';
                const escId = action === 'ESCALATE'
                    ? (escByUser ? body.querySelector('[data-esc-user]') : body.querySelector('[data-esc-role]')).value : '';
                return { roleId: !byUser && id ? Number(id) : null, userId: byUser && id ? Number(id) : null,
                         minAmount: num('[data-min]'), maxAmount: num('[data-max]'),
                         timeLimitMinutes: limit == null ? null : Math.round(limit * Number(body.querySelector('[data-limit-unit]').value)),
                         timeoutAction: limit == null ? 'REMIND' : action,
                         escalateRoleId: !escByUser && escId ? Number(escId) : null,
                         escalateUserId: escByUser && escId ? Number(escId) : null };
            });
        }

        levelTable.addEventListener('change', event => {
            const body = event.target.closest('[data-level]');
            if (!body) return;
            const kind = event.target.closest('[data-kind]');
            if (kind) {
                body.querySelector('[data-role-wrap]').hidden = kind.value !== 'role';
                body.querySelector('[data-user-wrap]').hidden = kind.value !== 'user';
            }
            syncTiming(body);
        });
        levelTable.addEventListener('input', event => {
            const limit = event.target.closest('[data-limit]');
            if (limit) syncTiming(limit.closest('[data-level]'));
        });
        levelTable.addEventListener('click', event => {
            if (!event.target.closest('[data-level-remove]')) return;
            if (levelTable.tBodies.length === 1) { App.toast('A matrix needs at least one level.', 'warn'); return; }
            event.target.closest('[data-level]').remove();
            renumber();
            dirty = true;
        });
        form.querySelector('[data-action="level-add"]').addEventListener('click', () => {
            addLevel(null);
            renumber();
            dirty = true;
        });

        // ------------------------------------------------------------------ the matrix's page

        // A page, not a modal (App.PageEditor): the list steps aside, ?view=12 / ?edit=12 name the matrix.
        const page = new App.PageEditor({
            list: document.querySelector('[data-list-view]'),
            isDirty: () => dirty,
            onClose: () => { dirty = false; },
            onReopen: p => {
                if (p.get('edit')) open(Number(p.get('edit')), false);
                else if (p.get('view')) open(Number(p.get('view')), true);
                else if (p.has('new') && document.querySelector('[data-action="matrix-new"]')) open(null, false);
                else return false;
            }
        });

        async function open(id, viewOnly) {
            await optionsReady;
            let detail = null;
            if (id != null) {
                try { detail = await App.api(`${API}/${id}`); } catch (error) { App.fail(error); return; }
            }
            App.viewMode(dialog, false);
            matrix = detail;
            f('documentType').value = detail ? detail.documentType : (typeFilter.value || 'BOOKING');
            f('marketingTeamId').innerHTML = '<option value="">Business unit - every team without its own</option>'
                + options.teams.map(t => `<option value="${t.id}">Team ${esc(t.text)}</option>`).join('');
            if (detail && detail.marketingTeamId && !options.teams.some(t => t.id === detail.marketingTeamId)) {
                f('marketingTeamId').insertAdjacentHTML('beforeend', `<option value="${detail.marketingTeamId}">${esc(detail.scope)}</option>`);
            }
            f('marketingTeamId').value = detail && detail.marketingTeamId ? detail.marketingTeamId : '';
            f('name').value = detail ? detail.name : '';
            f('active').checked = detail ? !!detail.active : true;
            // type and team are the matrix's identity once it exists
            f('documentType').disabled = !!detail;
            f('marketingTeamId').disabled = !!detail;
            form.querySelector('[data-fixed-note]').hidden = !detail;
            setLevels(detail ? detail.levels : null);

            const title = form.querySelector('[data-title]');
            const actions = form.querySelector('[data-view-actions]');
            actions.hidden = true;
            actions.innerHTML = '';
            if (viewOnly && detail) {
                title.innerHTML = `${esc(detail.name)} ${detail.active ? '<span class="badge-green badge-dot">Active</span>' : '<span class="badge-gray badge-dot">Inactive</span>'}`;
                if (canEdit) {
                    actions.innerHTML = `<button type="button" class="btn-primary" data-action="matrix-edit">${App.icon('edit')}Edit</button>`;
                    actions.hidden = false;
                }
                form.querySelector('[data-cancel]').textContent = 'Close';
            } else {
                title.textContent = detail ? `Edit ${detail.name}` : 'New approval matrix';
                form.querySelector('[data-cancel]').textContent = 'Cancel';
            }
            dirty = false;
            page.show(dialog, detail ? `?${viewOnly ? 'view' : 'edit'}=${detail.id}` : '?new');
            if (viewOnly && detail) App.viewMode(dialog, true);
            else f(detail ? 'name' : 'documentType').focus();
        }

        form.querySelector('[data-view-actions]').addEventListener('click', event => {
            if (!event.target.closest('[data-action="matrix-edit"]')) return;
            App.viewMode(dialog, false);
            form.querySelector('[data-title]').textContent = `Edit ${matrix.name}`;
            form.querySelector('[data-view-actions]').hidden = true;
            form.querySelector('[data-cancel]').textContent = 'Cancel';
            page.replaceQuery(`?edit=${matrix.id}`);
            f('name').focus();
        });

        /** Back to the list; asks first when there are unsaved changes. */
        function close(force) {
            return page.close(force);
        }
        form.querySelectorAll('[data-matrix-close]').forEach(b => b.addEventListener('click', () => close()));
        form.addEventListener('input', () => { dirty = true; });
        form.addEventListener('change', () => { dirty = true; });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            const levels = readLevels();
            const missing = levels.findIndex(l => !l.roleId && !l.userId);
            const noTarget = levels.findIndex(l => l.timeoutAction === 'ESCALATE' && !l.escalateRoleId && !l.escalateUserId);
            const badLimit = levels.findIndex(l => l.timeLimitMinutes != null && !(l.timeLimitMinutes >= 1));
            if (!f('name').value.trim()) { App.toast('Give the matrix a name.', 'warn'); f('name').focus(); return; }
            if (missing >= 0) { App.toast(`Level ${missing + 1} needs a role or a person.`, 'warn'); return; }
            if (badLimit >= 0) { App.toast(`Level ${badLimit + 1}: a time frame is at least one minute.`, 'warn'); return; }
            if (noTarget >= 0) { App.toast(`Level ${noTarget + 1}: choose the role or person it escalates to.`, 'warn'); return; }
            const body = {
                id: matrix ? matrix.id : null,
                documentType: f('documentType').value,
                marketingTeamId: f('marketingTeamId').value ? Number(f('marketingTeamId').value) : null,
                name: f('name').value.trim(),
                active: f('active').checked,
                levels
            };
            try {
                await App.api(API, { method: 'POST', body });
                App.toast(matrix ? 'Matrix saved.' : 'Matrix created. Documents submitted from now on follow it.', 'success');
                dirty = false;
                close(true);
                grid.reload();
            } catch (error) { App.fail(error); }
        });

        // ?view=12, ?edit=12 or ?new - a link or a refresh - opens the matrix's page.
        const asked = page.initial;
        if (asked.get('edit')) open(Number(asked.get('edit')), false);
        else if (asked.get('view')) open(Number(asked.get('view')), true);
        else if (asked.has('new') && document.querySelector('[data-action="matrix-new"]')) open(null, false);
    });
})();
