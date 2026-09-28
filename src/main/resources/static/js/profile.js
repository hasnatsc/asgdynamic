/*
 * My profile (templates/account/profile.html). The page arrives rendered; this handles:
 *  - the photo: picked or dropped, shrunk in the browser before it is sent (a 6 MB phone photo goes
 *    up as ~150 KB), shown at once, then replaced by what the server stored - which also updates
 *    the avatar in the header without a reload;
 *  - the details form: Save and Discard only when something changed, errors from the server shown
 *    as they are sent.
 * The server does its own resizing and checking (ProfilePhotoProcessor); the browser's shrinking
 * only saves the upload, so a picture the browser cannot decode is sent as it is.
 */
(function () {
    'use strict';

    const page = document.querySelector('[data-profile-page]');
    if (!page) return;

    const API = '/api/account/profile';
    const MAX_BYTES = 5 * 1024 * 1024;
    const SEND_SIDE = 1024;                 // the server keeps 512; twice that leaves it room to crop and sharpen
    const FIELDS = ['fullName', 'email', 'phone', 'designation', 'department', 'bio'];

    const $ = (sel, root) => (root || page).querySelector(sel);
    const frame = $('[data-photo-frame]');
    const busy = $('[data-photo-busy]');
    const input = $('[data-photo-input]');
    const drop = $('[data-photo-drop]');
    const removeBtn = $('[data-photo-remove]');
    const form = $('[data-profile-form]');
    const saveBtn = $('[data-form-save]');
    const resetBtn = $('[data-form-reset]');
    const formState = $('[data-form-state]');
    const bio = form.elements.bio;
    const bioCount = $('[data-bio-count]');
    let uploading = false;

    // ------------------------------------------------------------------------------ photo

    input.addEventListener('change', () => {
        if (input.files && input.files[0]) upload(input.files[0]);
        input.value = '';
    });
    ['dragenter', 'dragover'].forEach(type => drop.addEventListener(type, e => {
        e.preventDefault();
        drop.classList.add('is-over');
    }));
    ['dragleave', 'dragend', 'drop'].forEach(type => drop.addEventListener(type, () => drop.classList.remove('is-over')));
    drop.addEventListener('drop', e => {
        e.preventDefault();
        const file = [...(e.dataTransfer?.files || [])].find(f => f.type.startsWith('image/'));
        if (file) upload(file);
        else App.toast('Drop a picture - a JPEG or PNG photo.', 'warn');
    });

    async function upload(file) {
        if (uploading) return;
        if (!file.type.startsWith('image/')) {
            App.toast('That file is not a picture. Choose a JPEG or PNG photo.', 'warn');
            return;
        }
        uploading = true;
        const previous = frame.querySelector('[data-photo-img], [data-photo-initials]');
        let preview = null;
        try {
            const shrunk = await shrink(file);
            if (shrunk.size > MAX_BYTES) throw new Error('The picture is larger than 5 MB. Choose a smaller one.');
            preview = URL.createObjectURL(shrunk);
            showPhoto(preview);
            busy.hidden = false;
            const body = new FormData();
            body.append('photo', shrunk, 'photo.jpg');
            const profile = await App.api(`${API}/photo`, { method: 'POST', body });
            render(profile);
            App.toast(`Photo saved - ${Math.round(profile.photo.storedBytes / 1024)} KB, from ${kb(file.size)}.`, 'success');
        } catch (e) {
            frame.querySelectorAll('[data-photo-img], [data-photo-initials]').forEach(el => el.remove());
            if (previous) frame.prepend(previous);
            App.fail(e);
        } finally {
            busy.hidden = true;
            uploading = false;
            if (preview) setTimeout(() => URL.revokeObjectURL(preview), 1000);
        }
    }

    /**
     * A centred square of at most SEND_SIDE px as JPEG - what the server would crop to anyway, so
     * nothing it keeps is lost. createImageBitmap applies the camera's orientation. Anything the
     * browser cannot decode (an old browser, an odd format) goes up untouched.
     */
    async function shrink(file) {
        if (!window.createImageBitmap || !HTMLCanvasElement.prototype.toBlob) return file;
        let bitmap;
        try {
            bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
        } catch (ignored) {
            return file;
        }
        const side = Math.min(bitmap.width, bitmap.height);
        const out = Math.min(side, SEND_SIDE);
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = out;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#fff';                               // transparency on white, as the server does
        ctx.fillRect(0, 0, out, out);
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, out, out);
        bitmap.close && bitmap.close();
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9));
        return blob && blob.size < file.size ? blob : file;
    }

    removeBtn.addEventListener('click', async () => {
        if (!await App.confirm({ title: 'Remove your photo?', message: 'Your initials are shown instead until you add another.',
                confirmText: 'Remove', danger: true })) return;
        try {
            render(await App.api(`${API}/photo`, { method: 'DELETE' }));
            App.toast('Photo removed.', 'success');
        } catch (e) {
            App.fail(e);
        }
    });

    function showPhoto(src) {
        frame.querySelectorAll('[data-photo-img], [data-photo-initials]').forEach(el => el.remove());
        const img = new Image(128, 128);
        img.alt = 'Your profile photo';
        img.decoding = 'async';
        img.dataset.photoImg = '';
        img.src = src;
        frame.prepend(img);
    }

    function showInitials() {
        frame.querySelectorAll('[data-photo-img], [data-photo-initials]').forEach(el => el.remove());
        const span = document.createElement('span');
        span.className = 'profile-initials';
        span.dataset.photoInitials = '';
        span.textContent = initials(form.elements.fullName.value || '');
        frame.prepend(span);
    }

    /** The header's avatar, swapped in place: the new thumbnail's URL is new, so it is fetched once. */
    function updateHeaderAvatar(profile) {
        const current = document.querySelector('header [data-avatar], [data-menu] [data-avatar]');
        if (!current) return;
        let next;
        if (profile.photoUrl) {
            next = new Image(32, 32);
            next.className = 'avatar avatar-photo';
            next.alt = '';
            next.src = `/account/photo/${profile.id}?size=thumb&v=${profile.photoVersion}`;
        } else {
            next = document.createElement('span');
            next.className = 'avatar';
            next.textContent = initials(profile.fullName || profile.username);
        }
        next.dataset.avatar = '';
        current.replaceWith(next);
    }

    function initials(name) {
        const parts = name.trim().split(/\s+/).filter(Boolean);
        if (!parts.length) return '?';
        return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
    }

    const kb = bytes => bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;

    // ------------------------------------------------------------------------------ details

    let saved = values();

    function values() {
        const v = {};
        FIELDS.forEach(name => { v[name] = form.elements[name].value.trim(); });
        return v;
    }

    function dirty() {
        const now = values();
        return FIELDS.some(name => now[name] !== saved[name]);
    }

    function refreshState() {
        const changed = dirty();
        saveBtn.disabled = !changed || !form.elements.fullName.value.trim();
        resetBtn.disabled = !changed;
        formState.textContent = changed ? 'Unsaved changes' : '';
        bioCount.textContent = `${bio.value.length} / 500`;
    }

    form.addEventListener('input', e => {
        if (e.target.getAttribute('aria-invalid')) e.target.removeAttribute('aria-invalid');
        refreshState();
    });
    form.addEventListener('reset', e => {
        e.preventDefault();
        FIELDS.forEach(name => { form.elements[name].value = saved[name]; });
        refreshState();
    });
    form.addEventListener('submit', async e => {
        e.preventDefault();
        const email = form.elements.email;
        if (email.value.trim() && !email.checkValidity()) {
            email.setAttribute('aria-invalid', 'true');
            email.focus();
            App.toast('That email address does not look right.', 'warn');
            return;
        }
        saveBtn.disabled = true;
        formState.textContent = 'Saving…';
        try {
            const profile = await App.api(API, { method: 'POST', body: values() });
            render(profile);
            App.toast('Profile saved.', 'success');
        } catch (err) {
            formState.textContent = 'Not saved';
            App.fail(err);
            refreshState();
        }
    });
    window.addEventListener('beforeunload', e => {
        if (dirty()) { e.preventDefault(); e.returnValue = ''; }
    });

    // ------------------------------------------------------------------------------ redraw

    function render(p) {
        FIELDS.forEach(name => { form.elements[name].value = p[name] || ''; });
        saved = values();
        refreshState();

        $('[data-show="fullName"]').textContent = p.fullName || p.username;
        $('[data-show="headline"]').textContent = p.headline || '';
        $('[data-show="completenessText"]').textContent = `${p.completeness}%`;
        const meter = $('[data-meter]');
        meter.setAttribute('aria-valuenow', p.completeness);
        meter.firstElementChild.style.width = `${p.completeness}%`;

        if (p.photoUrl) {
            const img = frame.querySelector('[data-photo-img]');
            if (!img || !img.src.endsWith(p.photoUrl)) showPhoto(p.photoUrl);
        } else {
            showInitials();
        }
        $('[data-drop-title]').textContent = p.photoUrl ? 'Change photo' : 'Add a photo';
        removeBtn.hidden = !p.photoUrl;
        $('[data-photo-facts]').textContent = p.photo
            ? `${p.photo.width} × ${p.photo.height} px · ${Math.round(p.photo.storedBytes / 1024)} KB stored` : '';

        // The header shows the name too.
        document.querySelectorAll('[data-menu] summary .truncate.font-medium, [data-menu] .menu p.font-medium')
            .forEach(el => { el.textContent = p.fullName || p.username; });
        updateHeaderAvatar(p);
    }

    refreshState();
})();
