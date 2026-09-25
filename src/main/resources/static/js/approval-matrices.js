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
        const levelBody = document.querySelector('#levelTable tbody');
        const canEdit = !!document.getElementById('canEdit');
        const canDelete = !!document.getElementById('canDelete');
        const typeFilter = document.getElementById('amTypeFilter');

        let options = { teams: [], roles: [], users: [] };
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
                r => `<ol class="space-y-0.5 text-xs">${r.levels.map(l => `<li><span class="tabular-nums text-gray-400">${l.sequence}.</span>
                        ${esc(l.approver)} <span class="text-gray-500">· ${esc(l.band)}</span></li>`).join('')}</ol>`,
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

        const optionList = (list, selected, blank) => (blank ? `<option value="">${esc(blank)}</option>` : '')
            + list.map(o => `<option value="${o.id}"${String(o.id) === String(selected) ? ' selected' : ''}>${esc(o.text)}</option>`).join('');

        function levelRow(level) {
            const kind = level && level.userId ? 'user' : 'role';
            return `<tr>
                <td class="!pl-5 tabular-nums text-gray-500" data-seq></td>
                <td><select class="field" data-kind>
                    <option value="role"${kind === 'role' ? ' selected' : ''}>A role</option>
                    <option value="user"${kind === 'user' ? ' selected' : ''}>One person</option></select></td>
                <td>
                    <select class="field" data-role${kind === 'role' ? '' : ' hidden'}>${optionList(options.roles, level?.roleId, 'Choose the role…')}</select>
                    <select class="field" data-user${kind === 'user' ? '' : ' hidden'}>${optionList(options.users, level?.userId, 'Choose the person…')}</select>
                </td>
                <td><input type="number" min="0" step="0.01" class="field text-right tabular-nums" data-min placeholder="Any" value="${level?.minAmount ?? ''}"></td>
                <td><input type="number" min="0" step="0.01" class="field text-right tabular-nums" data-max placeholder="Any" value="${level?.maxAmount ?? ''}"></td>
                <td><button type="button" class="btn-icon btn-sm hover:text-red-700" data-level-remove aria-label="Remove level">${App.icon('trash')}</button></td>
            </tr>`;
        }

        function renumber() {
            [...levelBody.rows].forEach((tr, i) => { tr.querySelector('[data-seq]').textContent = i + 1; });
        }

        function setLevels(levels) {
            levelBody.innerHTML = (levels && levels.length ? levels : [null]).map(levelRow).join('');
            renumber();
        }

        function readLevels() {
            return [...levelBody.rows].map(tr => {
                const byUser = tr.querySelector('[data-kind]').value === 'user';
                const id = (byUser ? tr.querySelector('[data-user]') : tr.querySelector('[data-role]')).value;
                const num = sel => { const v = tr.querySelector(sel).value; return v === '' ? null : Number(v); };
                return { roleId: !byUser && id ? Number(id) : null, userId: byUser && id ? Number(id) : null,
                         minAmount: num('[data-min]'), maxAmount: num('[data-max]') };
            });
        }

        levelBody.addEventListener('change', event => {
            const kind = event.target.closest('[data-kind]');
            if (!kind) return;
            const tr = kind.closest('tr');
            tr.querySelector('[data-role]').hidden = kind.value !== 'role';
            tr.querySelector('[data-user]').hidden = kind.value !== 'user';
        });
        levelBody.addEventListener('click', event => {
            if (!event.target.closest('[data-level-remove]')) return;
            if (levelBody.rows.length === 1) { App.toast('A matrix needs at least one level.', 'warn'); return; }
            event.target.closest('tr').remove();
            renumber();
            dirty = true;
        });
        form.querySelector('[data-action="level-add"]').addEventListener('click', () => {
            levelBody.insertAdjacentHTML('beforeend', levelRow(null));
            renumber();
            dirty = true;
        });

        // ------------------------------------------------------------------ the dialog

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
            if (!dialog.open) dialog.showModal();
            if (viewOnly && detail) App.viewMode(dialog, true);
            else f(detail ? 'name' : 'documentType').focus();
        }

        form.querySelector('[data-view-actions]').addEventListener('click', event => {
            if (!event.target.closest('[data-action="matrix-edit"]')) return;
            App.viewMode(dialog, false);
            form.querySelector('[data-title]').textContent = `Edit ${matrix.name}`;
            form.querySelector('[data-view-actions]').hidden = true;
            form.querySelector('[data-cancel]').textContent = 'Cancel';
            f('name').focus();
        });

        async function close() {
            if (dirty && !await App.confirm({ title: 'Close without saving?', message: 'Changes to this matrix have not been saved.',
                    confirmText: 'Close', danger: true })) return;
            dialog.close();
        }
        form.querySelectorAll('[data-matrix-close]').forEach(b => b.addEventListener('click', close));
        dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
        form.addEventListener('input', () => { dirty = true; });
        form.addEventListener('change', () => { dirty = true; });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            const levels = readLevels();
            const missing = levels.findIndex(l => !l.roleId && !l.userId);
            if (!f('name').value.trim()) { App.toast('Give the matrix a name.', 'warn'); f('name').focus(); return; }
            if (missing >= 0) { App.toast(`Level ${missing + 1} needs a role or a person.`, 'warn'); return; }
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
                dialog.close();
                grid.reload();
            } catch (error) { App.fail(error); }
        });
    });
})();
