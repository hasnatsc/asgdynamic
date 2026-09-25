/*
 * FABRICS LTD. ERP - shared front-end helpers, exposed as window.App.
 *
 * Loaded with `defer` by layout/main.html, so it runs before DOMContentLoaded. Page scripts
 * inside a content fragment therefore wrap themselves in
 *     document.addEventListener('DOMContentLoaded', () => { ... App ... });
 *
 * No framework and no build step: native <dialog>, fetch and URLSearchParams cover what the
 * admin screens need. Everything user-supplied goes through App.esc before reaching innerHTML.
 */
(function () {
    'use strict';

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    // ------------------------------------------------------------------------------------------
    // Text & formatting
    // ------------------------------------------------------------------------------------------

    const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };

    function esc(value) {
        return value == null ? '' : String(value).replace(/[&<>"']/g, ch => ESCAPES[ch]);
    }

    function toDate(iso) {
        if (!iso) return null;
        const d = new Date(iso);
        return isNaN(d) ? null : d;
    }

    const fmt = {
        date(iso) {
            const d = toDate(iso);
            return d ? d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '';
        },
        dateTime(iso) {
            const d = toDate(iso);
            return d ? d.toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric',
                                                     hour: '2-digit', minute: '2-digit' }) : '';
        },
        /** "3 min ago", "yesterday", "12 Mar 2026" - with the exact time as the tooltip. */
        relative(iso) {
            const d = toDate(iso);
            if (!d) return '';
            const seconds = Math.round((Date.now() - d.getTime()) / 1000);
            const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
            if (Math.abs(seconds) < 60) return 'just now';
            if (Math.abs(seconds) < 3600) return rtf.format(-Math.round(seconds / 60), 'minute');
            if (Math.abs(seconds) < 86400) return rtf.format(-Math.round(seconds / 3600), 'hour');
            if (Math.abs(seconds) < 86400 * 7) return rtf.format(-Math.round(seconds / 86400), 'day');
            return fmt.date(iso);
        },
        timeTag(iso, empty) {
            if (!iso) return `<span class="text-gray-400">${esc(empty || 'Never')}</span>`;
            return `<time datetime="${esc(iso)}" title="${esc(fmt.dateTime(iso))}">${esc(fmt.relative(iso))}</time>`;
        },
        /** 1234567.5 -> '1,234,567.50'. Accounting figures always show two decimals. */
        money(value) {
            if (value === null || value === undefined || value === '') return '';
            return Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        },
        initials(name) {
            return String(name || '?').trim().split(/[\s._-]+/).filter(Boolean).slice(0, 2)
                .map(part => part[0].toUpperCase()).join('') || '?';
        }
    };

    /** Downloads rows (arrays of cells) as a CSV file; the first row is the header. */
    function downloadCsv(filename, rows) {
        const cell = v => {
            const text = v === null || v === undefined ? '' : String(v);
            return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
        };
        // A byte-order mark so Excel reads the file as UTF-8 (Taka signs, names in Bangla).
        const csv = '\ufeff' + rows.map(r => r.map(cell).join(',')).join('\r\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: filename });
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    }

    function debounce(fn, wait) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), wait);
        };
    }

    // ------------------------------------------------------------------------------------------
    // Dark mode
    // ------------------------------------------------------------------------------------------

    function darkMode() {
        const stored = localStorage.getItem('theme');
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        const isDark = stored ? stored === 'dark' : prefersDark;
        if (isDark) document.documentElement.classList.add('dark');

        return {
            toggle() {
                const dark = document.documentElement.classList.toggle('dark');
                localStorage.setItem('theme', dark ? 'dark' : 'light');
                return dark;
            },
            get isDark() { return document.documentElement.classList.contains('dark'); }
        };
    }

    const theme = darkMode();

    // ------------------------------------------------------------------------------------------
    // HTTP
    // ------------------------------------------------------------------------------------------

    function queryString(params) {
        const qs = new URLSearchParams();
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value !== null && value !== undefined && value !== '') qs.append(key, value);
        });
        const s = qs.toString();
        return s ? '?' + s : '';
    }

    /**
     * JSON in, JSON out, CSRF header attached. Rejects with an Error whose message is the
     * server's sentence (see ApiExceptionHandler) and whose .status is the HTTP status.
     */
    async function api(url, options) {
        options = options || {};
        const headers = { 'Accept': 'application/json' };
        if (options.body !== undefined) headers['Content-Type'] = 'application/json';
        if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

        const response = await fetch(url + queryString(options.query), {
            method: options.method || 'GET',
            headers,
            body: options.body === undefined ? undefined : JSON.stringify(options.body),
            credentials: 'same-origin'
        });

        if (response.status === 401) {
            toast('Your session has ended. Taking you to sign in…', 'warn');
            setTimeout(() => { window.location.href = '/login'; }, 1200);
            throw Object.assign(new Error('Session ended'), { status: 401, handled: true });
        }
        const text = await response.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch (ignored) { /* non-JSON body */ }

        if (!response.ok) {
            const message = (data && data.message) || `Request failed (${response.status})`;
            throw Object.assign(new Error(message), { status: response.status, body: data });
        }
        return data;
    }

    /** Standard catch for an action: toast the server's sentence. */
    function fail(error) {
        if (!error || error.handled) return;
        toast(error.message || 'Something went wrong', 'error');
    }

    // ------------------------------------------------------------------------------------------
    // Toasts
    // ------------------------------------------------------------------------------------------

    /** A sprite icon as an HTML string: icon('search', 'icon-lg text-gray-400'). */
    function icon(name, cls) {
        return `<svg class="icon ${cls || ''}" aria-hidden="true"><use href="/images/icons.svg#${esc(name)}"/></svg>`;
    }

    /**
     * Per-row buttons, identical on every register: bordered, icon + word, always visible, and
     * pinned to the table's right edge automatically (the .row-actions rules in input.css).
     *   App.rowActions(App.editButton(r.id, CAN.amend), App.rowButton('Unlock', 'unlock', `data-unlock="${r.id}"`))
     */
    function rowButton(label, iconName, attrs, cls) {
        return `<button type="button" class="btn-ghost btn-sm ${cls || ''}" ${attrs || ''} title="${esc(label)}">`
            + `${icon(iconName)}${esc(label)}</button>`;
    }
    function viewButton(id) {
        return rowButton('View', 'eye', `data-view="${esc(id)}"`);
    }
    function editButton(id) {
        return rowButton('Edit', 'edit', `data-edit="${esc(id)}"`);
    }
    /** The standard pair on every register: View always, Edit only with AMEND. */
    function recordButtons(id, canAmend) {
        return viewButton(id) + (canAmend ? editButton(id) : '');
    }
    function rowActions(...buttons) {
        return `<div class="row-actions">${buttons.filter(Boolean).join('')}</div>`;
    }

    // One neutral surface for every toast; the type shows in the icon, never in colour alone.
    const TOAST_STYLES = {
        success: ['check-circle', 'text-emerald-600'],
        error:   ['x-circle', 'text-red-600'],
        warn:    ['alert', 'text-amber-500'],
        info:    ['info', 'text-sky-600']
    };

    function toastRoot() {
        let root = document.getElementById('toast-root');
        if (!root) {
            root = document.createElement('div');
            root.id = 'toast-root';
            root.className = 'pointer-events-none fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2';
            root.setAttribute('aria-live', 'polite');
            document.body.appendChild(root);
        }
        return root;
    }

    function toast(message, type) {
        const el = document.createElement('div');
        const [glyph, tone] = TOAST_STYLES[type] || TOAST_STYLES.info;
        el.className = 'pointer-events-auto flex animate-toast-in items-start gap-3 rounded-xl border border-gray-200 bg-white '
            + 'px-4 py-3 text-sm text-gray-800 shadow-float dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100';
        el.setAttribute('role', type === 'error' ? 'alert' : 'status');
        el.innerHTML = `${icon(glyph, 'icon-lg mt-px ' + tone)}<span class="flex-1"></span>`;
        el.querySelector('span').textContent = message;
        toastRoot().appendChild(el);
        const ttl = type === 'error' ? 7000 : 3500;
        setTimeout(() => {
            el.style.transition = 'opacity .2s';
            el.style.opacity = '0';
            setTimeout(() => el.remove(), 220);
        }, ttl);
        el.addEventListener('click', () => el.remove());
    }

    // ------------------------------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------------------------------

    /** Closes any <dialog> when its backdrop (the dialog element itself) is clicked. */
    document.addEventListener('click', event => {
        const target = event.target;
        if (target instanceof HTMLDialogElement && target.open && !target.hasAttribute('data-sticky')) {
            const r = target.getBoundingClientRect();
            const inside = event.clientX >= r.left && event.clientX <= r.right
                        && event.clientY >= r.top && event.clientY <= r.bottom;
            if (!inside) target.close('cancel');
        }
    });

    /** [data-close] inside a dialog closes it. */
    document.addEventListener('click', event => {
        const closer = event.target.closest('[data-close]');
        if (closer) closer.closest('dialog')?.close('cancel');
    });

    /**
     * A one-off dialog with a form. fields: [{name, label, type, required, minlength, maxlength,
     * placeholder, value, hint, options:[{value,label}]}]. Resolves with the values, or null
     * when cancelled. Native validation runs before it resolves.
     */
    function formDialog(opts) {
        return new Promise(resolve => {
            const dialog = document.createElement('dialog');
            dialog.className = 'modal';
            const fields = (opts.fields || []).map((f, i) => {
                const id = 'dlg-field-' + i;
                const attrs = [
                    `id="${id}"`, `name="${esc(f.name)}"`,
                    f.required ? 'required' : '',
                    f.minlength ? `minlength="${f.minlength}"` : '',
                    f.maxlength ? `maxlength="${f.maxlength}"` : '',
                    f.placeholder ? `placeholder="${esc(f.placeholder)}"` : '',
                    f.autocomplete ? `autocomplete="${esc(f.autocomplete)}"` : ''
                ].join(' ');
                let control;
                if (f.type === 'textarea') {
                    control = `<textarea class="field" rows="3" ${attrs}>${esc(f.value)}</textarea>`;
                } else if (f.type === 'select') {
                    control = `<select class="field" ${attrs}>${(f.options || []).map(o =>
                        `<option value="${esc(o.value)}"${o.value === f.value ? ' selected' : ''}>${esc(o.label)}</option>`
                    ).join('')}</select>`;
                } else {
                    control = `<input class="field" type="${esc(f.type || 'text')}" value="${esc(f.value)}" ${attrs}>`;
                }
                return `<div><label class="label" for="${id}">${esc(f.label)}</label>${control}`
                    + (f.hint ? `<p class="hint">${esc(f.hint)}</p>` : '') + `</div>`;
            }).join('');

            dialog.innerHTML = `
                <form method="dialog" novalidate>
                    <div class="modal-head">
                        <div>
                            <h2 class="modal-title">${esc(opts.title)}</h2>
                            ${opts.message ? `<p class="mt-1 text-sm text-gray-500">${esc(opts.message)}</p>` : ''}
                        </div>
                        <button type="button" class="btn-icon -mr-2 -mt-1" data-close aria-label="Close">${icon('x', 'icon-lg')}</button>
                    </div>
                    ${fields ? `<div class="modal-body space-y-4">${fields}</div>` : ''}
                    <div class="modal-foot">
                        <button type="button" class="btn-ghost" data-close>${esc(opts.cancelText || 'Cancel')}</button>
                        <button type="submit" class="${opts.danger ? 'btn-danger' : 'btn-primary'}" value="ok">
                            ${esc(opts.confirmText || 'OK')}</button>
                    </div>
                </form>`;
            document.body.appendChild(dialog);

            const form = dialog.querySelector('form');
            let result = null;
            form.addEventListener('submit', event => {
                if (!form.checkValidity()) {
                    event.preventDefault();
                    form.reportValidity();
                    return;
                }
                result = Object.fromEntries(new FormData(form).entries());
            });
            dialog.addEventListener('close', () => {
                dialog.remove();
                resolve(dialog.returnValue === 'ok' ? result || {} : null);
            });
            dialog.showModal();
            (dialog.querySelector('input,select,textarea') || dialog.querySelector('[type=submit]')).focus();
        });
    }

    function confirmDialog(opts) {
        return formDialog(Object.assign({ fields: [] }, opts)).then(result => result !== null);
    }

    // ------------------------------------------------------------------------------------------
    // View mode: every record dialog opens read-only from View or a row click
    // ------------------------------------------------------------------------------------------

    // Stay usable while viewing: tabs, close buttons, and anything a page marks data-view-keep
    // (e.g. a filter over a long list). Everything else is locked.
    const VIEW_KEEP = '[role=tab], [data-dismiss], [data-close], [data-view-keep], [data-view-edit]';
    const VIEW_CONTROLS = 'input, select, textarea, button';

    /**
     * App.viewMode(dialog, true, { canEdit, onEdit }) - show a record dialog read-only.
     *
     * Call it at the end of a page's openEditor(), after the page has applied its own rules.
     * It only ever disables controls that were enabled, and marks them, so leaving view mode
     * restores exactly the page's state. Controls added later (a tab's rows loaded on demand)
     * are locked as they appear. Save, delete and add/remove buttons are hidden, Cancel reads
     * Close, the title gets a "View only" badge, and with canEdit an Edit button switches the same
     * dialog into edit mode. Closing the dialog always leaves view mode.
     */
    function viewMode(dialog, on, opts) {
        opts = opts || {};
        if (!on) {
            if (!dialog.hasAttribute('data-view-mode')) return;
            dialog.removeAttribute('data-view-mode');
            dialog._viewObserver?.disconnect();
            dialog.querySelectorAll('[data-view-locked]').forEach(el => {
                el.disabled = false;
                el.removeAttribute('data-view-locked');
            });
            dialog.querySelectorAll('[data-view-label]').forEach(el => {
                el.textContent = el.dataset.viewLabel;
                el.removeAttribute('data-view-label');
            });
            dialog.querySelectorAll('[data-view-badge], [data-view-edit]').forEach(el => el.remove());
            return;
        }
        if (dialog.hasAttribute('data-view-mode')) return;
        dialog.setAttribute('data-view-mode', '');

        const lock = el => {
            if (el.disabled || el.matches(VIEW_KEEP) || el.closest('[data-view-keep]')) return;
            el.disabled = true;
            el.setAttribute('data-view-locked', '');
        };
        dialog.querySelectorAll(VIEW_CONTROLS).forEach(lock);
        dialog._viewObserver = new MutationObserver(records => records.forEach(record =>
            record.addedNodes.forEach(node => {
                if (node.nodeType !== 1) return;
                if (node.matches(VIEW_CONTROLS)) lock(node);
                node.querySelectorAll(VIEW_CONTROLS).forEach(lock);
            })));
        dialog._viewObserver.observe(dialog, { childList: true, subtree: true });

        const title = dialog.querySelector('.modal-head h2, .modal-head .modal-title');
        title?.insertAdjacentHTML('beforeend',
            ' <span class="badge-gray ml-1 align-middle" data-view-badge>' + icon('eye', 'h-3 w-3') + 'View only</span>');

        const foot = dialog.querySelector('.modal-foot');
        foot?.querySelectorAll('[data-dismiss], [data-close]').forEach(btn => {
            if (btn.textContent.trim() === 'Cancel') {
                btn.dataset.viewLabel = btn.textContent;
                btn.textContent = 'Close';
            }
        });
        if (foot && opts.canEdit) {
            const edit = document.createElement('button');
            edit.type = 'button';
            edit.className = 'btn-primary';
            edit.setAttribute('data-view-edit', '');
            edit.innerHTML = icon('edit') + 'Edit';
            edit.addEventListener('click', () => {
                viewMode(dialog, false);
                if (opts.onEdit) opts.onEdit();
                else dialog.querySelector('.modal-body input:not([type=hidden]):not(:disabled), .modal-body select:not(:disabled)')?.focus();
            });
            foot.appendChild(edit);
        }
        if (!dialog._viewCloseHook) {
            dialog._viewCloseHook = true;
            dialog.addEventListener('close', () => viewMode(dialog, false));
        }
    }

    /**
     * A read-only record dialog for screens whose editor is not a dialog:
     * App.viewRecord({ title, subtitle, fields: [[label, html], ...], canEdit, onEdit }).
     * Values are HTML - escape user data with App.esc.
     */
    function viewRecord(opts) {
        const dialog = document.createElement('dialog');
        dialog.className = 'modal';
        dialog.innerHTML = `
            <div class="modal-head">
                <div class="min-w-0">
                    <h2 class="modal-title">${esc(opts.title)} <span class="badge-gray ml-1 align-middle">${icon('eye', 'h-3 w-3')}View only</span></h2>
                    ${opts.subtitle ? `<p class="mt-1 text-sm text-gray-500">${esc(opts.subtitle)}</p>` : ''}
                </div>
                <button type="button" class="btn-icon -mr-2 -mt-1" data-close aria-label="Close">${icon('x', 'icon-lg')}</button>
            </div>
            <dl class="modal-body divide-y divide-gray-100 p-0 dark:divide-gray-800">
                ${(opts.fields || []).map(([label, value]) => `<div class="grid grid-cols-3 gap-4 px-6 py-3 text-sm">
                    <dt class="text-gray-500">${esc(label)}</dt>
                    <dd class="col-span-2 font-medium text-gray-900 dark:text-white">${value == null || value === '' ? '<span class="font-normal text-gray-400">—</span>' : value}</dd>
                </div>`).join('')}
            </dl>
            <div class="modal-foot">
                <button type="button" class="btn-ghost" data-close>Close</button>
                ${opts.canEdit ? `<button type="button" class="btn-primary" data-view-edit>${icon('edit')}Edit</button>` : ''}
            </div>`;
        dialog.querySelector('[data-view-edit]')?.addEventListener('click', () => {
            dialog.close();
            opts.onEdit && opts.onEdit();
        });
        dialog.addEventListener('close', () => dialog.remove());
        document.body.appendChild(dialog);
        dialog.showModal();
    }

    // ------------------------------------------------------------------------------------------
    // Command palette (Ctrl+K)
    // ------------------------------------------------------------------------------------------

    function openCommandPalette() {
        const existing = document.getElementById('cmd-palette');
        if (existing?.open) { existing.close(); return; }
        if (existing) existing.remove();

        // Every link the sidebar renders carries data-nav: the palette offers exactly the screens the
        // user may open, and nothing else.
        const items = [...document.querySelectorAll('#sidebar a[data-nav]')].map(a => ({
            label: a.textContent.trim(),
            path: a.getAttribute('href'),
            section: a.dataset.navSection || '',
            group: a.dataset.navGroup || '',
            icon: a.dataset.navIcon || 'document'
        }));

        const dialog = document.createElement('dialog');
        dialog.id = 'cmd-palette';
        dialog.className = 'cmd-palette';
        dialog.innerHTML = `
            <div class="relative">
                ${icon('search', 'icon-lg pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-gray-400')}
                <input class="cmd-input" placeholder="Search screens…" autocomplete="off" spellcheck="false">
            </div>
            <div class="cmd-list" id="cmd-list"></div>
            <div class="flex items-center justify-between border-t border-gray-200 dark:border-gray-700 px-4 py-2 text-xs text-gray-400">
                <span>Navigate with <kbd class="kbd">↑</kbd> <kbd class="kbd">↓</kbd> then <kbd class="kbd">Enter</kbd></span>
                <span><kbd class="kbd">Esc</kbd> to close</span>
            </div>`;
        document.body.appendChild(dialog);

        const input = dialog.querySelector('.cmd-input');
        const list = dialog.querySelector('#cmd-list');
        let activeIndex = 0;

        function render(query) {
            const q = (query || '').toLowerCase();
            const filtered = q ? items.filter(i =>
                i.label.toLowerCase().includes(q) ||
                (i.section && i.section.toLowerCase().includes(q)) ||
                (i.group && i.group.toLowerCase().includes(q))
            ) : items;

            if (!filtered.length) {
                list.innerHTML = '<div class="px-4 py-6 text-center text-sm text-gray-500">No screens found.</div>';
                return;
            }
            let html = '';
            let lastSection = null;
            filtered.forEach((item, i) => {
                const sec = item.section || '';
                if (sec !== lastSection) {
                    html += `<div class="cmd-section">${esc(sec || 'Navigation')}</div>`;
                    lastSection = sec;
                }
                const active = i === activeIndex ? ' is-active' : '';
                const sub = item.group ? `<span class="text-xs text-gray-400">${esc(item.group)}</span>` : '';
                html += `<a href="${esc(item.path)}" class="cmd-item${active}" data-cmd="${i}">
                    ${icon(item.icon, 'text-gray-400')}
                    <span class="flex-1">${esc(item.label)}</span>${sub}
                </a>`;
            });
            list.innerHTML = html;
        }

        function navigate() {
            const active = list.querySelector('.cmd-item.is-active');
            if (active) { dialog.close(); window.location.href = active.getAttribute('href'); }
        }

        function clampIndex(filtered) {
            const count = list.querySelectorAll('.cmd-item').length;
            if (activeIndex < 0) activeIndex = count - 1;
            if (activeIndex >= count) activeIndex = 0;
        }

        input.addEventListener('input', () => { activeIndex = 0; render(input.value); });
        dialog.addEventListener('keydown', e => {
            if (e.key === 'ArrowDown') { e.preventDefault(); activeIndex++; clampIndex(); render(input.value); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); activeIndex--; clampIndex(); render(input.value); }
            else if (e.key === 'Enter') { e.preventDefault(); navigate(); }
        });
        list.addEventListener('click', e => {
            const item = e.target.closest('.cmd-item');
            if (item) { e.preventDefault(); dialog.close(); window.location.href = item.getAttribute('href'); }
        });

        render('');
        dialog.showModal();
        input.focus();
    }

    // ------------------------------------------------------------------------------------------
    // Tabs: [role=tablist] > [role=tab][data-tab=x] ; [data-panel=x]
    // ------------------------------------------------------------------------------------------

    function tabs(root, onChange) {
        const buttons = Array.from(root.querySelectorAll('[role="tab"]'));
        const panels = Array.from(root.querySelectorAll('[data-panel]'));
        function select(name) {
            buttons.forEach(b => b.setAttribute('aria-selected', String(b.dataset.tab === name)));
            panels.forEach(p => { p.hidden = p.dataset.panel !== name; });
            if (onChange) onChange(name);
        }
        buttons.forEach(b => b.addEventListener('click', () => { if (!b.disabled) select(b.dataset.tab); }));
        return { select };
    }

    // ------------------------------------------------------------------------------------------
    // Server-side grid over the DataTableRequest/DataTableResponse contract
    // ------------------------------------------------------------------------------------------

    /**
     * new App.Grid({
     *   url, table,                         // <table> with <thead> th[data-sort] and an empty <tbody>
     *   columns: [row => html, ...],        // one per <th>; return escaped HTML
     *   search, pager,                      // optional <input> and container element
     *   params: () => ({status: 'LOCKED'}), // extra filters, read on each load
     *   sort: {column, dir}, pageSize, emptyText, onRowClick(row, event)
     * })
     *
     * Every response carries the draw it answers, and one that is not the latest is dropped - a
     * slow page-1 reply can never overwrite the page-3 the user has since asked for.
     */
    class Grid {
        constructor(opts) {
            this.opts = opts;
            this.table = opts.table;
            this.tbody = this.table.querySelector('tbody');
            this.columnCount = this.table.querySelectorAll('thead th').length;
            this.pageSize = opts.pageSize || 25;
            this.start = 0;
            this.draw = 0;
            this.sort = opts.sort || {};
            this.rows = [];

            this.table.querySelectorAll('thead th[data-sort]').forEach(th => {
                th.tabIndex = 0;
                const toggle = () => {
                    const column = th.dataset.sort;
                    this.sort = { column, dir: this.sort.column === column && this.sort.dir === 'asc' ? 'desc' : 'asc' };
                    this.reload(true);
                };
                th.addEventListener('click', toggle);
                th.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); } });
            });

            if (opts.search) {
                opts.search.addEventListener('input', debounce(() => this.reload(true), 250));
            }
            if (opts.onRowClick) {
                this.tbody.addEventListener('click', event => {
                    if (event.target.closest('button, a, input, label, select')) return;
                    const tr = event.target.closest('tr[data-index]');
                    if (tr) opts.onRowClick(this.rows[Number(tr.dataset.index)], event);
                });
            }
            if (opts.pager) {
                opts.pager.addEventListener('click', event => {
                    const btn = event.target.closest('[data-page]');
                    if (!btn || btn.disabled) return;
                    this.start = Math.max(0, this.start + Number(btn.dataset.page) * this.pageSize);
                    this.load();
                });
                opts.pager.addEventListener('change', event => {
                    if (event.target.matches('[data-page-size]')) {
                        this.pageSize = Number(event.target.value);
                        this.reload(true);
                    }
                });
            }
        }

        reload(resetPage) {
            if (resetPage) this.start = 0;
            return this.load();
        }

        renderSortIndicators() {
            this.table.querySelectorAll('thead th[data-sort]').forEach(th => {
                if (th.dataset.sort === this.sort.column) {
                    th.setAttribute('aria-sort', this.sort.dir === 'desc' ? 'descending' : 'ascending');
                } else {
                    th.removeAttribute('aria-sort');
                }
            });
        }

        renderLoading() {
            if (this.rows.length) {
                this.tbody.style.opacity = '.55';
                return;
            }
            const cell = '<td><div class="skeleton h-4 w-3/4"></div></td>';
            this.tbody.innerHTML = Array.from({ length: 5 },
                () => `<tr>${cell.repeat(this.columnCount)}</tr>`).join('');
        }

        async load() {
            const draw = ++this.draw;
            this.renderSortIndicators();
            this.renderLoading();
            const extra = this.opts.params ? this.opts.params() : {};
            try {
                const data = await api(this.opts.url, { query: Object.assign({
                    draw, start: this.start, length: this.pageSize,
                    'search[value]': this.opts.search ? this.opts.search.value.trim() : '',
                    sortColumn: this.sort.column, sortDir: this.sort.dir
                }, extra) });
                if (draw !== this.draw) return;            // superseded by a newer request
                this.rows = data.data || [];
                this.total = data.recordsFiltered || 0;
                this.render();
            } catch (error) {
                if (draw !== this.draw) return;
                this.rows = [];
                this.tbody.style.opacity = '';
                this.tbody.innerHTML = `<tr><td colspan="${this.columnCount}"><div class="empty">`
                    + `<span class="empty-icon bg-red-50 text-red-600">${icon('alert', 'icon-lg')}</span>`
                    + `<p class="empty-title">Could not load this list</p>`
                    + `<p class="empty-text">${esc(error.message || 'Try again in a moment.')}</p></div></td></tr>`;
            }
        }

        render() {
            this.tbody.style.opacity = '';
            if (!this.rows.length) {
                this.tbody.innerHTML = `<tr><td colspan="${this.columnCount}"><div class="empty">`
                    + `<span class="empty-icon">${icon(this.opts.emptyIcon || 'search', 'icon-lg')}</span>`
                    + `<p class="empty-text">${esc(this.opts.emptyText || 'Nothing to show')}</p></div></td></tr>`;
            } else {
                // data-label lets .table-stack show each cell's column name when rows stack on phones.
                const labels = [...this.table.querySelectorAll('thead th')].map(th => esc(th.textContent.trim()));
                const clickable = this.opts.onRowClick ? ' class="cursor-pointer is-clickable"' : '';
                this.tbody.innerHTML = this.rows.map((row, i) =>
                    `<tr data-index="${i}"${clickable}>${this.opts.columns.map((col, c) =>
                        `<td data-label="${labels[c] || ''}">${col(row)}</td>`).join('')}</tr>`
                ).join('');
            }
            this.renderPager();
        }

        renderPager() {
            const pager = this.opts.pager;
            if (!pager) return;
            const from = this.total === 0 ? 0 : this.start + 1;
            const to = Math.min(this.start + this.pageSize, this.total);
            const sizes = [10, 25, 50, 100];
            pager.innerHTML = `
                <span><b class="font-medium text-gray-700 dark:text-gray-200">${from}–${to}</b> of
                    <b class="font-medium text-gray-700 dark:text-gray-200">${this.total}</b></span>
                <div class="flex items-center gap-3">
                    <label class="flex items-center gap-2">Rows per page
                        <select data-page-size class="field field-sm w-auto py-1 pl-2 pr-8">
                            ${sizes.map(s => `<option${s === this.pageSize ? ' selected' : ''}>${s}</option>`).join('')}
                        </select>
                    </label>
                    <div class="flex items-center gap-1">
                        <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="-1"
                                aria-label="Previous page" ${this.start === 0 ? 'disabled' : ''}>${icon('chevron-left')}</button>
                        <button type="button" class="btn-icon btn-sm border border-gray-200 dark:border-gray-700" data-page="1"
                                aria-label="Next page" ${to >= this.total ? 'disabled' : ''}>${icon('chevron-right')}</button>
                    </div>
                </div>`;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Remote select: a searchable, paged picker over the LookupPage contract
    // ------------------------------------------------------------------------------------------

    const POPOVER = typeof HTMLElement !== 'undefined' && HTMLElement.prototype.hasOwnProperty('popover');

    /**
     * The one picker for any list too large to ship whole into a <select> - Select2's job,
     * without jQuery. Declare it on a plain select and it is enhanced on page load:
     *
     *   <select id="fCategory" class="field" data-remote="/api/lookup/inventory/categories"
     *           data-placeholder="Choose…" data-allow-clear required></select>
     *
     * or build it from script: new App.RemoteSelect(select, { url, params, placeholder,
     * allowClear, pageSize, emptyText }). The feed answers ?q=&page=&size= (page 1-based) with
     * {results: [{id, text, code?, sub?}], pagination: {more}} and ?id= with that one option -
     * com.asg.fabricerp.common.LookupPage on the server.
     *
     * The native <select> stays in the form and holds the value, so .value, required, the
     * change event, form reset and App.viewMode all work as on any select. From script:
     *   App.RemoteSelect.of(select).setValue(id)   - labels it with one ?id= request
     *   .setValue(id, text), .setValue(option)     - no request
     *   .selected                                  - the chosen option object, or null
     *   .refresh()                                 - after params() would answer differently
     */
    class RemoteSelect {
        static of(select) {
            return select._remote || new RemoteSelect(select);
        }

        constructor(select, opts) {
            if (select._remote) return select._remote;
            select._remote = this;
            const data = select.dataset;
            this.select = select;
            this.opts = Object.assign({
                url: data.remote,
                placeholder: data.placeholder || 'Choose…',
                allowClear: 'allowClear' in data,
                pageSize: Number(data.pageSize) || 20,
                emptyText: data.emptyText || 'No matches',
                params: null
            }, opts || {});
            this.selected = null;
            this.results = [];
            this.page = 0;
            this.more = false;
            this.draw = 0;
            this.active = -1;
            this.build();
            const initial = select.value || data.value;
            if (initial) this.setValue(initial, select.selectedOptions[0]?.textContent);
            else this.renderValue();
        }

        build() {
            const s = this.select;
            const id = s.id || ('rs-' + Math.random().toString(36).slice(2, 8));
            this.wrap = document.createElement('div');
            this.wrap.className = 'rselect';
            s.parentNode.insertBefore(this.wrap, s);
            this.wrap.appendChild(s);
            s.classList.add('rselect-native');
            s.tabIndex = -1;
            s.setAttribute('aria-hidden', 'true');

            // The trigger is not a <button>: App.viewMode locks the native select and the trigger
            // follows it, so a record shown read-only reads as a value, not a hidden control.
            this.trigger = document.createElement('div');
            this.trigger.className = 'field rselect-trigger';
            this.trigger.tabIndex = 0;
            this.trigger.setAttribute('role', 'combobox');
            this.trigger.setAttribute('aria-haspopup', 'listbox');
            this.trigger.setAttribute('aria-expanded', 'false');
            this.trigger.setAttribute('aria-controls', id + '-list');
            const label = s.id && document.querySelector(`label[for="${CSS.escape(s.id)}"]`);
            if (label) {
                label.id = label.id || id + '-label';
                this.trigger.setAttribute('aria-labelledby', label.id);
                label.addEventListener('click', e => { e.preventDefault(); this.trigger.focus(); });
            }
            this.trigger.innerHTML = `<span class="rselect-value"></span>`
                + `<button type="button" class="rselect-clear" tabindex="-1" aria-label="Clear" hidden>${icon('x')}</button>`
                + icon('chevron-down', 'rselect-caret');
            this.wrap.insertBefore(this.trigger, s);

            this.menu = document.createElement('div');
            this.menu.className = 'rselect-menu';
            this.menu.setAttribute('data-view-keep', '');
            if (POPOVER) this.menu.popover = 'manual';
            else this.menu.hidden = true;
            this.menu.innerHTML = `
                <div class="rselect-search">${icon('search')}<input type="search" autocomplete="off" spellcheck="false"
                     placeholder="Type to search…" aria-label="Search" aria-controls="${id}-list"></div>
                <ul class="rselect-list scroll-thin" role="listbox" id="${id}-list"></ul>`;
            this.wrap.appendChild(this.menu);
            this.input = this.menu.querySelector('input');
            this.list = this.menu.querySelector('ul');

            this.trigger.addEventListener('click', e => {
                if (e.target.closest('.rselect-clear')) {
                    e.stopPropagation();
                    this.pick(null);
                    return;
                }
                this.isOpen ? this.close() : this.open();
            });
            this.trigger.addEventListener('keydown', e => {
                if (this.disabled) return;
                if (['Enter', ' ', 'ArrowDown', 'ArrowUp'].includes(e.key)) {
                    e.preventDefault();
                    this.open();
                } else if ((e.key === 'Delete' || e.key === 'Backspace') && this.opts.allowClear) {
                    e.preventDefault();
                    this.pick(null);
                } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
                    e.preventDefault();
                    this.open(e.key);           // start searching with the key just typed
                }
            });
            this.input.addEventListener('input', debounce(() => this.search(), 250));
            this.input.addEventListener('keydown', e => this.onKey(e));
            this.list.addEventListener('mousedown', e => e.preventDefault());   // keep focus in the search box
            this.list.addEventListener('click', e => {
                const li = e.target.closest('li[data-index]');
                if (li) this.pick(this.results[Number(li.dataset.index)]);
            });
            this.list.addEventListener('mousemove', e => {
                const li = e.target.closest('li[data-index]');
                if (li) this.highlight(Number(li.dataset.index), false);
            });
            this.list.addEventListener('scroll', () => {
                if (this.more && !this.loading
                        && this.list.scrollTop + this.list.clientHeight >= this.list.scrollHeight - 40) {
                    this.load(this.page + 1);
                }
            });

            this.onOutside = e => {
                if (!this.wrap.contains(e.target)) this.close();
            };
            this.onReflow = e => {
                if (e && e.target && this.menu.contains(e.target)) return;
                this.position();
            };
            // A page or App.viewMode enabling / disabling the select carries through to the trigger.
            new MutationObserver(() => this.syncDisabled())
                .observe(s, { attributes: true, attributeFilter: ['disabled'] });
            s.form?.addEventListener('reset', () => setTimeout(() => this.setValue(null)));
            s.closest('dialog')?.addEventListener('close', () => this.close());
            this.syncDisabled();
        }

        get disabled() {
            return this.select.disabled;
        }

        get isOpen() {
            return this.wrap.classList.contains('is-open');
        }

        syncDisabled() {
            const off = this.disabled;
            this.trigger.tabIndex = off ? -1 : 0;
            this.trigger.setAttribute('aria-disabled', String(off));
            this.trigger.classList.toggle('is-disabled', off);
            if (off) this.close();
            this.renderValue();
        }

        open(seed) {
            if (this.disabled || this.isOpen) return;
            this.wrap.classList.add('is-open');
            this.trigger.setAttribute('aria-expanded', 'true');
            if (POPOVER) this.menu.showPopover();
            else this.menu.hidden = false;
            this.position();
            this.input.value = seed || '';
            this.input.focus();
            this.search();
            document.addEventListener('pointerdown', this.onOutside, true);
            window.addEventListener('resize', this.onReflow);
            window.addEventListener('scroll', this.onReflow, true);
        }

        close(refocus) {
            if (!this.isOpen) return;
            this.wrap.classList.remove('is-open');
            this.trigger.setAttribute('aria-expanded', 'false');
            this.trigger.removeAttribute('aria-activedescendant');
            if (POPOVER) this.menu.hidePopover();
            else this.menu.hidden = true;
            this.draw++;                       // drop any reply still on its way
            document.removeEventListener('pointerdown', this.onOutside, true);
            window.removeEventListener('resize', this.onReflow);
            window.removeEventListener('scroll', this.onReflow, true);
            if (refocus) this.trigger.focus();
        }

        /** Under the trigger, or above it when the viewport has no room below. */
        position() {
            if (!this.isOpen) return;
            const r = this.trigger.getBoundingClientRect();
            const m = this.menu.style;
            m.position = 'fixed';
            m.left = r.left + 'px';
            m.width = r.width + 'px';
            const below = window.innerHeight - r.bottom;
            const height = this.menu.offsetHeight || 320;
            if (below < height + 8 && r.top > below) {
                m.top = '';
                m.bottom = (window.innerHeight - r.top + 4) + 'px';
            } else {
                m.bottom = '';
                m.top = (r.bottom + 4) + 'px';
            }
        }

        search() {
            this.results = [];
            this.active = -1;
            this.list.scrollTop = 0;
            this.load(1);
        }

        async load(page) {
            const draw = ++this.draw;
            this.loading = true;
            this.renderList(true);
            const extra = this.opts.params ? this.opts.params() : {};
            try {
                const data = await api(this.opts.url, { query: Object.assign({
                    q: this.input.value.trim(), page, size: this.opts.pageSize }, extra) });
                if (draw !== this.draw) return;        // superseded by a newer search or closed
                this.page = page;
                this.more = !!(data.pagination && data.pagination.more);
                this.results = this.results.concat(data.results || []);
                if (this.active < 0 && this.results.length) {
                    const current = this.results.findIndex(o => this.selected && String(o.id) === String(this.selected.id));
                    this.active = current >= 0 ? current : 0;
                }
                this.loading = false;
                this.renderList();
                // A short first page leaves nothing to scroll; fetch on until the list can scroll.
                if (this.more && this.list.scrollHeight <= this.list.clientHeight) this.load(page + 1);
            } catch (error) {
                if (draw !== this.draw) return;
                this.loading = false;
                this.more = false;
                this.list.innerHTML = `<li class="rselect-note text-red-600">${esc(error.message || 'Could not load the list')}</li>`;
            }
        }

        renderList(loading) {
            const q = this.input.value.trim();
            const mark = text => {
                const safe = esc(text);
                if (!q) return safe;
                const at = String(text).toLowerCase().indexOf(q.toLowerCase());
                return at < 0 ? safe
                    : esc(text.slice(0, at)) + '<mark>' + esc(text.slice(at, at + q.length)) + '</mark>' + esc(text.slice(at + q.length));
            };
            const items = this.results.map((o, i) => {
                const chosen = this.selected && String(o.id) === String(this.selected.id);
                return `<li role="option" id="${this.list.id}-${i}" data-index="${i}" aria-selected="${chosen}"
                            class="rselect-option${i === this.active ? ' is-active' : ''}">
                    <span class="min-w-0 flex-1"><span class="block truncate">${mark(o.text)}</span>
                        ${o.sub ? `<span class="rselect-sub">${esc(o.sub)}</span>` : ''}</span>
                    ${o.code ? `<span class="rselect-code">${mark(o.code)}</span>` : ''}
                    ${chosen ? icon('check', 'shrink-0 text-brand-600') : ''}</li>`;
            }).join('');
            const note = loading
                ? `<li class="rselect-note"><span class="rselect-spinner"></span>${this.results.length ? 'Loading more…' : 'Searching…'}</li>`
                : !this.results.length ? `<li class="rselect-note">${esc(q ? 'No matches for "' + q + '"' : this.opts.emptyText)}</li>`
                : this.more ? '<li class="rselect-note">Scroll for more…</li>' : '';
            this.list.innerHTML = items + note;
            this.syncActive();
            this.position();
        }

        highlight(index, scroll) {
            if (index < 0 || index >= this.results.length || index === this.active) return;
            this.list.querySelector('.rselect-option.is-active')?.classList.remove('is-active');
            this.active = index;
            this.syncActive(scroll);
        }

        syncActive(scroll) {
            const li = this.list.querySelector(`li[data-index="${this.active}"]`);
            if (!li) return this.input.removeAttribute('aria-activedescendant');
            li.classList.add('is-active');
            this.input.setAttribute('aria-activedescendant', li.id);
            if (scroll) li.scrollIntoView({ block: 'nearest' });
        }

        onKey(e) {
            const step = { ArrowDown: 1, ArrowUp: -1, PageDown: 8, PageUp: -8 }[e.key];
            if (step) {
                e.preventDefault();
                const next = Math.max(0, Math.min(this.results.length - 1, this.active + step));
                this.highlight(next, true);
                if (next >= this.results.length - 1 && this.more && !this.loading) this.load(this.page + 1);
            } else if (e.key === 'Enter') {
                e.preventDefault();
                if (this.results[this.active]) this.pick(this.results[this.active]);
            } else if (e.key === 'Escape') {
                e.preventDefault();             // close the list, not the dialog around it
                e.stopPropagation();
                this.close(true);
            } else if (e.key === 'Tab') {
                this.close();
            }
        }

        /** A user's choice: sets the value and fires change, as picking from a select would. */
        pick(option) {
            const changed = String(this.selected ? this.selected.id : '') !== String(option ? option.id : '');
            this.apply(option);
            this.close(true);
            if (changed) this.select.dispatchEvent(new Event('change', { bubbles: true }));
        }

        apply(option) {
            this.selected = option || null;
            this.select.innerHTML = '<option value=""></option>' + (option
                ? `<option value="${esc(option.id)}" selected>${esc(option.text)}</option>` : '');
            this.select.value = option ? String(option.id) : '';
            this.renderValue();
        }

        /**
         * Sets the value from script without firing change: an option object, an id and its
         * text, or a bare id - which one ?id= request labels.
         */
        async setValue(id, text) {
            if (id == null || id === '') return this.apply(null);
            if (typeof id === 'object') return this.apply(id);
            if (text) return this.apply({ id, text });
            this.apply({ id, text: '…' });
            try {
                const data = await api(this.opts.url, { query: { id } });
                const option = (data.results || [])[0];
                if (String(this.select.value) === String(id)) this.apply(option || { id, text: '#' + id });
            } catch (error) {
                if (String(this.select.value) === String(id)) this.apply({ id, text: '#' + id });
            }
        }

        refresh() {
            this.results = [];
            if (this.isOpen) this.search();
        }

        renderValue() {
            const o = this.selected;
            this.trigger.querySelector('.rselect-value').innerHTML = o
                ? `<span class="truncate">${esc(o.text)}</span>${o.code ? `<span class="rselect-code">${esc(o.code)}</span>` : ''}`
                : `<span class="truncate text-gray-400">${esc(this.opts.placeholder)}</span>`;
            this.trigger.querySelector('.rselect-clear').hidden = !(o && this.opts.allowClear && !this.disabled);
        }
    }

    /** Enhances every select[data-remote] under root (the page on load). Safe to call again. */
    function remoteSelects(root) {
        (root || document).querySelectorAll('select[data-remote]').forEach(s => RemoteSelect.of(s));
    }
    document.addEventListener('DOMContentLoaded', () => remoteSelects());

    // ------------------------------------------------------------------------------------------
    // Tree: an expandable hierarchy (item categories, chart of accounts, ...)
    // ------------------------------------------------------------------------------------------

    /**
     * new App.Tree(ulElement, {
     *   row: (r, { mark, depth, children }) => html,  // the row after its toggle; mark(text) highlights the search
     *   parentKey: r => r.parentId, key: r => r.id,   // defaults shown
     *   text: r => [r.name, r.code],                  // what the search matches
     *   filter: r => true,                            // e.g. show-inactive, a type chip
     *   rowClass: r => '',                            // e.g. 'is-inactive' (struck through)
     *   search,                                       // optional <input>
     *   storageKey, openDepth: 1,                     // remembered expansion; first visit opens this deep
     *   onSelect(r), onAction(action, r, event),      // row click / Enter, and [data-action] buttons in a row
     *   emptyText: () => 'No match.'
     * }).setRows(rows)
     *
     * Rows arrive flat, parents before or after children, in display order among siblings. A
     * search keeps every match plus the ancestors leading to it, all open. Arrow keys walk the
     * visible rows; Right / Left open and fold, as in any file tree. .select(key) marks the row
     * a page has open, .reveal(key) opens its ancestors, .expandAll() / .collapseAll().
     */
    class Tree {
        constructor(root, opts) {
            this.root = root;
            this.opts = Object.assign({
                key: r => r.id, parentKey: r => r.parentId, text: r => [r.name, r.code],
                filter: () => true, openDepth: 1, emptyText: () => 'Nothing matches.'
            }, opts);
            this.rows = [];
            this.byKey = new Map();
            this.kids = new Map();
            this.selectedKey = null;
            const stored = this.opts.storageKey && localStorage.getItem(this.opts.storageKey);
            this.expanded = new Set(stored ? JSON.parse(stored) : []);
            this.fresh = !stored;
            root.classList.add('tree');
            root.setAttribute('role', 'tree');
            this.opts.search?.addEventListener('input', debounce(() => this.render(), 150));

            root.addEventListener('click', e => {
                const li = e.target.closest('li[data-key]');
                if (!li) return;
                const r = this.row(li);
                const action = e.target.closest('[data-action]');
                if (action) return this.opts.onAction && this.opts.onAction(action.dataset.action, r, e);
                if (e.target.closest('[data-toggle]')) return this.toggle(r);
                this.opts.onSelect && this.opts.onSelect(r);
            });
            root.addEventListener('dblclick', e => {
                const li = e.target.closest('li[data-key][aria-expanded]');
                if (li && !e.target.closest('[data-action], [data-toggle]')) this.toggle(this.row(li));
            });
            root.addEventListener('keydown', e => this.onKey(e));
        }

        row(li) {
            return this.byKey.get(li.dataset.key);
        }

        keyOf(r) {
            return String(this.opts.key(r));
        }

        parentOf(r) {
            const p = this.opts.parentKey(r);
            return p == null ? null : this.byKey.get(String(p)) || null;
        }

        /** Replaces the data and redraws, keeping expansion and selection. */
        setRows(rows) {
            this.rows = rows;
            this.byKey = new Map(rows.map(r => [this.keyOf(r), r]));
            this.kids = new Map();
            rows.forEach(r => {
                const p = this.parentOf(r);
                const k = p ? this.keyOf(p) : '';
                if (!this.kids.has(k)) this.kids.set(k, []);
                this.kids.get(k).push(r);
            });
            if (this.fresh && rows.length) {           // first visit: open the top openDepth levels
                rows.filter(r => this.children(r).length && this.depth(r) < this.opts.openDepth)
                    .forEach(r => this.expanded.add(this.keyOf(r)));
                this.fresh = false;
                this.save();
            }
            this.render();
            return this;
        }

        get(key) {
            return this.byKey.get(String(key));
        }

        children(r) {
            return this.kids.get(r ? this.keyOf(r) : '') || [];
        }

        ancestors(r) {
            const out = [];
            for (let p = r && this.parentOf(r); p; p = this.parentOf(p)) out.unshift(p);
            return out;
        }

        depth(r) {
            return this.ancestors(r).length;
        }

        save() {
            if (this.opts.storageKey) localStorage.setItem(this.opts.storageKey, JSON.stringify([...this.expanded]));
        }

        query() {
            return this.opts.search ? this.opts.search.value.trim().toLowerCase() : '';
        }

        render() {
            const q = this.query();
            const visible = new Set();
            this.rows.forEach(r => {
                if (!this.opts.filter(r)) return;
                if (q && !this.opts.text(r).some(t => t != null && String(t).toLowerCase().includes(q))) return;
                visible.add(this.keyOf(r));
                this.ancestors(r).forEach(a => visible.add(this.keyOf(a)));
            });
            const mark = text => {
                text = text == null ? '' : String(text);
                const at = q ? text.toLowerCase().indexOf(q) : -1;
                return at < 0 ? esc(text) : esc(text.slice(0, at)) + '<mark>' + esc(text.slice(at, at + q.length))
                    + '</mark>' + esc(text.slice(at + q.length));
            };
            const node = (r, depth) => {
                const key = this.keyOf(r);
                const all = this.children(r);
                const kids = all.filter(k => visible.has(this.keyOf(k)));
                const open = kids.length > 0 && (!!q || this.expanded.has(key));
                const selected = key === this.selectedKey;
                return `<li role="treeitem" data-key="${esc(key)}" aria-level="${depth + 1}" aria-selected="${selected}"
                            ${kids.length ? `aria-expanded="${open}"` : ''}>
                    <div class="tree-row${selected ? ' is-selected' : ''} ${this.opts.rowClass ? this.opts.rowClass(r) : ''}" data-row tabindex="-1">
                        ${kids.length ? `<span class="tree-toggle" data-toggle aria-hidden="true">${icon('chevron-right')}</span>`
                                      : '<span class="tree-spacer"></span>'}
                        ${this.opts.row(r, { mark, depth, children: all.length })}
                    </div>
                    ${open ? `<ul role="group">${kids.map(k => node(k, depth + 1)).join('')}</ul>` : ''}
                </li>`;
            };
            const top = this.children(null).filter(r => visible.has(this.keyOf(r)));
            this.root.innerHTML = top.length ? top.map(r => node(r, 0)).join('')
                : `<li class="empty py-12"><span class="empty-icon">${icon('search', 'icon-lg')}</span>
                       <p class="empty-text">${esc(this.opts.emptyText(this.rows.length > 0))}</p></li>`;
            // One tab stop into the tree: the selected row, else the first.
            const stop = this.root.querySelector('.tree-row.is-selected') || this.root.querySelector('.tree-row');
            if (stop) stop.tabIndex = 0;
        }

        rowEl(key) {
            return this.root.querySelector(`li[data-key="${CSS.escape(String(key))}"] > .tree-row`);
        }

        toggle(r, open) {
            const key = this.keyOf(r);
            const want = open === undefined ? !this.expanded.has(key) : open;
            want ? this.expanded.add(key) : this.expanded.delete(key);
            this.save();
            this.render();
            this.rowEl(key)?.focus();
        }

        /** Opens every ancestor of key (and key itself with self=true) and redraws. */
        reveal(key, self) {
            const r = this.get(key);
            if (!r) return;
            this.ancestors(r).forEach(a => this.expanded.add(this.keyOf(a)));
            if (self) this.expanded.add(this.keyOf(r));
            this.save();
            this.render();
        }

        expandAll() {
            this.rows.filter(r => this.children(r).length).forEach(r => this.expanded.add(this.keyOf(r)));
            this.save();
            this.render();
        }

        collapseAll() {
            this.expanded.clear();
            this.save();
            this.render();
        }

        /** Marks the row a page has open (null clears), revealing and scrolling to it. */
        select(key) {
            this.selectedKey = key == null ? null : String(key);
            if (this.selectedKey && !this.rowEl(this.selectedKey)) this.reveal(this.selectedKey);
            this.root.querySelectorAll('.tree-row.is-selected').forEach(el => el.classList.remove('is-selected'));
            this.root.querySelectorAll('li[aria-selected="true"]').forEach(li => li.setAttribute('aria-selected', 'false'));
            const el = this.selectedKey && this.rowEl(this.selectedKey);
            if (el) {
                el.classList.add('is-selected');
                el.parentElement.setAttribute('aria-selected', 'true');
                el.scrollIntoView({ block: 'nearest' });
            }
        }

        onKey(e) {
            const row = e.target.closest('.tree-row');
            if (!row) return;
            const li = row.parentElement;
            const r = this.row(li);
            const all = [...this.root.querySelectorAll('.tree-row')];
            const at = all.indexOf(row);
            const go = el => {
                if (!el) return;
                all.forEach(x => { x.tabIndex = -1; });
                el.tabIndex = 0;
                el.focus();
            };
            const state = li.getAttribute('aria-expanded');
            switch (e.key) {
                case 'ArrowDown': go(all[at + 1]); break;
                case 'ArrowUp':   go(all[at - 1]); break;
                case 'Home':      go(all[0]); break;
                case 'End':       go(all[all.length - 1]); break;
                case 'ArrowRight':
                    if (state === 'false') this.toggle(r, true);
                    else if (state === 'true') go(all[at + 1]);
                    break;
                case 'ArrowLeft':
                    if (state === 'true') this.toggle(r, false);
                    else go(li.parentElement.closest('li[data-key]')?.querySelector(':scope > .tree-row'));
                    break;
                case 'Enter':
                case ' ':
                    if (e.target.closest('[data-action]')) return;
                    this.opts.onSelect && this.opts.onSelect(r);
                    break;
                default: return;
            }
            e.preventDefault();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Document status
    // ------------------------------------------------------------------------------------------

    /**
     * Document status badge - the client twin of fragments/ui :: status. Colour comes from
     * [data-status] in input.css; the label is BusinessDocumentStatus.label()'s rule.
     */
    function status(value) {
        if (!value) return '';
        const name = String(value);
        const label = name.charAt(0) + name.slice(1).toLowerCase();
        return `<span class="badge" data-status="${esc(name)}">${esc(label)}</span>`;
    }

    /** status() with a leading dot, for grids and the review drawer. */
    function statusBadge(value) {
        return value ? status(value).replace('class="badge"', 'class="badge badge-dot"')
                     : '<span class="text-gray-400">—</span>';
    }

    /**
     * The Draft -> Submitted -> Approved -> Completed rail - the client twin of
     * fragments/ui :: workflow, positioned by the same rule as BusinessDocumentStatus.workflowStep():
     * the current stage's index, 4 once past the end, -1 (badge only) when cancelled.
     */
    const WORKFLOW_STEP = { DRAFT: 0, SUBMITTED: 1, REJECTED: 1, APPROVED: 2, PARTIAL: 3, PROCESSING: 3,
                            COMPLETED: 4, CLOSED: 4, CANCELLED: -1 };

    function statusSteps(docStatus) {
        const step = WORKFLOW_STEP[docStatus] ?? 0;
        if (step < 0) return statusBadge(docStatus);
        const label = docStatus.charAt(0) + docStatus.slice(1).toLowerCase();
        return `<ol class="steps" aria-label="Document workflow">${['Draft', 'Submitted', 'Approved', 'Completed'].map((stage, i) => {
            const failed = i === step && docStatus === 'REJECTED';
            const state = i < step ? 'is-done' : i === step ? (failed ? 'is-failed' : 'is-current') : '';
            const dot = failed ? icon('x', 'h-3 w-3') : i < step ? icon('check', 'h-3 w-3') : i + 1;
            return `<li class="step ${state}"${i === step ? ' aria-current="step"' : ''}>`
                + `<span class="step-dot">${dot}</span>${esc(i === step ? label : stage)}</li>`;
        }).join('')}</ol>`;
    }

    // ------------------------------------------------------------------------------------------
    // Fabric document screens (Booking, BPO, RPI, work orders, receipts, deliveries)
    // ------------------------------------------------------------------------------------------

    const num = new Intl.NumberFormat(undefined, { maximumFractionDigits: 4 });
    const formatNumber = v => v == null || v === '' ? '' : num.format(Number(v));

    /** Labels for the fields a document's detail payload may carry; unknown keys are not shown. */
    const DOC_FIELDS = {
        documentDate: 'Date', referenceNo: 'Buyer reference', currency: 'Currency',
        totalQuantity: 'Total quantity', subtotalAmount: 'Amount', revisionNo: 'Revision',
        bookingId: 'Booking', bpoId: 'Production order', deliveryOrderId: 'Delivery order', scheduleId: 'Schedule'
    };
    const GROUP_FIELDS = {
        costingCode: 'Costing no', composition: 'Composition', weaveType: 'Weave type', weaveStyle: 'Weave style',
        finishType: 'Finish', finishWidth: 'Finish width', cuttableWidth: 'Cuttable width', gsm: 'GSM',
        epi: 'EPI', ppi: 'PPI', lightSource: 'Light source'
    };
    const LINE_COLUMNS = [
        ['colorCode', 'Code'], ['colorName', 'Colour'], ['fabricsStyle', 'Style'], ['colorReference', 'Colour ref.'],
        ['strikeOffReference', 'Strike-off'], ['labDipReference', 'Lab dip'], ['loomReference', 'Loom'],
        ['quantity', 'Quantity', true], ['rate', 'Rate', true], ['priceInMeter', 'Price / m', true],
        ['lineAmount', 'Amount', true], ['fulfilled', 'Fulfilled', true], ['outstanding', 'Outstanding', true]
    ];

    /**
     * A document list with filters and a review drawer, driven by the table's own header:
     *   <th data-col="documentNo" data-format="doc|date|num|status|actions" data-sort="…">
     * and by the [data-filter="search|status|from|to"] controls and [data-pager] inside the
     * surrounding [data-doc-screen].
     *
     * new App.DocumentScreen({ kind: 'Booking', api: '/api/booking', table: 'bookingTable',
     *                          form: 'bookingForm', revise: true })
     *
     * Submit / approve / reject call /api/documents/{id}/…; the server decides who may (four-eyes
     * rule included) and its refusal is shown as-is.
     */
    class DocumentScreen {
        constructor(opts) {
            this.opts = opts;
            const table = document.getElementById(opts.table);
            const root = table.closest('[data-doc-screen]') || document;
            const cols = [...table.querySelectorAll('thead th')].map(th => ({ key: th.dataset.col, format: th.dataset.format }));
            const filter = name => root.querySelector(`[data-filter="${name}"]`);

            this.grid = new Grid({
                url: opts.api, table,
                search: filter('search'),
                pager: root.querySelector('[data-pager]'),
                sort: { column: 'documentDate', dir: 'desc' },
                emptyText: `No ${opts.kind.toLowerCase()} documents match these filters.`,
                emptyIcon: 'document',
                params: () => ({ status: filter('status')?.value, from: filter('from')?.value, to: filter('to')?.value }),
                columns: cols.map(col => row => this.cell(row, col)),
                onRowClick: row => this.open(row.id)
            });
            table.addEventListener('click', event => {
                const btn = event.target.closest('[data-open]');
                if (btn) this.open(Number(btn.dataset.open));
            });
            ['status', 'from', 'to'].forEach(name => filter(name)?.addEventListener('change', () => this.grid.reload(true)));
            root.querySelector('[data-filter-reset]')?.addEventListener('click', () => {
                ['search', 'status', 'from', 'to'].forEach(name => { const el = filter(name); if (el) el.value = ''; });
                this.grid.reload(true);
            });
            this.grid.reload(true);

            // The editor stays out of the way until asked for.
            const form = opts.form && document.getElementById(opts.form);
            if (form) {
                form.addEventListener('submit', event => {
                    event.preventDefault();
                    toast('Saving from this editor is not connected yet.', 'warn');
                });
                document.querySelectorAll('[data-editor-open]').forEach(btn => btn.addEventListener('click', () => {
                    form.hidden = false;
                    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    form.querySelector('input:not([type=hidden]),select')?.focus({ preventScroll: true });
                }));
                form.querySelectorAll('[data-editor-close]').forEach(btn => btn.addEventListener('click', () => {
                    form.hidden = true;
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }));
            }
        }

        cell(row, col) {
            const value = row[col.key];
            switch (col.format) {
                case 'doc':    return `<span class="doc-no">${esc(value || 'Unnumbered')}</span>`;
                case 'date':   return esc(fmt.date(value));
                case 'num':    return `<span class="block text-right tabular-nums">${esc(formatNumber(value))}</span>`;
                case 'status': return statusBadge(value);
                case 'actions':
                    return rowActions(rowButton('View', 'eye', `data-open="${esc(row.id)}"`));
                default:       return esc(value);
            }
        }

        async open(id) {
            const drawer = this.drawer || (this.drawer = this.buildDrawer());
            drawer.querySelector('[data-body]').innerHTML =
                '<div class="space-y-3 p-6">' + '<div class="skeleton h-5 w-2/3"></div>'.repeat(4) + '</div>';
            if (!drawer.open) drawer.showModal();
            try {
                const [doc, history] = await Promise.all([
                    api(`${this.opts.api}/${id}`),
                    api(`/api/documents/${id}/history`).catch(() => [])
                ]);
                this.current = doc;
                this.renderDrawer(doc, history || []);
            } catch (error) {
                drawer.close();
                fail(error);
            }
        }

        buildDrawer() {
            const dialog = document.createElement('dialog');
            dialog.className = 'drawer';
            dialog.setAttribute('aria-labelledby', 'docDrawerTitle');
            dialog.innerHTML = `
                <div class="modal-head">
                    <div class="min-w-0">
                        <p class="text-xs font-medium uppercase tracking-wide text-gray-500">${esc(this.opts.kind)}</p>
                        <h2 id="docDrawerTitle" class="modal-title mt-0.5 flex flex-wrap items-center gap-2" data-title></h2>
                    </div>
                    <button type="button" class="btn-icon -mr-2" data-close aria-label="Close">${icon('x', 'icon-lg')}</button>
                </div>
                <div class="modal-body p-0" data-body></div>
                <div class="modal-foot justify-between" data-foot></div>`;
            dialog.addEventListener('click', event => {
                const action = event.target.closest('[data-doc-action]')?.dataset.docAction;
                if (action) this.act(action);
            });
            document.body.appendChild(dialog);
            return dialog;
        }

        renderDrawer(doc, history) {
            const d = this.drawer;
            d.querySelector('[data-title]').innerHTML = `${esc(doc.documentNo || 'Unnumbered')} ${statusBadge(doc.status)}`;

            const facts = Object.entries(DOC_FIELDS).filter(([key]) => doc[key] != null && doc[key] !== '')
                .map(([key, label]) => {
                    let value = doc[key];
                    if (key === 'documentDate') value = fmt.date(value);
                    else if (key.endsWith('Id')) value = '#' + value;
                    else if (typeof value === 'number') value = formatNumber(value);
                    return `<div><dt class="text-xs text-gray-500">${esc(label)}</dt>
                            <dd class="mt-0.5 font-medium text-gray-900 dark:text-white">${esc(value)}</dd></div>`;
                }).join('');

            const groups = (doc.lineGroups || []).map((g, i) => {
                const gFacts = Object.entries(GROUP_FIELDS).filter(([key]) => g[key] != null && g[key] !== '')
                    .map(([key, label]) => `<span><span class="text-gray-500">${esc(label)}</span>
                        <span class="font-medium text-gray-800 dark:text-gray-200">${esc(g[key])}</span></span>`).join('');
                const lines = g.colorLines || [];
                const cols = LINE_COLUMNS.filter(([key]) => lines.some(l => l[key] != null && l[key] !== ''));
                return `<div class="row-card">
                    <div class="row-card-head">
                        <p class="min-w-0 truncate text-sm font-semibold text-gray-900 dark:text-white">
                            Spec ${esc(g.groupNo || i + 1)} · ${esc(g.construction || 'No construction')}</p>
                        <span class="shrink-0 text-xs tabular-nums text-gray-500">${esc(formatNumber(g.groupQuantity))} total</span>
                    </div>
                    ${gFacts ? `<div class="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-xs">${gFacts}</div>` : ''}
                    ${lines.length ? `<div class="table-wrap -mx-4 -mb-4 border-t border-gray-100 dark:border-gray-800"><table class="table-grid table-lines">
                        <thead><tr>${cols.map(([, label, n]) => `<th class="${n ? 'text-right' : ''}">${esc(label)}</th>`).join('')}</tr></thead>
                        <tbody>${lines.map(l => `<tr>${cols.map(([key, , n]) =>
                            `<td class="${n ? 'text-right tabular-nums' : ''}">${esc(n ? formatNumber(l[key]) : l[key])}</td>`).join('')}</tr>`).join('')}
                        </tbody></table></div>` : '<p class="text-xs text-gray-500">No colour lines.</p>'}
                </div>`;
            }).join('');

            const timeline = history.length ? `<ol class="space-y-4">${history.map(h => `
                <li class="flex gap-3">
                    <span class="mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-gray-100 text-gray-500 dark:bg-gray-800">
                        ${icon(h.action === 'REJECT' ? 'x' : h.action === 'APPROVE' ? 'check' : 'send', 'h-3 w-3')}</span>
                    <div class="min-w-0 text-sm">
                        <p><span class="font-medium text-gray-900 dark:text-white">${esc(h.actor || 'System')}</span>
                           <span class="text-gray-500">moved it to</span> ${statusBadge(h.toStatus)}</p>
                        ${h.remarks ? `<p class="mt-1 rounded-lg bg-gray-50 px-3 py-2 text-gray-700 dark:bg-gray-800 dark:text-gray-300">${esc(h.remarks)}</p>` : ''}
                        <p class="mt-0.5 text-xs text-gray-500">${fmt.timeTag(h.at)}</p>
                    </div>
                </li>`).join('')}</ol>`
                : '<p class="text-sm text-gray-500">No approval activity yet.</p>';

            d.querySelector('[data-body]').innerHTML = `
                <section class="form-section">${statusSteps(doc.status)}</section>
                <section class="form-section"><dl class="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">${facts}</dl>
                    ${doc.remarks ? `<p class="mt-4 text-sm text-gray-600 dark:text-gray-300"><span class="text-gray-500">Remarks:</span> ${esc(doc.remarks)}</p>` : ''}
                </section>
                <section class="form-section">
                    <div class="form-section-head"><h3 class="form-section-title">Fabric specifications</h3>
                        <span class="text-xs text-gray-500">${(doc.lineGroups || []).length} spec(s)</span></div>
                    <div class="space-y-3">${groups || '<p class="text-sm text-gray-500">No specifications on this document.</p>'}</div>
                </section>
                <section class="form-section">
                    <div class="form-section-head"><h3 class="form-section-title">Approval history</h3></div>
                    ${timeline}
                </section>`;

            // Offered exactly as BusinessDocumentStatus.allowedNext() permits; the server still decides
            // who may (four-eyes included). A rejected document goes back to draft by being edited.
            const s = doc.status;
            const committed = ['APPROVED', 'PARTIAL', 'PROCESSING', 'COMPLETED', 'CLOSED'].includes(s);
            const actions = [];
            if (s === 'DRAFT') {
                actions.push(`<button type="button" class="btn-primary" data-doc-action="submit">${icon('send')}Submit for approval</button>`);
            }
            if (s === 'SUBMITTED') {
                actions.push(`<button type="button" class="btn-danger-ghost" data-doc-action="reject">${icon('x')}Reject</button>`);
                actions.push(`<button type="button" class="btn-primary" data-doc-action="approve">${icon('check')}Approve</button>`);
            }
            if (s === 'REJECTED') {
                actions.push('<p class="text-sm text-gray-500">Rejected - edit and save it to return it to draft.</p>');
            }
            if (this.opts.revise && committed) {
                actions.push(`<button type="button" class="btn-ghost" data-doc-action="revise">${icon('refresh')}Raise revision</button>`);
            }
            d.querySelector('[data-foot]').innerHTML = `
                <button type="button" class="btn-ghost" data-close>Close</button>
                <div class="flex flex-wrap justify-end gap-2">${actions.join('')}</div>`;
        }

        async act(action) {
            const doc = this.current;
            if (!doc) return;
            const label = doc.documentNo || 'this document';
            let query;
            if (action === 'approve' || action === 'reject') {
                const approve = action === 'approve';
                const values = await formDialog({
                    title: approve ? `Approve ${label}?` : `Reject ${label}?`,
                    message: approve ? 'It is locked for editing once approved.' : 'It goes back to the maker with your reason.',
                    fields: [{ name: 'remarks', label: approve ? 'Remarks (optional)' : 'Reason', type: 'textarea',
                               required: !approve, maxlength: 500 }],
                    confirmText: approve ? 'Approve' : 'Reject', danger: !approve
                });
                if (!values) return;
                query = { remarks: values.remarks };
            } else if (action === 'revise') {
                const values = await formDialog({
                    title: `Raise a revision of ${label}?`, message: 'A new draft is created from this approved document.',
                    fields: [{ name: 'reason', label: 'Reason', type: 'textarea', maxlength: 500 }],
                    confirmText: 'Raise revision'
                });
                if (!values) return;
                query = { reason: values.reason };
            }
            const url = action === 'revise' ? `${this.opts.api}/${doc.id}/revise` : `/api/documents/${doc.id}/${action}`;
            try {
                const result = await api(url, { method: 'POST', query });
                toast({ submit: 'Submitted for approval.', approve: 'Approved.', reject: 'Rejected and returned to the maker.',
                        revise: 'Revision raised as a new draft.' }[action], 'success');
                this.grid.reload();
                this.open(action === 'revise' && result && result.id ? result.id : doc.id);
            } catch (error) {
                fail(error);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Page chrome
    // ------------------------------------------------------------------------------------------

    document.addEventListener('DOMContentLoaded', () => {
        // Mobile sidebar: an off-canvas drawer below lg.
        const sidebar = document.getElementById('sidebar');
        const root = document.documentElement;
        document.querySelectorAll('[data-sidebar-toggle]').forEach(btn => btn.addEventListener('click', () => {
            const open = sidebar.classList.toggle('-translate-x-full') === false;
            document.getElementById('sidebar-scrim')?.toggleAttribute('hidden', !open);
        }));

        // Desktop rail: icons only. The <head> script applies the stored choice before paint.
        const groups = sidebar ? [...sidebar.querySelectorAll('details[data-nav-group]')] : [];
        const closedGroups = new Set(JSON.parse(localStorage.getItem('nav.closed') || '[]'));
        const isRail = () => root.classList.contains('sidebar-rail');
        const syncGroups = () => groups.forEach(g => {
            // The rail shows every icon; the full sidebar restores what the user collapsed, but
            // never hides the module holding the current page.
            g.open = isRail() || !closedGroups.has(g.dataset.navGroup) || !!g.querySelector('[aria-current="page"]');
        });
        syncGroups();
        groups.forEach(g => g.addEventListener('toggle', () => {
            if (isRail()) return;
            g.open ? closedGroups.delete(g.dataset.navGroup) : closedGroups.add(g.dataset.navGroup);
            localStorage.setItem('nav.closed', JSON.stringify([...closedGroups]));
        }));
        document.querySelectorAll('[data-rail-toggle]').forEach(btn => {
            const label = () => {
                const text = isRail() ? 'Expand sidebar' : 'Collapse sidebar';
                btn.title = text;
                btn.setAttribute('aria-label', text);
                btn.querySelector('.nav-label').textContent = text;
            };
            label();
            btn.addEventListener('click', () => {
                root.classList.toggle('sidebar-rail');
                localStorage.setItem('sidebar', isRail() ? 'rail' : 'full');
                syncGroups();
                label();
            });
        });
        // In the rail a group has no room for its children, so its icon opens the group's first page.
        sidebar?.querySelectorAll('summary[data-href]').forEach(summary => summary.addEventListener('click', event => {
            if (isRail() && window.matchMedia('(min-width: 1024px)').matches) {
                event.preventDefault();
                window.location.href = summary.dataset.href;
            }
        }));
        document.querySelectorAll('[data-rail-toggle-proxy]').forEach(btn => btn.addEventListener('click',
            () => document.querySelector('[data-rail-toggle]')?.click()));
        sidebar?.querySelector('.nav-link.is-active, .nav-sublink.is-active')?.scrollIntoView({ block: 'nearest' });

        // Greeting by the user's own clock, not the server's.
        document.querySelectorAll('[data-greeting]').forEach(el => {
            const h = new Date().getHours();
            el.textContent = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
        });

        // "/" focuses the page's search box, as in most admin tools.
        document.addEventListener('keydown', event => {
            if (event.key !== '/' || event.target.closest('input, textarea, select, [contenteditable]')) return;
            const search = document.querySelector('[data-search]');
            if (search) { event.preventDefault(); search.focus(); }
        });

        // Close an open <details> menu when clicking elsewhere.
        document.addEventListener('click', event => {
            document.querySelectorAll('details[data-menu][open]').forEach(menu => {
                if (!menu.contains(event.target)) menu.removeAttribute('open');
            });
        });

        // Ctrl+K / Cmd+K opens the command palette.
        document.addEventListener('keydown', event => {
            if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
                event.preventDefault();
                openCommandPalette();
            }
        });

        // Dark mode toggle button.
        document.querySelectorAll('[data-theme-toggle]').forEach(btn => btn.addEventListener('click', () => {
            const isDark = theme.toggle();
            btn.title = isDark ? 'Switch to light mode' : 'Switch to dark mode';
        }));

        // Command palette button.
        document.querySelectorAll('[data-cmd-trigger]').forEach(btn => btn.addEventListener('click', openCommandPalette));
    });

    window.App = { api, fail, esc, fmt, status, debounce, downloadCsv, icon, rowButton, viewButton, editButton, recordButtons, rowActions, viewMode, viewRecord, toast, form: formDialog, confirm: confirmDialog, tabs, Grid, RemoteSelect, remoteSelects, Tree, DocumentScreen, statusBadge, theme, commandPalette: openCommandPalette };
})();
