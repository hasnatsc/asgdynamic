/**
 * The header's workspace switcher - layout/main.html's #workspaceDialog over /api/workspace.
 *
 * Only what the user is granted is ever offered: the server sends, per organization, the units,
 * stores and cost centres they may pick. Stores follow the unit; everything follows the
 * organization. A switch reloads the page, because every list on it was drawn for the old one.
 */
document.addEventListener('DOMContentLoaded', () => {
    const dialog = document.getElementById('workspaceDialog');
    if (!dialog) return;
    const { api, fail, esc, toast } = App;
    const $ = selector => dialog.querySelector(selector);
    const org = $('#wsOrganization'), unit = $('#wsUnit'), store = $('#wsStore'), centre = $('#wsCentre');
    let organizations = [];

    const label = item => `${item.code} - ${item.name}`;
    const options = (items, selected, none) => (none ? `<option value="">${esc(none)}</option>` : '')
        + items.map(i => `<option value="${i.id}"${i.id === selected ? ' selected' : ''}>${esc(label(i))}</option>`).join('');
    const chosenOrganization = () => organizations.find(o => String(o.id) === org.value);

    function fillOrganization(selection) {
        const o = chosenOrganization();
        unit.innerHTML = options(o ? o.businessUnits : [], selection.businessUnitId);
        fillStores(selection.warehouseId);
        centre.innerHTML = options(o ? o.costCentres : [], selection.costCentreId, 'None');
    }

    function fillStores(selected) {
        const o = chosenOrganization();
        const unitId = Number(unit.value);
        const stores = (o ? o.stores : []).filter(s => s.businessUnitId == null || s.businessUnitId === unitId);
        store.innerHTML = options(stores, selected, 'None');
    }

    /** "ASG · AF - Weaving · WS - Weaving store", from the lists already loaded. */
    function describe(selection) {
        if (!selection) return 'No saved default: each sign-in starts where your administrator placed you.';
        const o = organizations.find(x => x.id === selection.organizationId);
        if (!o) return 'Your saved default is no longer open to you, so sign-ins start where your administrator placed you.';
        const find = (list, id) => list.find(x => x.id === id);
        const parts = [o.code, find(o.businessUnits, selection.businessUnitId), find(o.stores, selection.warehouseId),
                       find(o.costCentres, selection.costCentreId)];
        return 'Your default: ' + parts.filter(Boolean).map(p => typeof p === 'string' ? p : label(p)).join(' · ');
    }

    async function open() {
        try {
            const data = await api('/api/workspace');
            organizations = data.organizations;
            const current = data.current;
            org.innerHTML = organizations.map(o =>
                `<option value="${o.id}"${o.id === current.organizationId ? ' selected' : ''}>${esc(label(o))}</option>`).join('');
            fillOrganization(current);
            $('#wsDefault').checked = false;
            $('#wsSaved').textContent = describe(data.savedDefault);
            dialog.showModal();
            org.focus();
        } catch (error) { fail(error); }
    }

    org.addEventListener('change', () => fillOrganization({}));
    unit.addEventListener('change', () => fillStores(Number(store.value) || null));
    dialog.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => dialog.close()));
    document.querySelectorAll('[data-workspace-open]').forEach(b => b.addEventListener('click', open));

    dialog.querySelector('form').addEventListener('submit', async event => {
        event.preventDefault();
        if (!unit.value) {
            return toast('You have no business unit in that organization yet. Ask an administrator.', 'warn');
        }
        const makeDefault = $('#wsDefault').checked;
        try {
            await api('/api/workspace', { method: 'POST', body: {
                organizationId: Number(org.value),
                businessUnitId: Number(unit.value),
                warehouseId: Number(store.value) || null,
                costCentreId: Number(centre.value) || null,
                makeDefault
            } });
            toast(makeDefault ? 'Workspace switched and saved as your default.' : 'Workspace switched.', 'success');
            dialog.close();
            setTimeout(() => window.location.reload(), 400);
        } catch (error) { fail(error); }
    });
});
