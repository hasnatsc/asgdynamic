/*
 * ASG FabricERP - shared front-end helpers, exposed as window.App.
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
            if (!iso) return `<span class="text-slate-400">${esc(empty || 'Never')}</span>`;
            return `<time datetime="${esc(iso)}" title="${esc(fmt.dateTime(iso))}">${esc(fmt.relative(iso))}</time>`;
        },
        initials(name) {
            return String(name || '?').trim().split(/[\s._-]+/).filter(Boolean).slice(0, 2)
                .map(part => part[0].toUpperCase()).join('') || '?';
        }
    };

    function debounce(fn, wait) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), wait);
        };
    }

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

    const TOAST_STYLES = {
        success: 'bg-emerald-600 text-white',
        error:   'bg-red-600 text-white',
        warn:    'bg-amber-500 text-white',
        info:    'bg-slate-800 text-white'
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
        el.className = 'pointer-events-auto animate-toast-in rounded-lg px-4 py-3 text-sm shadow-lg '
            + (TOAST_STYLES[type] || TOAST_STYLES.info);
        el.setAttribute('role', type === 'error' ? 'alert' : 'status');
        el.textContent = message;
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
                            <h2 class="text-base font-semibold text-slate-900">${esc(opts.title)}</h2>
                            ${opts.message ? `<p class="mt-1 text-sm text-slate-500">${esc(opts.message)}</p>` : ''}
                        </div>
                        <button type="button" class="btn-icon" data-close aria-label="Close">&#x2715;</button>
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
                this.tbody.innerHTML = `<tr><td colspan="${this.columnCount}" class="py-10 text-center text-red-600">`
                    + `${esc(error.message || 'Could not load')}</td></tr>`;
            }
        }

        render() {
            this.tbody.style.opacity = '';
            if (!this.rows.length) {
                this.tbody.innerHTML = `<tr><td colspan="${this.columnCount}" class="py-12 text-center text-slate-500">`
                    + `${esc(this.opts.emptyText || 'Nothing to show')}</td></tr>`;
            } else {
                const clickable = this.opts.onRowClick ? ' class="cursor-pointer"' : '';
                this.tbody.innerHTML = this.rows.map((row, i) =>
                    `<tr data-index="${i}"${clickable}>${this.opts.columns.map(col => `<td>${col(row)}</td>`).join('')}</tr>`
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
                <span>Showing <b class="text-slate-700">${from}–${to}</b> of <b class="text-slate-700">${this.total}</b></span>
                <div class="flex items-center gap-2">
                    <label class="flex items-center gap-1">Rows
                        <select data-page-size class="rounded-md border-slate-300 py-1 pl-2 pr-7 text-xs">
                            ${sizes.map(s => `<option${s === this.pageSize ? ' selected' : ''}>${s}</option>`).join('')}
                        </select>
                    </label>
                    <button type="button" class="btn-ghost btn-sm" data-page="-1" ${this.start === 0 ? 'disabled' : ''}>Previous</button>
                    <button type="button" class="btn-ghost btn-sm" data-page="1" ${to >= this.total ? 'disabled' : ''}>Next</button>
                </div>`;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Page chrome
    // ------------------------------------------------------------------------------------------

    document.addEventListener('DOMContentLoaded', () => {
        // Mobile sidebar.
        const sidebar = document.getElementById('sidebar');
        document.querySelectorAll('[data-sidebar-toggle]').forEach(btn => btn.addEventListener('click', () => {
            const open = sidebar.classList.toggle('-translate-x-full') === false;
            document.getElementById('sidebar-scrim')?.toggleAttribute('hidden', !open);
        }));

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
    });

    window.App = { api, fail, esc, fmt, debounce, toast, form: formDialog, confirm: confirmDialog, tabs, Grid };
})();
