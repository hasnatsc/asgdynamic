/*
 * Marketing teams screen: the grid, and one dialog that is the team's form in every mode - New,
 * Edit, and View (App.viewMode, the same form read-only), as the Booking screen does.
 *
 * Members are saved as they are added or removed (each is its own request), so they need a saved
 * team; the Details tab is saved with the Save button. Approval is the team's matrix, set up on the
 * Approval matrices screen.
 */
(() => {
    'use strict';

    const API = '/api/setup/marketing-teams';
    const esc = s => App.esc(s);
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });

    document.addEventListener('DOMContentLoaded', () => {
        const dialog = document.getElementById('teamDialog');
        if (!dialog) return;
        const form = document.getElementById('teamForm');
        const f = name => form.elements.namedItem(name);
        const canEdit = !!document.getElementById('canEdit');
        const canDelete = !!document.getElementById('canDelete');
        const activeFilter = document.getElementById('mtActiveFilter');
        const tabs = App.tabs(form.querySelector('[data-team-body]'));
        const memberPicker = () => App.RemoteSelect.of(document.getElementById('mtMemberUser'));

        let team = null;       // the team in the dialog, as the server last returned it; null for a new one
        let dirty = false;

        // ------------------------------------------------------------------ grid

        const grid = new App.Grid({
            url: API,
            table: document.getElementById('teamGrid'),
            search: document.getElementById('mtSearch'),
            pager: document.getElementById('mtPager'),
            sort: { column: 'name', dir: 'asc' },
            params: () => ({ active: activeFilter.value }),
            emptyText: 'No marketing teams match. Create the first with “New team”.',
            emptyIcon: 'users',
            columns: [
                r => `<span class="font-mono text-gray-600 dark:text-gray-300">${esc(r.code)}</span>`,
                r => `<span class="font-medium text-gray-900 dark:text-white">${esc(r.name)}</span>`,
                r => r.leaderName ? esc(r.leaderName) : '<span class="text-gray-400">—</span>',
                r => `<span class="block text-right tabular-nums">${esc(r.memberCount)}</span>`,
                r => r.matrixCount
                    ? `<span class="badge-brand">Team matrix${r.matrixCount > 1 ? 'es (' + esc(r.matrixCount) + ')' : ''}</span>`
                    : '<span class="badge-gray" title="Approved under the business unit’s matrix">Business unit</span>',
                r => `<span class="block text-right tabular-nums">${r.bookingTarget == null ? '<span class="text-gray-400">—</span>' : esc(nf.format(r.bookingTarget))}</span>`,
                r => r.active ? '<span class="badge-green badge-dot">Active</span>' : '<span class="badge-gray badge-dot">Inactive</span>',
                r => App.rowActions(App.recordButtons(r.id, canEdit),
                    canDelete ? App.rowButton('Delete', 'trash', `data-delete="${r.id}"`, 'text-red-700') : '')
            ],
            onRowClick: row => open(row.id, true)
        });
        grid.reload(true);
        activeFilter.addEventListener('change', () => grid.reload(true));

        document.getElementById('teamGrid').addEventListener('click', async event => {
            const v = event.target.closest('[data-view]');
            if (v) { open(Number(v.dataset.view), true); return; }
            const e = event.target.closest('[data-edit]');
            if (e) { open(Number(e.dataset.edit), false); return; }
            const d = event.target.closest('[data-delete]');
            if (!d) return;
            const row = grid.rows.find(r => String(r.id) === d.dataset.delete);
            if (!await App.confirm({ title: `Delete ${row ? row.name : 'this team'}?`,
                    message: 'Only a team with no members and no documents can be deleted; otherwise deactivate it.',
                    confirmText: 'Delete', danger: true })) return;
            try {
                await App.api(`${API}/${d.dataset.delete}`, { method: 'DELETE' });
                App.toast('Team deleted.', 'success');
                grid.reload();
            } catch (error) { App.fail(error); }
        });

        document.querySelector('[data-action="team-new"]')?.addEventListener('click', () => open(null, false));

        // ------------------------------------------------------------------ the dialog

        async function open(id, viewOnly) {
            let detail = null;
            if (id != null) {
                try { detail = await App.api(`${API}/${id}`); } catch (error) { App.fail(error); return; }
            }
            App.viewMode(dialog, false);
            fill(detail);
            tabs.select('details');
            setMode(viewOnly && !!detail);
            if (!dialog.open) dialog.showModal();
            form.querySelector('[data-team-body]').scrollTop = 0;
            if (!viewOnly) f('code').focus();
        }

        function fill(detail) {
            team = detail;
            f('id').value = detail ? detail.id : '';
            f('code').value = detail ? detail.code : '';
            f('name').value = detail ? detail.name : '';
            f('bookingTarget').value = detail && detail.bookingTarget != null ? detail.bookingTarget : '';
            f('remarks').value = detail ? (detail.remarks || '') : '';
            f('active').checked = detail ? !!detail.active : true;
            renderMembers(detail ? detail.members : []);
            form.querySelector('[data-matrix-summary]').innerHTML = !detail ? 'Save the team first.'
                : detail.matrixCount
                    ? `<span class="badge-brand">Team matrix</span> <span class="ml-1 text-gray-600 dark:text-gray-400">${esc(detail.matrixCount)} active - this team's documents follow it.</span>`
                    : '<span class="badge-gray">Business unit</span> <span class="ml-1 text-gray-600 dark:text-gray-400">No matrix of its own - it follows the unit-wide one.</span>';
            form.querySelectorAll('[data-need-saved]').forEach(el => { el.hidden = !detail; });
            form.querySelector('[data-foot-note]').textContent = detail ? ''
                : 'Save the team to add its members.';
            dirty = false;
        }

        /** View: the same form read-only, with Edit in the footer; otherwise the editor. */
        function setMode(view) {
            const title = form.querySelector('[data-title]');
            const actions = form.querySelector('[data-view-actions]');
            const cancel = form.querySelector('[data-cancel]');
            actions.innerHTML = '';
            actions.hidden = true;
            if (view) {
                title.innerHTML = `${esc(team.name)} ${team.active ? '<span class="badge-green badge-dot">Active</span>' : '<span class="badge-gray badge-dot">Inactive</span>'}`;
                form.querySelector('[data-sub]').textContent = `Code ${team.code}`;
                if (canEdit) {
                    actions.innerHTML = `<button type="button" class="btn-primary" data-action="team-edit">${App.icon('edit')}Edit</button>`;
                    actions.hidden = false;
                }
                cancel.textContent = 'Close';
                App.viewMode(dialog, true);
            } else {
                title.textContent = team ? `Edit ${team.name}` : 'New team';
                form.querySelector('[data-sub]').textContent = team
                    ? 'Details are saved with Save; members are saved as you add or remove them.'
                    : 'Give it a code and a name; add its people once it is saved.';
                cancel.textContent = team ? 'Close' : 'Cancel';
            }
        }

        form.querySelector('[data-view-actions]').addEventListener('click', event => {
            if (!event.target.closest('[data-action="team-edit"]')) return;
            App.viewMode(dialog, false);
            setMode(false);
            f('name').focus();
        });

        async function close() {
            if (dirty && !await App.confirm({ title: 'Close without saving?',
                    message: 'Changes to the details have not been saved.', confirmText: 'Close', danger: true })) return;
            dialog.close();
        }
        form.querySelectorAll('[data-team-close]').forEach(b => b.addEventListener('click', close));
        dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
        form.querySelector('[data-panel="details"]').addEventListener('input', () => { dirty = true; });
        form.querySelector('[data-panel="details"]').addEventListener('change', () => { dirty = true; });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (!f('code').value.trim() || !f('name').value.trim()) {
                App.toast('A team needs a code and a name.', 'warn');
                (f('code').value.trim() ? f('name') : f('code')).focus();
                return;
            }
            const body = {
                id: team ? team.id : null,
                code: f('code').value.trim(),
                name: f('name').value.trim(),
                leaderUserId: f('leaderUserId').value ? Number(f('leaderUserId').value) : null,
                bookingTarget: f('bookingTarget').value === '' ? null : Number(f('bookingTarget').value),
                remarks: f('remarks').value.trim() || null,
                active: f('active').checked
            };
            try {
                const saved = await App.api(API, { method: 'POST', body });
                App.toast(team ? 'Team saved.' : 'Team created. Add its members next.', 'success');
                const created = !team;
                fill(await App.api(`${API}/${saved.id}`));
                setMode(false);
                if (created) tabs.select('members');
                grid.reload();
            } catch (error) { App.fail(error); }
        });

        // ------------------------------------------------------------------ members

        const personRow = (p, extra) => `<tr>
            <td><div class="font-medium text-gray-900 dark:text-white">${esc(p.fullName)}${extra || ''}</div>
                <div class="text-xs text-gray-500">${esc(p.username)}</div></td>
            <td class="text-gray-600 dark:text-gray-400">${p.since ? esc(App.fmt.date(p.since)) : '—'}</td>
            <td>${p.usable ? '<span class="badge-green badge-dot">Can sign in</span>'
                           : '<span class="badge-amber badge-dot" title="Locked or inactive: cannot act until unlocked">Locked / inactive</span>'}</td>`;

        function renderMembers(members) {
            form.querySelector('[data-count="members"]').textContent = members.length;
            const leader = team ? team.leaderUserId : null;
            form.querySelector('[data-list="members"]').innerHTML = members.length ? `
                <div class="table-wrap -mx-5"><table class="line-colours">
                    <thead><tr><th>Member</th><th>Since</th><th>Account</th><th class="w-px"></th></tr></thead>
                    <tbody>${members.map(m => `${personRow(m, m.userId === leader ? ' <span class="badge-brand ml-1">Leader</span>' : '')}
                        <td class="text-right"><button type="button" class="btn-ghost btn-sm text-red-700" data-member-remove="${m.id}"
                            title="End this membership from today">${App.icon('trash')}Remove</button></td></tr>`).join('')}</tbody>
                </table></div>`
                : '<p class="rounded-xl border border-dashed border-gray-300 px-4 py-6 text-center text-sm text-gray-500 dark:border-gray-700">No members yet.</p>';

            // the leader is one of the members
            const select = f('leaderUserId');
            select.innerHTML = '<option value="">— No leader</option>'
                + members.map(m => `<option value="${m.userId}">${esc(m.fullName)}</option>`).join('');
            select.value = leader != null && members.some(m => m.userId === leader) ? String(leader) : '';
        }

        form.querySelector('[data-action="member-add"]').addEventListener('click', async () => {
            const userId = document.getElementById('mtMemberUser').value;
            if (!userId) { App.toast('Choose the user to add.', 'warn'); return; }
            try {
                const members = await App.api(`${API}/${team.id}/members`, { method: 'POST',
                    body: { userId: Number(userId), from: document.getElementById('mtMemberFrom').value || null } });
                renderMembers(members);
                memberPicker().setValue(null);
                App.toast('Member added. They now see this team’s documents only.', 'success');
                grid.reload();
            } catch (error) { App.fail(error); }
        });

        form.querySelector('[data-list="members"]').addEventListener('click', async event => {
            const btn = event.target.closest('[data-member-remove]');
            if (!btn) return;
            const values = await App.form({ title: 'Remove from the team?',
                message: 'Their membership ends today. Documents they raised stay with this team.',
                fields: [{ name: 'reason', label: 'Reason', type: 'textarea', maxlength: 255, required: true }],
                confirmText: 'Remove', danger: true });
            if (!values) return;
            try {
                renderMembers(await App.api(`${API}/${team.id}/members/${btn.dataset.memberRemove}`,
                    { method: 'DELETE', query: { reason: values.reason } }));
                App.toast('Member removed.', 'success');
                grid.reload();
            } catch (error) { App.fail(error); }
        });

    });
})();
