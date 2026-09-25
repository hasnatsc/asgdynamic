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

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.getElementById('bookingForm');
        if (!form) return;
        const dialog = document.getElementById('bookingDialog');
        const $ = sel => form.querySelector(sel);
        const $$ = sel => [...form.querySelectorAll(sel)];

        const specEditor = document.getElementById('specEditor');
        const colorBody = document.querySelector('#colorTable tbody');
        const specBody = document.querySelector('#specTable tbody');
        const termsBody = document.querySelector('#termsTable tbody');
        const canAmend = !!document.getElementById('canAmendBooking');

        const party = () => App.RemoteSelect.of(document.getElementById('bkParty'));
        const brand = () => App.RemoteSelect.of(document.getElementById('bkBrand'));
        const garments = () => App.RemoteSelect.of(document.getElementById('bkGarments'));
        const marketer = () => App.RemoteSelect.of(document.getElementById('bkMarketingPerson'));
        const priceInMeter = () => document.getElementById('bkPriceInMeter').checked;

        let doc = null;            // the booking being edited; null for a new one
        let groups = [];           // [{ itemId, itemName, fabric: {...}, colorLines: [...] }]
        let terms = [];            // [{ serialNo, bodyText }]
        let editingGroup = -1;     // index into groups, -1 while adding a new specification
        let editingTerm = -1;
        let benchmark = {};        // { quoted, breakEven } of the specification in the editor
        let dirty = false;
        const items = new Map();   // item id -> label

        const screen = new App.DocumentScreen(Object.assign({}, window.BOOKING_SCREEN,
            { canEdit: canAmend, onEdit: d => openEditor(d) }));
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

        async function openEditor(existing) {
            if (dialog.open && dirty && !await App.confirm({
                title: 'Discard the booking you are editing?', message: 'Changes not saved yet will be lost.',
                confirmText: 'Discard', danger: true })) return;
            await optionsReady;
            doc = existing;
            fillHeader(existing);
            groups = existing ? (existing.lineGroups || []).map(fromDetail) : [];
            terms = existing ? (existing.terms || []).map(t => ({ serialNo: t.serialNo, bodyText: t.bodyText })) : [];
            if (!existing) {
                terms = await App.api('/api/terms/defaults', { query: { type: 'BOOKING' } }).catch(error => { App.fail(error); return []; });
            }
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
        }

        async function closeEditor(force) {
            if (!force && dirty && !await App.confirm({
                title: 'Close without saving?', message: 'Changes not saved yet will be lost.',
                confirmText: 'Close', danger: true })) return;
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
            marketer().setValue(d?.marketingPersonId ?? null, d?.marketingPersonName);
            syncPriceBasis();
        }

        document.getElementById('bkPriceInMeter').addEventListener('change', () => {
            syncPriceBasis();
            renderGroups();
        });
        ['bkDocumentDate', 'bkRequiredDate'].forEach(id =>
            document.getElementById(id).addEventListener('change', () => fillLeadTime(false)));

        /** Yard-priced: the metre price is derived and read-only; metre-priced, the reverse. */
        function syncPriceBasis() {
            const meter = priceInMeter();
            document.querySelector('[data-price-head="yard"]').innerHTML = 'Price / yd' + (meter ? '' : ' <span class="req">*</span>');
            document.querySelector('[data-price-head="meter"]').innerHTML = 'Price / m' + (meter ? ' <span class="req">*</span>' : '');
            [...colorBody.rows].forEach(tr => {
                tr.querySelector('[data-col="rate"]').readOnly = meter;
                tr.querySelector('[data-col="priceInMeter"]').readOnly = !meter;
                recalcRow(tr);
            });
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
            specEditor.querySelector('[data-spec-title]').textContent = 'Add a fabric specification';
            specEditor.querySelector('[data-commit-label]').textContent = 'Add to booking';
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

        function commitSpec() {
            if (!validSpec()) return false;
            const itemSelect = document.getElementById('spItem');
            if (!document.getElementById('spLead').value) fillLeadTime(false);
            const group = {
                itemId: itemSelect.value ? Number(itemSelect.value) : null,
                itemName: itemSelect.value ? items.get(itemSelect.value) : null,
                fabric: readSpec(),
                colorLines: readColorRows().filter(hasContent)
            };
            if (editingGroup >= 0) groups[editingGroup] = group;
            else groups.push(group);
            dirty = true;
            resetSpec();
            renderGroups();
            return true;
        }
        form.querySelector('[data-action="spec-commit"]').addEventListener('click', () => {
            const updating = editingGroup >= 0;
            if (commitSpec()) {
                App.toast(updating ? 'Specification updated.' : 'Specification added to the booking.', 'success');
                document.getElementById('specTable').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        });

        function editGroup(index) {
            const g = groups[index];
            resetSpec();
            editingGroup = index;
            applySpec(g.fabric, false);
            setSelect(document.getElementById('spItem'), g.itemId == null ? '' : String(g.itemId));
            setColorRows(g.colorLines);
            specEditor.querySelector('[data-spec-title]').textContent = `Edit specification ${index + 1}`;
            specEditor.querySelector('[data-commit-label]').textContent = 'Update specification';
            specEditor.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
        }

        function syncColorEmpty() {
            document.querySelector('[data-color-empty]').hidden = colorBody.rows.length > 0;
        }

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

        colorBody.addEventListener('input', event => {
            const tr = event.target.closest('tr');
            if (tr) recalcRow(tr);
        });
        colorBody.addEventListener('click', event => {
            if (!event.target.closest('[data-color-remove]')) return;
            event.target.closest('tr').remove();
            syncColorEmpty();
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

        function renderGroups() {
            const meter = priceInMeter();
            specBody.innerHTML = groups.length ? groups.map((g, i) => {
                const f = g.fabric;
                const colours = `<table class="w-full text-xs"><thead><tr class="text-gray-500">
                        <th class="py-1 pr-2 text-left font-medium">Colour</th><th class="py-1 pr-2 text-left font-medium">Style</th>
                        <th class="py-1 pr-2 text-left font-medium">Lab dip</th>
                        <th class="py-1 pr-2 text-right font-medium">Qty</th><th class="py-1 pr-2 text-right font-medium">${meter ? 'Price / m' : 'Price / yd'}</th>
                        <th class="py-1 text-right font-medium">Total</th></tr></thead><tbody>
                    ${g.colorLines.map(l => `<tr>
                        <td class="py-0.5 pr-2">${esc([l.colorCode, l.colorName].filter(Boolean).join(' · ') || '—')}</td>
                        <td class="py-0.5 pr-2">${esc(l.fabricsStyle || '')}</td>
                        <td class="py-0.5 pr-2">${esc(l.labDipReference || '')}</td>
                        <td class="py-0.5 pr-2 text-right tabular-nums">${nf.format(l.quantity || 0)}</td>
                        <td class="py-0.5 pr-2 text-right tabular-nums">${pf.format((meter ? l.priceInMeter : l.rate) || 0)}</td>
                        <td class="py-0.5 text-right tabular-nums">${nf.format(lineAmount(l))}</td></tr>`).join('')}
                    </tbody></table>`;
                return `<tr>
                    <td class="tabular-nums">${i + 1}</td>
                    <td><div class="font-medium text-gray-900 dark:text-white">${esc(g.itemName ? (g.itemName.split('|')[1] || g.itemName).trim() : '—')}</div>
                        ${f.costingCode ? `<div class="font-mono text-xs text-gray-500">Costing ${esc(f.costingCode)}</div>` : ''}</td>
                    <td class="font-mono text-xs">${esc(f.construction || '—')}</td>
                    <td>${esc(f.composition || '—')}</td>
                    <td>${esc([f.weaveType, f.weaveStyle].filter(Boolean).join(' · ') || '—')}</td>
                    <td class="text-right tabular-nums">${esc(f.finishWidth ?? '—')} / ${esc(f.cuttableWidth ?? '—')}</td>
                    <td class="min-w-[22rem]">${colours}</td>
                    <td>${App.rowActions(App.rowButton('Edit', 'edit', `data-group-edit="${i}"`),
                                         App.rowButton('Remove', 'trash', `data-group-remove="${i}"`, 'text-red-700'))}</td>
                </tr>`;
            }).join('') : `<tr><td colspan="8" class="py-6 text-center text-sm text-gray-500">No specifications yet. Fill the form above and choose “Add to booking”.</td></tr>`;

            form.querySelector('[data-count="groups"]').textContent = groups.length;
            const qty = groups.reduce((s, g) => s + g.colorLines.reduce((t, l) => t + (l.quantity || 0), 0), 0);
            const amount = groups.reduce((s, g) => s + g.colorLines.reduce((t, l) => t + lineAmount(l), 0), 0);
            form.querySelector('[data-total="qty"]').textContent = nf.format(qty);
            form.querySelector('[data-total="amount"]').textContent =
                `${form.elements.namedItem('currencyCode').value} ${nf.format(amount)}`;
        }
        form.elements.namedItem('currencyCode').addEventListener('change', renderGroups);

        specBody.addEventListener('click', async event => {
            const edit = event.target.closest('[data-group-edit]');
            if (edit) {
                if (specHasContent() && !await App.confirm({ title: 'Replace what is in the editor?',
                        message: 'The specification being entered above has not been added to the booking.',
                        confirmText: 'Replace' })) return;
                editGroup(Number(edit.dataset.groupEdit));
                return;
            }
            const remove = event.target.closest('[data-group-remove]');
            if (remove) {
                const i = Number(remove.dataset.groupRemove);
                if (!await App.confirm({ title: `Remove specification ${i + 1}?`,
                        message: 'Its colour breakdown is removed with it.', confirmText: 'Remove', danger: true })) return;
                groups.splice(i, 1);
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
                if (!commitSpec()) return;
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
                marketingPersonId: marketer().select.value ? Number(marketer().select.value) : null,
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
            const submit = form.querySelector('[type="submit"]');
            submit.disabled = true;
            try {
                const saved = await App.api('/api/booking', { method: 'POST', body });
                App.toast(`${saved.documentNo} saved as a draft.`, 'success');
                await closeEditor(true);
                screen.grid.reload();
                screen.open(saved.id);
            } catch (error) {
                App.fail(error);
            } finally {
                submit.disabled = false;
            }
        });
    });
})();
