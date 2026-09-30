/*
 * The notification centre.
 *
 *  - The bell in the top bar (every page): its badge counts unread notifications and messages, kept
 *    current by polling /api/notifications/summary; anything new since the page last looked is
 *    toasted once. Opening it lists the latest notifications, or conversations, in a panel.
 *  - /notifications (#notifyPage): every notification, filtered and searched, marked read or unread,
 *    cleared; and messages - conversations beside the open thread, with a composer.
 *
 * Everything shown is the signed-in user's own; the server scopes every call to them.
 */
(() => {
    'use strict';

    const esc = s => App.esc(s);
    const POLL_MS = 45000;
    const SEEN_N = 'notify.seenNotification';
    const SEEN_M = 'notify.seenMessage';

    const TONE_ICON = { info: 'info', success: 'check-circle', warn: 'clock', error: 'alert' };
    const KIND_ICON = { APPROVAL_PENDING: 'clipboard-check', APPROVAL_ESCALATED: 'escalate', APPROVAL_REMINDER: 'clock',
                        APPROVAL_OVERDUE: 'alert', APPROVAL_AUTO_DECIDED: 'workflow', APPROVAL_REJECTED: 'x-circle',
                        APPROVAL_RETURNED: 'arrow-right', APPROVAL_APPROVED: 'check-circle', APPROVAL_PROGRESS: 'check' };

    const avatar = (p, cls) => p && p.photoUrl
        ? `<img src="${esc(p.photoUrl)}" alt="" class="avatar avatar-photo ${cls || ''}" width="32" height="32" loading="lazy">`
        : `<span class="avatar ${cls || ''}">${esc((p && p.initials) || '?')}</span>`;

    /** A link to open a message thread, optionally about a document. */
    function threadHref(userId, n) {
        const q = new URLSearchParams({ tab: 'messages', with: userId });
        if (n && n.documentId) {
            q.set('doc', n.documentId);
            if (n.documentLabel) q.set('docLabel', n.documentLabel);
            if (n.link) q.set('link', n.link);
        }
        return `/notifications?${q}`;
    }

    /** One notification as a panel or page row. */
    function notificationItem(n, opts) {
        opts = opts || {};
        const glyph = KIND_ICON[n.kind] || TONE_ICON[n.tone] || 'info';
        const actions = opts.actions ? `<div class="ml-2 flex shrink-0 items-start gap-1">
                ${n.read
                    ? `<button type="button" class="btn-icon btn-sm" data-n-unread="${n.id}" title="Mark unread" aria-label="Mark unread">${App.icon('eye')}</button>`
                    : `<button type="button" class="btn-icon btn-sm" data-n-read="${n.id}" title="Mark read" aria-label="Mark read">${App.icon('check')}</button>`}
                ${n.actorId ? `<a class="btn-icon btn-sm" href="${esc(threadHref(n.actorId, n))}" title="Message ${esc(n.actorName)}" aria-label="Message ${esc(n.actorName)}">${App.icon('message')}</a>` : ''}
                <button type="button" class="btn-icon btn-sm hover:text-red-700" data-n-delete="${n.id}" title="Remove" aria-label="Remove">${App.icon('trash')}</button>
            </div>` : '';
        return `<div class="notify-item${n.read ? '' : ' is-unread'}" data-n-id="${n.id}">
            <span class="notify-icon" data-tone="${esc(n.tone)}">${App.icon(glyph)}</span>
            <a class="min-w-0 flex-1" href="${esc(n.link || '#')}" data-n-open="${n.id}">
                <p class="notify-title">${esc(n.title)}</p>
                ${n.body ? `<p class="notify-body">${esc(n.body)}</p>` : ''}
                <p class="notify-meta"><span>${esc(n.kindLabel)}</span><span aria-hidden="true">·</span>${App.fmt.timeTag(n.at)}
                    ${n.actorName ? `<span aria-hidden="true">·</span><span>by ${esc(n.actorName)}</span>` : ''}</p>
            </a>
            ${actions}
        </div>`;
    }

    function conversationItem(c, active) {
        return `<a class="chat-person${active ? ' is-active' : ''}" href="${esc(threadHref(c.userId))}" data-person="${c.userId}">
            ${avatar(c)}
            <span class="min-w-0 flex-1">
                <span class="flex items-center justify-between gap-2">
                    <span class="truncate text-sm font-medium text-gray-900 dark:text-white">${esc(c.name)}</span>
                    <span class="shrink-0 text-[11px] text-gray-400">${c.lastAt ? App.fmt.relative(c.lastAt) : ''}</span>
                </span>
                <span class="flex items-center justify-between gap-2">
                    <span class="truncate text-xs text-gray-500">${c.lastMine ? 'You: ' : ''}${esc(c.lastBody || '')}</span>
                    ${c.unread ? `<span class="notify-count">${c.unread}</span>` : ''}
                </span>
            </span>
        </a>`;
    }

    const empty = (glyph, text) => `<div class="notify-empty">${App.icon(glyph, 'icon-lg text-gray-300')}<p>${esc(text)}</p></div>`;

    // ============================================================================ the bell

    const bell = document.querySelector('[data-notify]');
    const state = { unreadN: 0, unreadM: 0 };
    const baseTitle = document.title.replace(/^\(\d+\+?\)\s*/, '');

    function setBadge(summary) {
        state.unreadN = summary.unreadNotifications || 0;
        state.unreadM = summary.unreadMessages || 0;
        const total = state.unreadN + state.unreadM;
        document.querySelectorAll('[data-notify-badge]').forEach(b => {
            b.hidden = total === 0;
            b.textContent = total > 99 ? '99+' : String(total);
        });
        document.querySelectorAll('[data-notify-count]').forEach(c => {
            const n = c.dataset.notifyCount === 'messages' ? state.unreadM : state.unreadN;
            c.hidden = n === 0;
            c.textContent = n > 99 ? '99+' : String(n);
        });
        document.title = total ? `(${total > 99 ? '99+' : total}) ${baseTitle}` : baseTitle;
        document.dispatchEvent(new CustomEvent('notify:summary', { detail: summary }));
    }

    async function poll() {
        const seenN = localStorage.getItem(SEEN_N);
        const seenM = localStorage.getItem(SEEN_M);
        let summary;
        try {
            summary = await App.api('/api/notifications/summary', { query: { nAfter: seenN, mAfter: seenM } });
        } catch (ignored) { return; }
        setBadge(summary);
        // What arrived since any page last looked is toasted once, in whichever tab sees it first.
        (summary.fresh || []).slice(0, 3).forEach(f => App.toast(f.title, f.tone === 'error' ? 'warn' : f.tone || 'info'));
        localStorage.setItem(SEEN_N, summary.latestNotificationId);
        localStorage.setItem(SEEN_M, summary.latestMessageId);
    }

    let panelTab = 'notifications';

    async function renderPanel() {
        const list = bell.querySelector('[data-notify-list]');
        bell.querySelectorAll('[data-notify-tab]').forEach(t => {
            const on = t.dataset.notifyTab === panelTab;
            t.classList.toggle('is-active', on);
            t.setAttribute('aria-selected', String(on));
        });
        bell.querySelector('[data-notify-read-all]').hidden = panelTab !== 'notifications';
        list.innerHTML = `<div class="notify-empty">${App.icon('refresh', 'icon-lg animate-spin text-gray-300')}</div>`;
        try {
            if (panelTab === 'notifications') {
                const page = await App.api('/api/notifications', { query: { size: 8 } });
                list.innerHTML = page.rows.length
                    ? page.rows.map(n => notificationItem(n)).join('')
                    : empty('bell', 'Nothing yet. Approvals waiting for you, decisions on your documents and deadlines appear here.');
            } else {
                const people = await App.api('/api/messages/conversations');
                list.innerHTML = people.length
                    ? people.slice(0, 8).map(c => conversationItem(c)).join('')
                    : empty('message', 'No messages yet. Start one from the notification centre.');
            }
        } catch (error) {
            list.innerHTML = empty('alert', error.message || 'Could not load');
        }
    }

    /** Opening a notification reads it, then follows its link. */
    async function openNotification(event, id) {
        const link = event.target.closest('a')?.getAttribute('href');
        event.preventDefault();
        try { await App.api('/api/notifications/read', { method: 'POST', body: { ids: [id] } }); } catch (ignored) { /* still go */ }
        if (link && link !== '#') window.location.href = link;
        else poll();
    }

    if (bell) {
        bell.addEventListener('toggle', () => { if (bell.open) renderPanel(); });
        bell.addEventListener('click', event => {
            const tab = event.target.closest('[data-notify-tab]');
            if (tab) { event.preventDefault(); panelTab = tab.dataset.notifyTab; renderPanel(); return; }
            const open = event.target.closest('[data-n-open]');
            if (open) { openNotification(event, Number(open.dataset.nOpen)); return; }
            if (event.target.closest('[data-notify-read-all]')) {
                event.preventDefault();
                App.api('/api/notifications/read-all', { method: 'POST' })
                    .then(() => { renderPanel(); poll(); document.dispatchEvent(new Event('notify:changed')); })
                    .catch(App.fail);
            }
        });
        poll();
        setInterval(() => { if (!document.hidden) poll(); }, POLL_MS);
        document.addEventListener('visibilitychange', () => { if (!document.hidden) poll(); });
    }

    window.Notify = { poll };

    // ============================================================================ the page

    document.addEventListener('DOMContentLoaded', () => {
        const root = document.getElementById('notifyPage');
        if (!root) return;
        const params = new URLSearchParams(window.location.search);

        // ---------------------------------------------------------------- tabs
        const views = { notifications: root.querySelector('[data-view="notifications"]'),
                        messages: root.querySelector('[data-view="messages"]') };
        function showTab(name, push) {
            Object.entries(views).forEach(([k, v]) => { v.hidden = k !== name; });
            root.querySelectorAll('[data-page-tab]').forEach(t => {
                const on = t.dataset.pageTab === name;
                t.classList.toggle('is-active', on);
                t.setAttribute('aria-selected', String(on));
            });
            if (push) {
                const url = new URL(window.location);
                url.search = name === 'messages' ? '?tab=messages' : '';
                history.replaceState(null, '', url);
            }
            if (name === 'messages') loadConversations();
        }
        root.querySelectorAll('[data-page-tab]').forEach(t => t.addEventListener('click', () => showTab(t.dataset.pageTab, true)));

        // ---------------------------------------------------------------- notifications
        const list = root.querySelector('[data-n-list]');
        const more = root.querySelector('[data-n-more]');
        const search = root.querySelector('[data-n-search]');
        const totalLabel = root.querySelector('[data-n-total]');
        const filter = { unread: false, category: '', q: '', page: 0 };

        async function loadNotifications(append) {
            if (!append) filter.page = 0;
            try {
                const page = await App.api('/api/notifications', { query: {
                    unread: filter.unread, category: filter.category, q: filter.q, page: filter.page, size: 20 } });
                const html = page.rows.map(n => notificationItem(n, { actions: true })).join('');
                if (append) list.insertAdjacentHTML('beforeend', html);
                else list.innerHTML = html || empty('bell', filter.unread ? 'You have read everything here.' : 'No notifications match.');
                more.hidden = !page.more;
                totalLabel.textContent = `${page.total} notification${page.total === 1 ? '' : 's'}`;
            } catch (error) { App.fail(error); }
        }

        root.querySelectorAll('[data-n-filter]').forEach(b => b.addEventListener('click', () => {
            root.querySelectorAll('[data-n-filter]').forEach(o => o.classList.toggle('is-active', o === b));
            filter.unread = b.dataset.nFilter === 'unread';
            filter.category = b.dataset.nFilter === 'unread' || b.dataset.nFilter === 'all' ? '' : b.dataset.nFilter;
            loadNotifications(false);
        }));
        search.addEventListener('input', App.debounce(() => { filter.q = search.value.trim(); loadNotifications(false); }, 300));
        more.addEventListener('click', () => { filter.page++; loadNotifications(true); });

        const post = (url, ids) => App.api(url, { method: 'POST', body: ids ? { ids } : undefined });
        const refresh = () => { loadNotifications(false); poll(); };

        list.addEventListener('click', async event => {
            const open = event.target.closest('[data-n-open]');
            if (open) { openNotification(event, Number(open.dataset.nOpen)); return; }
            const read = event.target.closest('[data-n-read]');
            const unread = event.target.closest('[data-n-unread]');
            const del = event.target.closest('[data-n-delete]');
            try {
                if (read) await post('/api/notifications/read', [Number(read.dataset.nRead)]);
                else if (unread) await post('/api/notifications/unread', [Number(unread.dataset.nUnread)]);
                else if (del) await post('/api/notifications/delete', [Number(del.dataset.nDelete)]);
                else return;
                refresh();
            } catch (error) { App.fail(error); }
        });
        root.querySelector('[data-n-read-all]').addEventListener('click', () =>
            post('/api/notifications/read-all').then(() => { App.toast('Everything marked read.', 'success'); refresh(); }).catch(App.fail));
        root.querySelector('[data-n-clear]').addEventListener('click', async () => {
            if (!await App.confirm({ title: 'Clear read notifications?', message: 'Every notification you have read is removed. Unread ones stay.',
                    confirmText: 'Clear', danger: true })) return;
            post('/api/notifications/clear-read').then(r => { App.toast(`${r.deleted} removed.`, 'success'); refresh(); }).catch(App.fail);
        });
        document.addEventListener('notify:changed', () => loadNotifications(false));

        // ---------------------------------------------------------------- messages
        const people = root.querySelector('[data-people]');
        const thread = root.querySelector('[data-thread]');
        const scroll = root.querySelector('[data-thread-scroll]');
        const head = root.querySelector('[data-thread-head]');
        const composeForm = root.querySelector('[data-compose]');
        const body = composeForm.querySelector('textarea');
        const aboutChip = root.querySelector('[data-about]');
        const picker = root.querySelector('[data-new-message]');
        const pickerSelect = picker.querySelector('select');
        let withId = params.get('with') ? Number(params.get('with')) : null;
        let about = params.get('doc') ? { documentId: Number(params.get('doc')), documentLabel: params.get('docLabel') || 'a document',
                                          link: params.get('link') } : null;
        let threadPage = 0;
        let threadTimer = null;

        async function loadConversations() {
            try {
                const rows = await App.api('/api/messages/conversations');
                people.innerHTML = rows.length ? rows.map(c => conversationItem(c, c.userId === withId)).join('')
                    : empty('message', 'No conversations yet.');
            } catch (error) { App.fail(error); }
        }

        // Built without whitespace between tags: the text keeps its own line breaks (pre-wrap), not the template's.
        function bubble(m) {
            const doc = m.documentLabel
                ? `<a class="chat-doc" href="${esc(m.link || '#')}">${App.icon('document', 'h-3 w-3')}${esc(m.documentLabel)}</a>` : '';
            const time = `<span class="chat-time" title="${esc(App.fmt.dateTime(m.at))}">${esc(App.fmt.relative(m.at))}${m.mine && m.read ? ' · Seen' : ''}</span>`;
            return `<div class="chat-bubble ${m.mine ? 'is-mine' : 'is-theirs'}">${doc}<span class="chat-text">${esc(m.body)}</span>${time}</div>`;
        }

        async function openThread(userId, older) {
            withId = userId;
            thread.hidden = false;
            root.querySelector('[data-thread-empty]').hidden = true;
            if (!older) threadPage = 0;
            try {
                const t = await App.api(`/api/messages/thread/${userId}`, { query: { page: threadPage } });
                head.innerHTML = `${avatar(t.with)}<div class="min-w-0"><p class="truncate text-sm font-semibold text-gray-900 dark:text-white">${esc(t.with.name)}</p>
                    <p class="truncate text-xs text-gray-500">${esc(t.with.username || '')}</p></div>`;
                const html = (t.more ? `<div class="text-center"><button type="button" class="btn-ghost btn-sm" data-older>Earlier messages</button></div>` : '')
                    + t.messages.slice().reverse().map(bubble).join('');
                if (older) {
                    scroll.querySelector('[data-older]')?.parentElement.remove();
                    const before = scroll.scrollHeight;
                    scroll.insertAdjacentHTML('afterbegin', html);
                    scroll.scrollTop = scroll.scrollHeight - before;
                } else {
                    scroll.innerHTML = html || `<p class="py-10 text-center text-sm text-gray-500">No messages yet - say hello.</p>`;
                    scroll.scrollTop = scroll.scrollHeight;
                }
                people.querySelectorAll('[data-person]').forEach(p => p.classList.toggle('is-active', Number(p.dataset.person) === userId));
                poll();
            } catch (error) { App.fail(error); }
            clearInterval(threadTimer);
            threadTimer = setInterval(() => { if (!document.hidden && withId === userId && !older) refreshThread(); }, 15000);
        }

        /** New messages in the open thread, without losing the reader's place. */
        async function refreshThread() {
            if (!withId || threadPage > 0) return;
            const atBottom = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 40;
            try {
                const t = await App.api(`/api/messages/thread/${withId}`);
                scroll.innerHTML = (t.more ? `<div class="text-center"><button type="button" class="btn-ghost btn-sm" data-older>Earlier messages</button></div>` : '')
                    + t.messages.slice().reverse().map(bubble).join('');
                if (atBottom) scroll.scrollTop = scroll.scrollHeight;
                loadConversations();
            } catch (ignored) { /* the next tick tries again */ }
        }

        scroll.addEventListener('click', event => {
            if (!event.target.closest('[data-older]')) return;
            threadPage++;
            openThread(withId, true);
        });
        people.addEventListener('click', event => {
            const p = event.target.closest('[data-person]');
            if (!p) return;
            event.preventDefault();
            about = null;
            renderAbout();
            openThread(Number(p.dataset.person));
            history.replaceState(null, '', `?tab=messages&with=${p.dataset.person}`);
        });

        function renderAbout() {
            aboutChip.hidden = !about;
            if (about) aboutChip.querySelector('[data-about-label]').textContent = about.documentLabel;
        }
        aboutChip.querySelector('[data-about-clear]').addEventListener('click', () => { about = null; renderAbout(); });

        composeForm.addEventListener('submit', async event => {
            event.preventDefault();
            const text = body.value.trim();
            if (!withId) { App.toast('Choose who the message is for.', 'warn'); return; }
            if (!text) { body.focus(); return; }
            const button = composeForm.querySelector('[type=submit]');
            button.disabled = true;
            try {
                await App.api('/api/messages', { method: 'POST', body: { recipientId: withId, body: text,
                    documentId: about?.documentId ?? null, documentLabel: about?.documentLabel ?? null, link: about?.link ?? null } });
                body.value = '';
                about = null;
                renderAbout();
                await openThread(withId);
                loadConversations();
            } catch (error) { App.fail(error); }
            finally { button.disabled = false; body.focus(); }
        });
        body.addEventListener('keydown', event => {
            if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) composeForm.requestSubmit();
        });

        // New message: pick a person, and their thread opens.
        root.querySelector('[data-compose-new]').addEventListener('click', () => {
            picker.hidden = !picker.hidden;
            if (!picker.hidden) picker.querySelector('.rselect-trigger, select')?.focus();
        });
        pickerSelect.addEventListener('change', () => {
            if (!pickerSelect.value) return;
            picker.hidden = true;
            const id = Number(pickerSelect.value);
            openThread(id);
            history.replaceState(null, '', `?tab=messages&with=${id}`);
            body.focus();
        });

        // ---------------------------------------------------------------- start
        renderAbout();
        loadNotifications(false);
        showTab(params.get('tab') === 'messages' ? 'messages' : 'notifications', false);
        if (withId) openThread(withId);
        if (params.has('compose')) picker.hidden = false;
    });
})();
