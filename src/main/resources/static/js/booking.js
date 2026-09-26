/*
 * Booking editor. The list and review drawer are App.DocumentScreen; this file is the editor.
 *
 * State lives here, not in the DOM: the header fields, `groups` (fabric specifications, each
 * with its colour lines) and `terms`. One specification is edited at a time in #specEditor and
 * committed to `groups` with "Add to booking" - the legacy screen's dtlSet form and table.
 *
 * Every figure shown here (construction, derived price, totals, lead time) is a preview. The
 * server re-reads the costing and recomputes GSM, prices and amounts on save.
 */
(() => {
    'use strict';

    const METRES_PER_YARD = 0.9144;
    const esc = s => App.esc(s);
    const nf = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 });
    const pf = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 });
    const numOrNull = v => (v === '' || v == null || Number.isNaN(Number(v))) ? null : Number(v);
    const strOrNull = v => (v == null || String(v).trim() === '') ? null : String(v).trim();
    const today = () => new Date().toISOString().slice(0, 10);
    const round = (v, dp) => v == null ? null : Math.round(v * 10 ** dp) / 10 ** dp;

    /** Colour-line inputs, in column order. */
    const COLOR_TEXT = ['colorCode', 'colorName', 'fabricsStyle', 'colorReference', 'strikeOffReference',
                        'labDipReference', 'loomReference'];

    /** Fabric types are compared as ColourStructure.key does on the server: lower case, letters and digits only. */
    const typeKey = s => String(s || '').toLowerCase().replace(/[^a-z0-9]/g, '');

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('bookingForm');
        if (!form) return;
        const dialog = document.getElementById('bookingDialog');
        const $ = sel => form.querySelector(sel);
        const $$ = sel => [...form.querySelectorAll(sel)];

        const specEditor = document.getElementById('specEditor');
        const colorBody = document.querySelector('#colorTable tbody');
        const specLines = document.getElementById('specLines');
        const termsBody = document.querySelector('#termsTable tbody');
        const canAmend = !!document.getElementById('canAmendBooking');
        const canCreateOrders = !!document.getElementById('canCreateProductionOrder');
        const fabricTypeSelect = document.getElementById('spFabricType');
        /** Solid-dyed types: one colour per fabric line (ColourStructure.SINGLE). */
        const singleColourTypes = new Set((fabricTypeSelect.dataset.singleColour || '').split('|').filter(Boolean).map(typeKey));
        const isSingleColour = type => singleColourTypes.has(typeKey(type));

        const party = () => App.RemoteSelect.of(document.getElementById('bkParty'));
        const brand = () => App.RemoteSelect.of(document.getElementById('bkBrand'));
        const garments = () => App.RemoteSelect.of(document.getElementById('bkGarments'));
        const priceInMeter = () => document.getElementById('bkPriceInMeter').checked;

        let doc = null;            // the booking being edited; null for a new one
        let groups = [];           // [{ itemId, itemName, fabric: {...}, colorLines: [...] }]
        let terms = [];            // [{ serialNo, bodyText }]
        let editingGroup = -1;     // index into groups, -1 while adding a new specification
        let editingTerm = -1;
        let openGroups = new Set();  // fabric lines whose "Item detail" is open
        let benchmark = {};        // { quoted, breakEven } of the specification in the editor
        let dirty = false;
        let lineDirty = false;     // the fabric-line modal has been typed into since it opened
        const items = new Map();   // item id -> label

        const screen = new App.DocumentScreen(Object.assign({}, window.BOOKING_SCREEN,
            { canEdit: canAmend, onEdit: d => openEditor(d), onView: (d, history) => viewBooking(d, history) }));
        const editorTabs = App.tabs(form.querySelector('[data-editor-tabs]').parentElement);

        // ------------------------------------------------------------------ reference lists

        const optionsReady = Promise.all([
            ...$$('select[data-options]').map(async select => {
                const rows = await App.api('/api/lookup/fabric/' + select.dataset.options).catch(() => []);
                select.innerHTML = '<option value="">—</option>'
                    + rows.map(r => `<option value="${esc(r.text)}">${esc(r.text)}</option>`).join('');
            }),
            App.api('/api/lookup/inventory/items', { query: { itemType: 'FABRICS' } }).catch(() => []).then(rows => {
                rows.forEach(r => items.set(String(r.id), r.text));
                document.getElementById('spItem').innerHTML = '<option value="">Choose the item…</option>'
                    + rows.map(r => `<option value="${esc(r.id)}">${esc(r.text)}</option>`).join('');
            })
        ]);

        /** Selects a value, adding it as an option first when the list does not carry it (a retired entry, say). */
        function setSelect(select, value) {
            const v = value == null ? '' : String(value);
            if (v && ![...select.options].some(o => o.value === v)) {
                select.insertAdjacentHTML('beforeend', `<option value="${esc(v)}">${esc(v)}</option>`);
            }
            select.value = v;
        }

        // ------------------------------------------------------------------ open / close

        document.querySelector('[data-action="booking-new"]')?.addEventListener('click', () => openEditor(null));
        $$('[data-editor-close]').forEach(b => b.addEventListener('click', () => closeEditor()));
        // Esc on the modal asks the same question the Cancel button does.
        dialog.addEventListener('cancel', event => {
            event.preventDefault();
            closeEditor();
        });

        async function openEditor(existing, approvalInfo) {
            if (dialog.open && dirty && !await App.confirm({
                title: 'Discard the booking you are editing?', message: 'Changes not saved yet will be lost.',
                confirmText: 'Discard', danger: true })) return;
            await optionsReady;
            setViewChrome(null);
            doc = existing;
            fillHeader(existing);
            groups = existing ? (existing.lineGroups || []).map(fromDetail) : [];
            terms = existing ? (existing.terms || []).map(t => ({ serialNo: t.serialNo, bodyText: t.bodyText })) : [];
            if (!existing) {
                terms = await App.api('/api/terms/defaults', { query: { type: 'BOOKING' } }).catch(error => { App.fail(error); return []; });
            }
            openGroups = new Set();
            resetSpec();
            resetTerm();
            renderGroups();
            renderTerms();
            editorTabs.select('items');
            form.querySelector('[data-editor-title]').textContent = existing ? `Edit ${existing.documentNo}` : 'New booking';
            form.querySelector('[data-editor-rail]').innerHTML = App.statusSteps(existing ? existing.status : 'DRAFT');
            dirty = false;
            if (!dialog.open) dialog.showModal();
            form.querySelector('[data-editor-body]').scrollTop = 0;
            renderApproval(existing, approvalInfo || null);
            if (existing && !approvalInfo) {
                // Editing a returned or rejected draft: say why it came back.
                Promise.all([
                    App.api(`/api/documents/${existing.id}/history`).catch(() => []),
                    App.api(`/api/documents/${existing.id}/approval`).catch(() => null)
                ]).then(([history, approval]) => { if (doc === existing) renderApproval(existing, { history, approval }); });
            }
        }

        // ------------------------------------------------------------------ approval

        const DECISION = {
            APPROVED: ['Approved', 'text-emerald-700 dark:text-emerald-400', 'check'],
            RETURNED: ['Returned', 'text-amber-700 dark:text-amber-400', 'arrow-right'],
            REJECTED: ['Rejected', 'text-red-700 dark:text-red-400', 'x'],
            SUBMITTED: ['Submitted', 'text-gray-600 dark:text-gray-300', 'send'],
            RESUBMITTED: ['Submitted again', 'text-gray-600 dark:text-gray-300', 'send'],
            CANCELLED: ['Cancelled', 'text-red-700 dark:text-red-400', 'x-circle'],
            SUPERSEDED: ['Superseded by a revision', 'text-gray-600 dark:text-gray-300', 'refresh']
        };

        /**
         * The booking's approval, as asfl-erp's booking shows it: where it stands, who it is waiting
         * for, which matrix (the team's own or the unit's), and the decisions of this round - or, on a
         * draft that came back, the reason it came back. Nothing on a booking never submitted.
         */
        function renderApproval(d, info) {
            const panel = form.querySelector('[data-approval-panel]');
            const history = (info && info.history) || [];
            const approval = info && info.approval;
            // This round: newest first, back to (and including) the latest submission.
            const round = [];
            for (const h of history) {
                round.push(h);
                if (h.action === 'SUBMITTED' || h.action === 'RESUBMITTED') break;
            }
            if (!d || !round.length) {
                panel.hidden = true;
                panel.innerHTML = '';
                return;
            }
            const last = round[0];
            const when = h => App.fmt.timeTag ? App.fmt.timeTag(h.at) : esc(h.at || '');
            const trail = round.map(h => {
                const [label, tone, glyph] = DECISION[h.action] || [h.action, 'text-gray-600', 'send'];
                return `<li class="flex items-start gap-3">
                    <span class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-800 ${tone}">${App.icon(glyph, 'h-3 w-3')}</span>
                    <div class="min-w-0 text-sm">
                        <p><span class="font-semibold ${tone}">${esc(label)}</span>${h.level ? ` <span class="text-gray-500">level ${esc(h.level)}</span>` : ''}
                           <span class="text-gray-500">·</span> <span class="font-medium text-gray-900 dark:text-white">${esc(h.actor || 'System')}</span>
                           <span class="text-xs text-gray-500">${when(h)}</span></p>
                        ${h.remarks ? `<p class="mt-1 rounded-lg bg-gray-50 px-3 py-1.5 text-gray-700 dark:bg-gray-800 dark:text-gray-300">${esc(h.remarks)}</p>` : ''}
                    </div></li>`;
            }).join('');

            let headline;
            if (d.status === 'SUBMITTED' && approval && approval.pending !== false) {
                const levels = approval.totalLevels > 1 ? `Level ${approval.level} of ${approval.totalLevels}` : 'Awaiting approval';
                headline = `<span class="badge-amber badge-dot">${esc(levels)}</span>
                    <span class="text-sm text-gray-600 dark:text-gray-300">${approval.canAct ? 'Waiting for you'
                        : esc(approval.waitingReason || ('Waiting for ' + (approval.approver || 'an approver')))}</span>`;
            } else if (last.action === 'RETURNED' && d.status === 'DRAFT') {
                headline = `<span class="badge-amber badge-dot">Returned for correction</span>
                    <span class="text-sm text-gray-600 dark:text-gray-300">Correct it and submit again - a new approval round starts.</span>`;
            } else if (last.action === 'REJECTED' || d.status === 'REJECTED') {
                headline = `<span class="badge-red badge-dot">Rejected</span>
                    <span class="text-sm text-gray-600 dark:text-gray-300">Edit it to make it a draft again, or cancel it.</span>`;
            } else if (last.action === 'APPROVED' && last.fromStatus !== last.toStatus) {
                headline = '<span class="badge-green badge-dot">Approved</span>';
            } else {
                headline = App.statusBadge(d.status);
            }
            const matrix = approval && (approval.matrixName || approval.scope)
                ? `<span class="ml-auto text-xs text-gray-500">${esc(approval.matrixName || 'No matrix')}${approval.scope ? ' · ' + esc(approval.scope) : ''}</span>` : '';

            panel.innerHTML = `
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span class="inline-flex items-center gap-1.5 text-sm font-semibold text-gray-900 dark:text-white">${App.icon('check-circle', 'h-4 w-4 text-brand-600')}Approval</span>
                    ${headline}${matrix}
                </div>
                <ol class="mt-3 space-y-3">${trail}</ol>`;
            panel.hidden = false;
        }

        /**
         * View: the same form, read-only (App.viewMode) - the header, the fabric-line cards with their
         * Item detail, the terms - plus the document's workflow buttons and its approval history.
         */
        async function viewBooking(d, history) {
            await openEditor(d, { history, approval: screen.approval });
            if (doc !== d) return;                       // the user declined to leave unsaved edits
            setViewChrome(d, history);
            App.viewMode(dialog, true);
        }

        /** Title, footer actions and history tab for a viewed document; null puts the editor's back. */
        function setViewChrome(d, history) {
            const title = form.querySelector('[data-editor-title]');
            const actions = form.querySelector('[data-view-actions]');
            const historyTab = form.querySelector('[data-tab="history"]');
            const cancel = form.querySelector('[data-cancel]');
            if (!d) {
                App.viewMode(dialog, false);
                actions.hidden = true;
                actions.innerHTML = '';
                historyTab.hidden = true;
                cancel.textContent = 'Cancel';
                return;
            }
            title.innerHTML = `${esc(d.documentNo || 'Unnumbered booking')} ${App.statusBadge(d.status)}`;
            // Who signs next, and whether it is you, comes with the approval state (screen.approval).
            // An approved booking goes to production: one draft production order per fabric type.
            const toProduction = canCreateOrders && ['APPROVED', 'PROCESSING', 'PARTIAL'].includes(d.status)
                ? `<button type="button" class="btn-secondary" data-bpo-create>${App.icon('planning')}Create production order</button>` : '';
            actions.innerHTML = toProduction + screen.actionButtons(d);
            actions.hidden = !actions.innerHTML;
            form.querySelector('[data-history]').innerHTML =
                `<h3 class="form-section-title mb-4">Approval history</h3>${screen.historyHtml(history || [])}`;
            historyTab.hidden = false;
            cancel.textContent = 'Close';
        }
        form.querySelector('[data-view-actions]').addEventListener('click', event => {
            const action = event.target.closest('[data-doc-action]')?.dataset.docAction;
            if (action) screen.act(action);
        });
        form.querySelector('[data-view-actions]').addEventListener('click', async event => {
            if (!event.target.closest('[data-bpo-create]') || !screen.current) return;
            const d = screen.current;
            if (!await App.confirm({ title: `Create production orders from ${d.documentNo}?`,
                message: 'One draft production order per fabric type, with every quantity not yet ordered. Review and submit each one.',
                confirmText: 'Create' })) return;
            try {
                const created = await App.api(`/api/bpo/from-booking/${d.id}`, { method: 'POST' });
                App.toast(`Created ${created.map(o => o.documentNo).join(', ')}.`, 'success');
                if (created.length) window.location.href = `/bpo?open=${created[0].id}`;
            } catch (error) {
                App.fail(error);
            }
        });

        async function closeEditor(force) {
            if (!force && dirty && !await App.confirm({
                title: 'Close without saving?', message: 'Changes not saved yet will be lost.',
                confirmText: 'Close', danger: true })) return;
            if (specEditor.open) specEditor.close();
            dialog.close();
            doc = null;
            dirty = false;
        }

        form.addEventListener('input', () => { dirty = true; });
        form.addEventListener('change', () => { dirty = true; });

        // ------------------------------------------------------------------ header

        function fillHeader(d) {
            const f = name => form.elements.namedItem(name);
            document.getElementById('bkDocumentNo').value = d ? d.documentNo : '';
            f('bookingType').value = d?.bookingType || 'BULK';
            f('currencyCode').value = d?.currency || 'USD';
            f('orderType').value = d?.orderType || '';
            f('documentDate').value = d?.documentDate || today();
            f('requiredDate').value = d?.requiredDate || '';
            f('preCostBuyer').value = d?.preCostBuyer || '';
            f('garmentsAddress').value = d?.garmentsAddress || '';
            f('remarks').value = d?.remarks || '';
            document.getElementById('bkPriceInMeter').checked = !!d?.priceInMeter;
            party().setValue(d?.partyId ?? null, d?.partyName);
            brand().setValue(d?.brandId ?? null, d?.brandName);
            garments().setValue(d?.garmentsId ?? null, d?.garmentsName);
            const marketer = document.getElementById('bkMarketingPerson');
            marketer.value = d ? (d.marketingPersonName || '') : (marketer.dataset.me || '');
            fillTeam(d);
            syncPriceBasis();
        }

        document.getElementById('bkPriceInMeter').addEventListener('change', () => {
            syncPriceBasis();
            renderGroups();
        });
        ['bkDocumentDate', 'bkRequiredDate'].forEach(id =>
            document.getElementById(id).addEventListener('change', () => fillLeadTime(false)));

        /**
         * The Marketing team field (ADM-4, ADM-7), read-only. A saved booking shows the team it was
         * raised under; a new one shows the creator's own team, which the server stamps.
         */
        function fillTeam(d) {
            const select = document.getElementById('bkMarketingTeam');
            const option = (id, text) => `<option value="${esc(id ?? '')}">${esc(text)}</option>`;
            select.innerHTML = d
                ? option(d.marketingTeamId, d.marketingTeamName || '—')
                : option(select.dataset.ownId, select.dataset.ownName || 'Your team');
            select.disabled = true;
            select.title = d ? 'A booking keeps the team it was raised under'
                             : 'Every booking you raise is filed under your team';
        }

        /** Yard-priced: the metre price is derived and read-only; metre-priced, the reverse. */
        function syncPriceBasis() {
            const meter = priceInMeter();
            syncColorHeads();
            [...colorBody.rows].forEach(tr => {
                tr.querySelector('[data-col="rate"]').readOnly = meter;
                tr.querySelector('[data-col="priceInMeter"]').readOnly = !meter;
                recalcRow(tr);
            });
        }

        /** Units once, in the header: the currency on each money column, the asterisk on the price that is keyed. */
        function syncColorHeads() {
            const meter = priceInMeter();
            const unit = `<span class="unit">${esc(currency())}</span>`;
            const req = ' <span class="req text-brand-600">*</span>';
            document.querySelector('[data-price-head="yard"]').innerHTML = `Price / yd ${unit}${meter ? '' : req}`;
            document.querySelector('[data-price-head="meter"]').innerHTML = `Price / m ${unit}${meter ? req : ''}`;
            document.querySelector('[data-currency-unit]').textContent = currency();
        }

        // ------------------------------------------------------------------ costing

        const costingInput = document.getElementById('spCostingCode');
        costingInput.addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); fetchCosting(); }
        });
        costingInput.addEventListener('input', syncGsmLock);
        form.querySelector('[data-action="costing-fetch"]').addEventListener('click', fetchCosting);

        async function fetchCosting() {
            const code = costingInput.value.trim();
            if (!code) {
                App.toast('Enter a costing number first.', 'warn');
                costingInput.focus();
                return;
            }
            const button = form.querySelector('[data-action="costing-fetch"]');
            button.disabled = true;
            try {
                const p = await App.api('/api/booking/costing/' + encodeURIComponent(code),
                    { query: { bookingId: doc?.id } });
                applySpec(p.spec, true);
                const entered = readColorRows().filter(hasContent);
                if (p.colours.length && (!entered.length || await App.confirm({
                        title: 'Replace the colour breakdown?',
                        message: `The costing plans ${p.colours.length} colour(s). Replace the ${entered.length} line(s) already entered?`,
                        confirmText: 'Replace' }))) {
                    setColorRows(p.colours.map(c => ({ colorName: c.colorName, quantity: c.quantity, rate: c.rate })));
                }
                if (p.preCostBuyer) form.elements.namedItem('preCostBuyer').value = p.preCostBuyer;
                if (!party().select.value && p.preCostBuyer) suggestBuyer(p.preCostBuyer);
                suggestItem(p.spec.fabricType);
                fillLeadTime(false);
                renderCostingInfo(p);
                dirty = true;
            } catch (error) {
                renderCostingInfo(null);
                App.fail(error);
            } finally {
                button.disabled = false;
            }
        }

        function renderCostingInfo(p) {
            const box = specEditor.querySelector('[data-costing-info]');
            if (!p) { box.hidden = true; box.innerHTML = ''; return; }
            const fact = (label, value) => value == null || value === '' ? ''
                : `<span><span class="text-gray-500">${esc(label)}</span> <span class="font-medium text-gray-900 dark:text-white">${esc(value)}</span></span>`;
            box.innerHTML = `
                <div class="flex flex-wrap gap-x-6 gap-y-1 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm dark:border-gray-700 dark:bg-gray-800/60">
                    ${fact('Costing', p.code)}${fact('Buyer', p.preCostBuyer)}${fact('Costing ref.', p.costingReference)}
                    ${fact('Planned qty', p.costingOrderQty == null ? null : nf.format(p.costingOrderQty))}
                    ${fact('Quoted / yd', p.spec.quotedPrice == null ? null : pf.format(p.spec.quotedPrice))}
                    ${fact('Break-even / yd', p.spec.breakEvenPrice == null ? null : pf.format(p.spec.breakEvenPrice))}
                </div>
                ${p.warnings.map(w => `<p class="alert-warn">${App.icon('alert', 'icon mt-px shrink-0')}<span>${esc(w)}</span></p>`).join('')}
                ${p.note ? `<p class="alert-info">${App.icon('info', 'icon mt-px shrink-0')}<span class="whitespace-pre-line"><span class="font-medium">Costing note:</span> ${esc(p.note.trim())}</span></p>` : ''}`;
            box.hidden = false;
        }

        /** Picks the buyer the costing names, when exactly one customer matches it. */
        async function suggestBuyer(name) {
            const norm = s => String(s || '').toLowerCase().replace(/\blimited\b/g, 'ltd').replace(/[^a-z0-9]/g, '');
            const word = String(name).split(/[^A-Za-z0-9]+/).find(w => w.length >= 3) || name;
            try {
                const page = await App.api('/api/parties/lookup', { query: { role: 'CUSTOMER', q: word, size: 50 } });
                const hits = (page.results || []).filter(o => norm(o.text) === norm(name));
                if (hits.length === 1 && !party().select.value) {
                    party().setValue(hits[0]);
                    App.toast(`Buyer set to ${hits[0].text}, as on the costing.`, 'info');
                }
            } catch (ignored) { /* a suggestion; the user picks the buyer anyway */ }
        }

        /** Chooses the FABRICS item named after the costing's fabric type ("Solid Dyed Print Fabrics (Yard)"). */
        function suggestItem(fabricType) {
            const select = document.getElementById('spItem');
            if (select.value || !fabricType) return;
            const want = fabricType.toLowerCase() + ' fabric';
            const match = [...select.options].find(o => {
                const name = (o.textContent.split('|')[1] || o.textContent).trim().toLowerCase();
                return name.startsWith(want);
            });
            if (match) select.value = match.value;
        }

        // ------------------------------------------------------------------ fabric-line modal

        /** Opens the line editor: empty to add a line, or filled with line `index` to edit it. */
        function openLine(index) {
            if (index >= 0) editGroup(index);
            else resetSpec();
            lineDirty = false;
            if (!specEditor.open) specEditor.showModal();
            specEditor.querySelector('[data-line-body]').scrollTop = 0;
            (index >= 0 ? specEditor.querySelector('[data-spec]:not([readonly])') : costingInput).focus();
        }

        async function closeLine(force) {
            if (!force && lineDirty && specHasContent() && !await App.confirm({
                title: editingGroup >= 0 ? 'Discard the changes to this line?' : 'Discard this fabric line?',
                message: 'What was keyed here has not been added to the booking.',
                confirmText: 'Discard', danger: true })) return;
            resetSpec();
            specEditor.close();
        }

        form.querySelector('[data-action="line-add"]').addEventListener('click', () => openLine(-1));
        specEditor.querySelectorAll('[data-line-close]').forEach(b => b.addEventListener('click', () => closeLine()));
        // Esc asks the same question Cancel does; it does not reach the booking dialog underneath.
        specEditor.addEventListener('cancel', event => {
            event.preventDefault();
            closeLine();
        });
        ['input', 'change'].forEach(type => specEditor.addEventListener(type, () => { lineDirty = true; }));
        // Enter in a line field would submit the whole booking (the fields belong to #bookingForm).
        specEditor.addEventListener('keydown', event => {
            if (event.key === 'Enter' && event.target.tagName === 'INPUT') event.preventDefault();
        });

        // ------------------------------------------------------------------ specification editor

        const specInputs = () => $$('#specEditor [data-spec]');

        /**
         * Fills the editor. From a costing only the values the costing has are written (what the
         * user typed elsewhere survives), except the figures that belong to the costing.
         */
        function applySpec(spec, fromCosting) {
            const owned = ['gsm', 'quotedPrice', 'costingAmendmentNo'];
            specInputs().forEach(el => {
                const key = el.dataset.spec;
                if (!(key in spec)) return;
                const value = spec[key];
                if (fromCosting && (value == null || value === '') && !owned.includes(key)) return;
                const shown = key === 'quotedPrice' && value != null ? pf.format(value) : value;
                if (el.tagName === 'SELECT') setSelect(el, value);
                else el.value = shown == null ? '' : shown;
            });
            benchmark = { quoted: numOrNull(spec.quotedPrice), breakEven: numOrNull(spec.breakEvenPrice),
                          quotedRaw: spec.quotedPrice, breakEvenRaw: spec.breakEvenPrice };
            updateConstruction();
            syncGsmLock();
            [...colorBody.rows].forEach(recalcRow);
            syncColourLimit();
        }

        function readSpec() {
            const spec = {};
            specInputs().forEach(el => {
                const key = el.dataset.spec;
                if (key === 'quotedPrice') return;                  // the costing's, not the form's
                spec[key] = el.type === 'number' ? numOrNull(el.value) : strOrNull(el.value);
            });
            spec.quotedPrice = benchmark.quotedRaw ?? null;
            spec.breakEvenPrice = benchmark.breakEvenRaw ?? null;
            return spec;
        }

        /** Construction is derived, as on the legacy form: warp X weft / EPI X PPI. */
        function updateConstruction() {
            const v = key => strOrNull(specEditor.querySelector(`[data-spec="${key}"]`).value);
            const parts = [v('warpCount1'), v('weftCount1'), v('epi'), v('ppi')];
            specEditor.querySelector('[data-spec="construction"]').value =
                parts.every(Boolean) ? `${parts[0]}X${parts[1]}/${parts[2]}X${parts[3]}` : '';
        }
        $$('#specEditor [data-construction]').forEach(el => el.addEventListener('input', updateConstruction));

        /** GSM comes from the costing when there is one; without one it is typed. */
        function syncGsmLock() {
            const gsm = document.getElementById('spGsm');
            gsm.readOnly = !!costingInput.value.trim();
            gsm.tabIndex = gsm.readOnly ? -1 : 0;
        }

        function fillLeadTime(force) {
            const lead = document.getElementById('spLead');
            if (lead.value && !force) return;
            const from = form.elements.namedItem('documentDate').value;
            const to = form.elements.namedItem('requiredDate').value;
            if (!from || !to) {
                if (force) App.toast('Enter the booking and delivery required dates first.', 'warn');
                return;
            }
            lead.value = Math.max(0, Math.round((Date.parse(to) - Date.parse(from)) / 86400000));
        }
        form.querySelector('[data-action="lead-time"]').addEventListener('click', () => fillLeadTime(true));

        function resetSpec() {
            editingGroup = -1;
            specInputs().forEach(el => { el.value = ''; });
            document.getElementById('spItem').value = '';
            benchmark = {};
            setColorRows([]);
            renderCostingInfo(null);
            updateConstruction();
            syncGsmLock();
            specEditor.querySelector('[data-spec-title]').textContent = 'Add a fabric line';
            specEditor.querySelector('[data-commit-label]').textContent = 'Add to booking';
            if (specLines.querySelector('.is-editing')) renderGroups();
        }
        form.querySelector('[data-action="spec-reset"]').addEventListener('click', resetSpec);

        function specHasContent() {
            return !!costingInput.value.trim() || readColorRows().some(hasContent)
                || !!document.getElementById('spConstruction').value;
        }

        /** Checks the editor the way the legacy form did - required spec fields, a quantity and price on every colour. */
        function validSpec() {
            const missing = $$('#specEditor [required]').find(el => !String(el.value).trim());
            if (missing) {
                const label = form.querySelector(`label[for="${missing.id}"]`)?.textContent.replace('*', '').trim() || 'A required field';
                App.toast(`${label} is required.`, 'warn');
                if (missing.readOnly) specEditor.querySelector('[data-construction]')?.focus();
                else missing.focus();
                return false;
            }
            const lines = readColorRows().filter(hasContent);
            if (!lines.length) {
                App.toast('Add at least one colour to the breakdown.', 'warn');
                return false;
            }
            const meter = priceInMeter();
            const bad = lines.findIndex(l => !(l.quantity > 0) || !((meter ? l.priceInMeter : l.rate) > 0));
            if (bad >= 0) {
                App.toast(`Colour ${bad + 1} needs a quantity and a price.`, 'warn');
                return false;
            }
            return true;
        }

        /**
         * Puts the editor's line on the booking. A single-colour fabric keyed with several colours
         * (a costing's colour plan, say) goes on as one line per colour, same specification, once
         * the user agrees. Resolves with the number of lines written, 0 when nothing was.
         */
        async function commitSpec() {
            if (!validSpec()) return 0;
            const itemSelect = document.getElementById('spItem');
            if (!document.getElementById('spLead').value) fillLeadTime(false);
            const fabric = readSpec();
            const lines = readColorRows().filter(hasContent);
            const split = isSingleColour(fabric.fabricType) && lines.length > 1;
            if (split && !await App.confirm({
                    title: `Split into ${lines.length} fabric lines?`,
                    message: `${fabric.fabricType} is a single-colour fabric, so each colour is booked as a line of its own with this specification.`,
                    confirmText: `Add ${lines.length} lines` })) return 0;
            const made = (split ? lines.map(l => [l]) : [lines]).map(colorLines => ({
                itemId: itemSelect.value ? Number(itemSelect.value) : null,
                itemName: itemSelect.value ? items.get(itemSelect.value) : null,
                fabric: Object.assign({}, fabric),
                colorLines
            }));
            if (editingGroup >= 0) {
                // Lines after the one edited move down; keep their "Item detail" open.
                const at = editingGroup;
                const shift = made.length - 1;
                openGroups = new Set([...openGroups].map(k => k > at ? k + shift : k));
                groups.splice(at, 1, ...made);
            } else {
                groups.push(...made);
            }
            dirty = true;
            resetSpec();
            renderGroups();
            return made.length;
        }
        form.querySelector('[data-action="spec-commit"]').addEventListener('click', async () => {
            const updating = editingGroup >= 0;
            const count = await commitSpec();
            if (count) {
                specEditor.close();
                App.toast(count > 1 ? `Added as ${count} fabric lines, one per colour.`
                    : updating ? 'Fabric line updated.' : 'Fabric line added to the booking.', 'success');
                specLines.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        });

        function editGroup(index) {
            const g = groups[index];
            resetSpec();
            editingGroup = index;
            applySpec(g.fabric, false);
            setSelect(document.getElementById('spItem'), g.itemId == null ? '' : String(g.itemId));
            setColorRows(g.colorLines);
            specEditor.querySelector('[data-spec-title]').textContent = `Edit fabric line ${index + 1}`;
            specEditor.querySelector('[data-commit-label]').textContent = 'Update line';
            renderGroups();
        }

        // ------------------------------------------------------------------ colour breakdown

        function colorRow(line) {
            const meter = priceInMeter();
            const text = key => `<td><input class="field" data-col="${key}" value="${esc(line[key] ?? '')}" maxlength="120"></td>`;
            return `<tr>
                ${COLOR_TEXT.map(text).join('')}
                <td><input type="number" step="any" min="0" class="field text-right tabular-nums" data-col="quantity" value="${esc(line.quantity ?? '')}"></td>
                <td><input type="number" step="any" min="0" class="field text-right tabular-nums" data-col="rate" value="${esc(line.rate ?? '')}"${meter ? ' readonly tabindex="-1"' : ''}></td>
                <td><input type="number" step="any" min="0" class="field text-right tabular-nums" data-col="priceInMeter" value="${esc(line.priceInMeter ?? '')}"${meter ? '' : ' readonly tabindex="-1"'}></td>
                <td class="text-right tabular-nums" data-col="total">—</td>
                <td><input class="field" data-col="remarks" value="${esc(line.remarks ?? '')}" maxlength="500"></td>
                <td><button type="button" class="btn-icon btn-sm" data-color-remove aria-label="Remove colour">${App.icon('trash')}</button></td>
            </tr>`;
        }

        function setColorRows(lines) {
            colorBody.innerHTML = (lines || []).map(colorRow).join('');
            [...colorBody.rows].forEach(recalcRow);
            syncColorEmpty();
            updateColorTotals();
        }

        function syncColorEmpty() {
            document.querySelector('[data-color-empty]').hidden = colorBody.rows.length > 0;
            syncColourLimit();
        }

        /** A single-colour fabric type takes one colour: "Add colour" stops at one, and says why. */
        function syncColourLimit() {
            const type = fabricTypeSelect.value;
            const single = isSingleColour(type);
            const rows = colorBody.rows.length;
            const add = form.querySelector('[data-action="color-add"]');
            add.disabled = single && rows >= 1;
            add.title = add.disabled ? `${type} takes one colour per fabric line` : '';
            const note = document.querySelector('[data-color-single]');
            note.hidden = !single;
            note.textContent = !single ? ''
                : rows > 1 ? `${type} takes one colour per line — these ${rows} colours will be added as ${rows} fabric lines.`
                : `${type} is a single-colour fabric: one colour per line. Book each further colour as a line of its own.`;
        }
        fabricTypeSelect.addEventListener('change', syncColourLimit);

        function readColorRows() {
            return [...colorBody.rows].map(tr => {
                const line = {};
                COLOR_TEXT.concat('remarks').forEach(k => { line[k] = strOrNull(tr.querySelector(`[data-col="${k}"]`).value); });
                ['quantity', 'rate', 'priceInMeter'].forEach(k => { line[k] = numOrNull(tr.querySelector(`[data-col="${k}"]`).value); });
                return line;
            });
        }

        const hasContent = l => Object.values(l).some(v => v != null && v !== '');

        /** Derives the other price unit and the total, and flags a price under the costing's figures. */
        function recalcRow(tr) {
            const q = numOrNull(tr.querySelector('[data-col="quantity"]').value);
            const yd = tr.querySelector('[data-col="rate"]');
            const m = tr.querySelector('[data-col="priceInMeter"]');
            let perYard;
            let total = null;
            if (priceInMeter()) {
                const perMetre = numOrNull(m.value);
                perYard = perMetre == null ? null : round(perMetre * METRES_PER_YARD, 6);
                yd.value = perYard == null ? '' : perYard;
                total = q != null && perMetre != null ? q * perMetre : null;
            } else {
                perYard = numOrNull(yd.value);
                m.value = perYard == null ? '' : round(perYard / METRES_PER_YARD, 4);
                total = q != null && perYard != null ? q * perYard : null;
            }
            tr.querySelector('[data-col="total"]').textContent = total == null ? '—' : nf.format(total);
            updateColorTotals();

            yd.classList.remove('text-red-700', 'text-amber-700');
            yd.title = '';
            if (perYard != null && benchmark.breakEven != null && perYard < benchmark.breakEven) {
                yd.classList.add('text-red-700');
                yd.title = `Below break-even (${pf.format(benchmark.breakEven)} / yd)`;
            } else if (perYard != null && benchmark.quoted != null && perYard < benchmark.quoted) {
                yd.classList.add('text-amber-700');
                yd.title = `Below the quoted price (${pf.format(benchmark.quoted)} / yd)`;
            }
        }

        function updateColorTotals() {
            let qty = 0;
            let amount = 0;
            readColorRows().forEach(l => { qty += l.quantity || 0; amount += lineAmount(l); });
            document.querySelector('[data-color-total="qty"]').textContent = nf.format(qty);
            document.querySelector('[data-color-total="amount"]').textContent = nf.format(amount);
        }

        colorBody.addEventListener('input', event => {
            const tr = event.target.closest('tr');
            if (tr) recalcRow(tr);
        });
        colorBody.addEventListener('click', event => {
            if (!event.target.closest('[data-color-remove]')) return;
            event.target.closest('tr').remove();
            syncColorEmpty();
            updateColorTotals();
            dirty = true;
        });
        form.querySelector('[data-action="color-add"]').addEventListener('click', () => {
            colorBody.insertAdjacentHTML('beforeend', colorRow({ rate: benchmark.quoted ?? '' }));
            const tr = colorBody.rows[colorBody.rows.length - 1];
            recalcRow(tr);
            syncColorEmpty();
            tr.querySelector('input').focus();
        });

        // ------------------------------------------------------------------ specifications on the booking

        function fromDetail(g) {
            const fabric = Object.assign({}, g);
            ['id', 'groupNo', 'itemId', 'itemName', 'groupQuantity', 'groupAmount', 'colorLines'].forEach(k => delete fabric[k]);
            return {
                itemId: g.itemId, itemName: g.itemName, fabric,
                colorLines: (g.colorLines || []).map(l => {
                    const line = {};
                    COLOR_TEXT.concat('remarks', 'quantity', 'rate', 'priceInMeter').forEach(k => { line[k] = l[k]; });
                    return line;
                })
            };
        }

        function lineAmount(l) {
            const price = priceInMeter() ? l.priceInMeter : l.rate;
            return (l.quantity || 0) * (price || 0);
        }

        const currency = () => form.elements.namedItem('currencyCode').value;
        const filled = v => v != null && String(v).trim() !== '';
        const joined = (...parts) => parts.filter(filled).join(' · ');
        const itemLabel = g => g.itemName ? (g.itemName.split('|')[1] || g.itemName).trim() : '';
        const widthOf = f => filled(f.finishWidth) || filled(f.cuttableWidth)
            ? `${f.finishWidth ?? '—'}″ finished · ${f.cuttableWidth ?? '—'}″ cuttable` : '';

        /** Label/value rows; a value the specification does not have shows as a dash. */
        const dlRows = pairs => pairs.map(([label, value, cls]) =>
            `<dt>${esc(label)}</dt><dd class="${cls || ''}">${filled(value) ? esc(value) : '—'}</dd>`).join('');

        /**
         * One fabric line as asfl-erp draws it: title and meta line, the identifying facts as a
         * strip, the full specification behind "Item detail", then the colour breakdown.
         */
        function groupCard(g, i, meter, cur) {
            const f = g.fabric;
            const open = openGroups.has(i);
            const item = itemLabel(g);
            const meta = [f.fabricType, f.fabricSource, f.costingCode && `Costing ${f.costingCode}`,
                          filled(f.leadTimeDays) && `Lead time ${f.leadTimeDays} days`,
                          f.qualityReference && `Quality ref ${f.qualityReference}`].filter(Boolean);
            const strip = [['Composition', f.composition], ['Weave', joined(f.weaveType, f.weaveStyle)],
                           ['EPI × PPI', filled(f.epi) && filled(f.ppi) ? `${f.epi} × ${f.ppi}` : ''],
                           ['Width', widthOf(f)], ['GSM', f.gsm]].filter(([, v]) => filled(v));
            const qty = g.colorLines.reduce((t, l) => t + (l.quantity || 0), 0);
            const amount = g.colorLines.reduce((t, l) => t + lineAmount(l), 0);

            return `<article class="line-card${editingGroup === i ? ' is-editing' : ''}">
                <header class="line-head">
                    <span class="line-no">${i + 1}</span>
                    <div class="min-w-0 flex-1">
                        <div class="flex min-w-0 items-baseline gap-2">
                            <h4 class="line-title font-mono">${esc(f.construction || item || `Fabric line ${i + 1}`)}</h4>
                            ${f.construction && item ? `<span class="line-sub" title="${esc(item)}">${esc(item)}</span>` : ''}
                        </div>
                        ${meta.length ? `<p class="line-meta">${meta.map(m => `<span>${esc(m)}</span>`).join('')}</p>` : ''}
                    </div>
                    <div class="flex shrink-0 items-center gap-1.5">
                        <button type="button" class="line-toggle" data-group-detail="${i}" data-view-keep aria-expanded="${open}"
                                title="The full specification of this line">${App.icon('chevron-right', 'transition-transform')}Item detail</button>
                        <button type="button" class="btn-subtle btn-sm" data-group-edit="${i}" title="Edit this line">${App.icon('edit')}Edit</button>
                        <button type="button" class="btn-icon btn-sm hover:text-red-700" data-group-remove="${i}"
                                aria-label="Remove line ${i + 1}" title="Remove this line">${App.icon('trash')}</button>
                    </div>
                </header>
                ${strip.length ? `<dl class="line-strip">${strip.map(([k, v]) =>
                    `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>` : ''}
                ${open ? specPanel(g) : ''}
                <div class="table-wrap">
                    <table class="line-colours">
                        <thead><tr>
                            <th>Colour</th><th>Fabrics style</th><th>References</th>
                            <th class="num w-32">Quantity</th>
                            <th class="num w-36">${meter ? 'Price / m' : 'Price / yd'} <span class="unit">${esc(cur)}</span></th>
                            <th class="num w-40">Amount <span class="unit">${esc(cur)}</span></th>
                        </tr></thead>
                        <tbody>${g.colorLines.map(l => `<tr>
                            <td class="font-medium">${esc(joined(l.colorCode, l.colorName) || '—')}</td>
                            <td class="text-gray-600 dark:text-gray-400">${esc(l.fabricsStyle || '—')}</td>
                            <td class="text-xs text-gray-500 dark:text-gray-400" title="${esc(l.remarks || '')}">${esc(joined(
                                l.colorReference && `Colour ${l.colorReference}`, l.labDipReference && `Lab dip ${l.labDipReference}`,
                                l.strikeOffReference && `Strike off ${l.strikeOffReference}`, l.loomReference && `Loom ${l.loomReference}`) || '—')}</td>
                            <td class="num">${nf.format(l.quantity || 0)}</td>
                            <td class="num">${pf.format((meter ? l.priceInMeter : l.rate) || 0)}</td>
                            <td class="num">${nf.format(lineAmount(l))}</td>
                        </tr>`).join('')}</tbody>
                        <tfoot><tr>
                            <td colspan="3">${g.colorLines.length} ${g.colorLines.length === 1 ? 'colour' : 'colours'}</td>
                            <td class="num">${nf.format(qty)}</td>
                            <td></td>
                            <td class="num">${nf.format(amount)}</td>
                        </tr></tfoot>
                    </table>
                </div>
            </article>`;
        }

        /** "Item detail": the whole specification, read-only - three label/value columns, then what the buyer stated. */
        function specPanel(g) {
            const f = g.fabric;
            const yarns = ['warp', 'weft'].flatMap(dir => [1, 2, 3].map(n => ({
                dir: dir === 'warp' ? 'Warp' : 'Weft', n,
                count: f[`${dir}Count${n}`], ratio: f[`${dir}CountRatio${n}`] }))).filter(y => filled(y.count));
            const facts = [
                ['Style', f.styleReference], ['Swatch No', f.swatchNo], ['DISPO reference', f.dispoReference],
                ['LC tenure', f.lcTenure], ['LC payment type', f.lcPaymentType],
                ['Wash type', f.washType], ['End use', f.endUse],
                ['Warp shrinkage', filled(f.shrinkageWarp) ? `${f.shrinkageWarp} %` : ''],
                ['Weft shrinkage', filled(f.shrinkageWeft) ? `${f.shrinkageWeft} %` : ''],
                ['Mech. shrinkage', filled(f.shrinkageMechanical) ? `${f.shrinkageMechanical} %` : ''],
                ['GSM before / after wash', filled(f.gsmBeforeWash) || filled(f.gsmAfterWash) ? `${f.gsmBeforeWash ?? '—'} / ${f.gsmAfterWash ?? '—'}` : ''],
                ['Light source', joined(f.lightSource, f.lightSourceType)], ['Base material', f.baseMaterial],
                ['Wash instruction', f.washInstruction, true], ['Target quality parameter', f.targetQualityParameter, true],
                ['Item description', f.itemDescription, true]
            ].filter(([, v]) => filled(v));

            return `<section class="spec-panel">
                <div class="grid gap-x-10 gap-y-5 md:grid-cols-2 xl:grid-cols-3">
                    <dl class="spec-dl"><div class="spec-group">Fabric</div>${dlRows([
                        ['Costing No', f.costingCode, 'font-mono'], ['Amendment No', f.costingAmendmentNo],
                        ['Item', itemLabel(g)], ['Fabrics type', f.fabricType], ['In-house / Export', f.fabricSource],
                        ['Finish type', joined(f.finishType, f.finishTypeRef)],
                        ['Quoted / break-even', filled(f.quotedPrice) || filled(f.breakEvenPrice)
                            ? `${filled(f.quotedPrice) ? pf.format(f.quotedPrice) : '—'} / ${filled(f.breakEvenPrice) ? pf.format(f.breakEvenPrice) : '—'} per yd` : '']])}</dl>
                    <dl class="spec-dl"><div class="spec-group">Construction</div>${dlRows([
                        ['Construction', f.construction, 'font-mono'], ['PI construction', f.declaredConstruction, 'font-mono'],
                        ['Composition', f.composition], ['PI composition', f.declaredComposition],
                        ['Weave', joined(f.weaveType, f.weaveStyle)],
                        ['EPI × PPI', filled(f.epi) && filled(f.ppi) ? `${f.epi} × ${f.ppi}` : ''],
                        ['Width', widthOf(f)], ['Calculated GSM', f.gsm]])}</dl>
                    <div class="md:col-span-2 xl:col-span-1">
                        <div class="spec-group">Yarns</div>
                        ${yarns.length ? `<table class="w-full text-[13px]">
                            <thead><tr class="text-left text-[11px] text-gray-500 dark:text-gray-400">
                                <th class="pb-1 font-semibold"></th><th class="pb-1 font-semibold">Count</th><th class="pb-1 text-right font-semibold">Ratio</th></tr></thead>
                            <tbody>${yarns.map(y => `<tr class="border-t border-dashed border-gray-200 dark:border-gray-700">
                                <td class="py-1.5 text-gray-500 dark:text-gray-400">${y.dir} ${y.n}</td>
                                <td class="py-1.5 font-mono">${esc(y.count)}</td>
                                <td class="py-1.5 text-right tabular-nums">${esc(y.ratio ?? '—')}</td></tr>`).join('')}</tbody>
                        </table>` : '<p class="text-xs text-gray-500">No yarn counts recorded.</p>'}
                        ${filled(f.warpYarnName) || filled(f.weftYarnName) ? `<dl class="spec-dl mt-3">${dlRows([
                            ['Warp yarn', f.warpYarnName], ['Weft yarn', f.weftYarnName]])}</dl>` : ''}
                    </div>
                </div>
                <div class="mt-6 border-t border-gray-200 pt-4 dark:border-gray-800">
                    <h5 class="mb-3 text-[13px] font-semibold text-gray-900 dark:text-white">Keyed on this order
                        <span class="ml-1 text-xs font-normal text-gray-500">what the buyer will test this cloth against</span></h5>
                    ${facts.length ? `<dl class="spec-facts">${facts.map(([k, v, wide]) =>
                        `<div class="${wide ? 'is-wide' : ''}"><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>`
                        : '<p class="text-xs text-gray-500">The buyer has not stated a specification for this line.</p>'}
                </div>
            </section>`;
        }

        function renderGroups() {
            const meter = priceInMeter();
            const cur = currency();
            specLines.innerHTML = groups.length
                ? groups.map((g, i) => groupCard(g, i, meter, cur)).join('')
                : `<div class="rounded-xl border border-dashed border-gray-300 px-4 py-8 text-center text-sm text-gray-500 dark:border-gray-700">
                       No fabric lines yet. Choose “Add fabric line” to key one, or fetch it from a costing.</div>`;

            form.querySelector('[data-count="groups"]').textContent = groups.length;
            const colours = groups.reduce((s, g) => s + g.colorLines.length, 0);
            const qty = groups.reduce((s, g) => s + g.colorLines.reduce((t, l) => t + (l.quantity || 0), 0), 0);
            const amount = groups.reduce((s, g) => s + g.colorLines.reduce((t, l) => t + lineAmount(l), 0), 0);
            form.querySelector('[data-total="lines"]').textContent = groups.length;
            form.querySelector('[data-total="colours"]').textContent = colours;
            form.querySelector('[data-total="qty"]').textContent = nf.format(qty);
            form.querySelector('[data-total="amount"]').textContent = `${cur} ${nf.format(amount)}`;
            syncColorHeads();
        }
        form.elements.namedItem('currencyCode').addEventListener('change', renderGroups);

        specLines.addEventListener('click', async event => {
            const detail = event.target.closest('[data-group-detail]');
            if (detail) {
                const i = Number(detail.dataset.groupDetail);
                if (!openGroups.delete(i)) openGroups.add(i);
                renderGroups();
                return;
            }
            const edit = event.target.closest('[data-group-edit]');
            if (edit) {
                openLine(Number(edit.dataset.groupEdit));
                return;
            }
            const remove = event.target.closest('[data-group-remove]');
            if (remove) {
                const i = Number(remove.dataset.groupRemove);
                if (!await App.confirm({ title: `Remove fabric line ${i + 1}?`,
                        message: 'Its colour breakdown is removed with it.', confirmText: 'Remove', danger: true })) return;
                groups.splice(i, 1);
                openGroups = new Set([...openGroups].filter(k => k !== i).map(k => k > i ? k - 1 : k));
                if (editingGroup === i) resetSpec();
                else if (editingGroup > i) editingGroup--;
                dirty = true;
                renderGroups();
            }
        });

        // ------------------------------------------------------------------ terms & conditions

        const tcBody = document.getElementById('tcBody');
        const tcSerial = document.getElementById('tcSerial');

        function renderTerms() {
            terms.sort((a, b) => (a.serialNo ?? 0) - (b.serialNo ?? 0));
            termsBody.innerHTML = terms.length ? terms.map((t, i) => `<tr>
                    <td class="tabular-nums">${esc(t.serialNo)}</td>
                    <td class="whitespace-pre-line">${esc(t.bodyText)}</td>
                    <td>${App.rowActions(App.rowButton('Edit', 'edit', `data-term-edit="${i}"`),
                                         App.rowButton('Delete', 'trash', `data-term-remove="${i}"`, 'text-red-700'))}</td>
                </tr>`).join('')
                : '<tr><td colspan="3" class="py-6 text-center text-sm text-gray-500">No terms on this booking.</td></tr>';
            form.querySelector('[data-count="terms"]').textContent = terms.length;
            if (editingTerm < 0) tcSerial.value = nextSerial();
        }

        const nextSerial = () => terms.reduce((m, t) => Math.max(m, t.serialNo || 0), 0) + 1;

        function resetTerm() {
            editingTerm = -1;
            tcBody.value = '';
            tcSerial.value = nextSerial();
            form.querySelector('[data-term-label]').textContent = 'Add';
            form.querySelector('[data-action="term-cancel"]').hidden = true;
        }

        form.querySelector('[data-action="term-commit"]').addEventListener('click', () => {
            const bodyText = tcBody.value.trim();
            const serialNo = Math.round(numOrNull(tcSerial.value) || 0);
            if (!bodyText) { App.toast('Write the clause first.', 'warn'); tcBody.focus(); return; }
            if (serialNo < 1) { App.toast('Give it a serial number of 1 or more.', 'warn'); tcSerial.focus(); return; }
            if (editingTerm >= 0) terms[editingTerm] = { serialNo, bodyText };
            else terms.push({ serialNo, bodyText });
            dirty = true;
            resetTerm();
            renderTerms();
        });
        form.querySelector('[data-action="term-cancel"]').addEventListener('click', () => { resetTerm(); renderTerms(); });

        termsBody.addEventListener('click', async event => {
            const edit = event.target.closest('[data-term-edit]');
            if (edit) {
                editingTerm = Number(edit.dataset.termEdit);
                tcBody.value = terms[editingTerm].bodyText;
                tcSerial.value = terms[editingTerm].serialNo;
                form.querySelector('[data-term-label]').textContent = 'Update';
                form.querySelector('[data-action="term-cancel"]').hidden = false;
                tcBody.focus();
                return;
            }
            const remove = event.target.closest('[data-term-remove]');
            if (remove) {
                terms.splice(Number(remove.dataset.termRemove), 1);
                dirty = true;
                resetTerm();
                renderTerms();
            }
        });

        form.querySelector('[data-action="terms-defaults"]').addEventListener('click', async () => {
            if (terms.length && !await App.confirm({ title: 'Replace these terms with the defaults?',
                    message: 'Clauses you added or edited on this booking are replaced.', confirmText: 'Replace' })) return;
            try {
                terms = await App.api('/api/terms/defaults', { query: { type: 'BOOKING' } });
                dirty = true;
                resetTerm();
                renderTerms();
            } catch (error) { App.fail(error); }
        });

        form.querySelector('[data-action="terms-library"]').addEventListener('click', async () => {
            let library;
            try { library = await App.api('/api/terms/library', { query: { type: 'BOOKING' } }); }
            catch (error) { App.fail(error); return; }
            const unused = library.filter(c => !terms.some(t => t.bodyText === c.bodyText));
            if (!unused.length) {
                App.toast(library.length ? 'Every standard clause is already on this booking.'
                    : 'No standard booking clauses yet - add them under Master data › Terms & conditions.', 'info');
                return;
            }
            const picked = await App.form({
                title: 'Add a standard clause', confirmText: 'Add',
                fields: [{ name: 'id', label: 'Clause', type: 'select', required: true,
                           options: unused.map(c => ({ value: String(c.id), label: `${c.caption} — ${c.bodyText}`.slice(0, 140) })) }]
            });
            if (!picked) return;
            const clause = unused.find(c => String(c.id) === picked.id);
            terms.push({ serialNo: nextSerial(), bodyText: clause.bodyText });
            dirty = true;
            resetTerm();
            renderTerms();
        });

        // ------------------------------------------------------------------ save

        form.addEventListener('submit', async event => {
            event.preventDefault();
            const andSubmit = !!event.submitter?.hasAttribute('data-submit-for-approval');
            const header = $$('[data-editor-body] > .form-section:first-of-type [required]').find(el => !String(el.value).trim());
            if (header) {
                const label = form.querySelector(`label[for="${header.id}"]`)?.textContent.replace('*', '').trim() || 'A required field';
                App.toast(`${label} is required.`, 'warn');
                (App.RemoteSelect.of && header._remote ? header._remote.trigger : header).focus();
                return;
            }
            if (specHasContent()) {
                if (!await App.confirm({ title: 'Add the specification in the editor?',
                        message: 'It has not been added to the booking yet. Add it and save, or cancel and clear it.',
                        confirmText: 'Add and save' })) return;
                editorTabs.select('items');
                if (!await commitSpec()) return;
            }
            const f = name => form.elements.namedItem(name).value;
            const idOrNull = rs => rs.select.value ? { id: Number(rs.select.value) } : null;
            const body = {
                id: doc?.id ?? null,
                bookingType: f('bookingType'),
                orderType: f('orderType') || null,
                currencyCode: f('currencyCode'),
                documentDate: f('documentDate'),
                requiredDate: f('requiredDate'),
                party: idOrNull(party()),
                brand: idOrNull(brand()),
                garments: idOrNull(garments()),
                preCostBuyer: strOrNull(f('preCostBuyer')),
                garmentsAddress: strOrNull(f('garmentsAddress')),
                remarks: strOrNull(f('remarks')),
                priceInMeter: priceInMeter(),
                lineGroups: groups.map(g => ({
                    item: g.itemId ? { id: g.itemId } : null,
                    fabric: g.fabric,
                    colorLines: g.colorLines
                })),
                terms: terms.map(t => ({ serialNo: t.serialNo, bodyText: t.bodyText }))
            };
            if (andSubmit && !await App.confirm({
                    title: `Submit ${doc?.documentNo || 'this booking'} for approval?`,
                    message: 'It is saved, checked for completeness and sent to the approvers of its team. It cannot be edited while it is being decided.',
                    confirmText: 'Save & submit' })) return;
            const buttons = [...form.querySelectorAll('[type="submit"]')];
            buttons.forEach(b => { b.disabled = true; });
            try {
                const saved = await App.api('/api/booking', { method: 'POST', body });
                if (andSubmit) {
                    try {
                        await App.api(`/api/documents/${saved.id}/submit`, { method: 'POST' });
                        App.toast(`${saved.documentNo} submitted for approval.`, 'success');
                    } catch (error) {
                        // Saved, not submitted: it stays a draft its maker can correct.
                        App.toast(`${saved.documentNo} was saved as a draft but not submitted: ${error.message || 'refused'}`, 'warn');
                    }
                } else {
                    App.toast(`${saved.documentNo} saved as a draft.`, 'success');
                }
                await closeEditor(true);
                screen.grid.reload();
                screen.open(saved.id);
            } catch (error) {
                App.fail(error);
            } finally {
                buttons.forEach(b => { b.disabled = false; });
            }
        });
    });
})();
