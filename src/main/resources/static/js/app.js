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

    window.App = { api, fail, esc, fmt, status, debounce, downloadCsv, icon, rowButton, viewButton, editButton, recordButtons, rowActions, viewMode, viewRecord, toast, form: formDialog, confirm: confirmDialog, tabs, Grid, DocumentScreen, statusBadge, theme, commandPalette: openCommandPalette };
})();
