const LRCLIB_GET_URL = 'https://lrclib.net/api/get';
const LRCLIB_SEARCH_URL = 'https://lrclib.net/api/search';
function normalizeText(value) {
    return value.trim().replace(/\s+/g, ' ');
}
function parseSongIdentity(songId) {
    const raw = normalizeText(songId);
    if (!raw)
        return null;
    for (const separator of ['::', ' - ', ' — ', '|', '/']) {
        if (!raw.includes(separator))
            continue;
        const [left, right] = raw.split(separator, 2);
        const artist = normalizeText(left);
        const title = normalizeText(right);
        if (artist && title) {
            return { artist, title };
        }
    }
    const colonParts = raw.split(':').map(normalizeText).filter(Boolean);
    if (colonParts.length >= 3 && colonParts[0].includes('.')) {
        return {
            artist: normalizeText(colonParts.slice(2).join(':')),
            title: colonParts[1],
        };
    }
    if (colonParts.length === 2) {
        return {
            artist: colonParts[0],
            title: colonParts[1],
        };
    }
    return null;
}
function buildUrl(baseUrl, params) {
    const url = new URL(baseUrl);
    for (const [key, value] of Object.entries(params)) {
        if (value.trim()) {
            url.searchParams.set(key, value);
        }
    }
    return url.toString();
}
function parseLrcTimestamp(value) {
    const match = value.match(/^(\d{2}):(\d{2})(?:[.:](\d{1,3}))?$/);
    if (!match)
        return null;
    const minutes = Number.parseInt(match[1], 10);
    const seconds = Number.parseInt(match[2], 10);
    const fractionRaw = match[3] ?? '0';
    const fractionMs = fractionRaw.length === 1
        ? Number.parseInt(fractionRaw, 10) * 100
        : fractionRaw.length === 2
            ? Number.parseInt(fractionRaw, 10) * 10
            : Number.parseInt(fractionRaw.slice(0, 3), 10);
    return (minutes * 60000) + (seconds * 1000) + fractionMs;
}
function parseLrcToLyricData(raw) {
    const entries = raw
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
        .map((line) => {
        const match = line.match(/^\[([^\]]+)\](.*)$/);
        if (!match)
            return null;
        const inSongMs = parseLrcTimestamp(match[1]);
        const text = normalizeText(match[2] ?? '');
        if (inSongMs === null || !text)
            return null;
        return { inSongMs, text };
    })
        .filter((entry) => entry !== null);
    if (entries.length === 0)
        return null;
    return {
        granularity: 'line',
        units: entries.map((entry, index) => {
            const nextStart = entries[index + 1]?.inSongMs ?? (entry.inSongMs + 2500);
            return {
                text: entry.text,
                inSongMs: entry.inSongMs,
                durationMs: Math.max(200, nextStart - entry.inSongMs),
            };
        }),
    };
}
async function readJson(response) {
    try {
        return await response.json();
    }
    catch {
        return null;
    }
}
function pickBestRecord(records, identity) {
    const artist = identity.artist.toLowerCase();
    const title = identity.title.toLowerCase();
    for (const record of records) {
        if (!record || typeof record !== 'object')
            continue;
        const row = record;
        const rowArtist = String(row.artistName ?? '').trim().toLowerCase();
        const rowTitle = String(row.trackName ?? '').trim().toLowerCase();
        if (rowArtist === artist && rowTitle === title) {
            return row;
        }
    }
    const first = records.find((record) => record && typeof record === 'object');
    return first ? first : null;
}
function extractSyncedLyrics(record) {
    if (!record || typeof record !== 'object')
        return null;
    const syncedLyrics = String(record.syncedLyrics ?? '').trim();
    if (!syncedLyrics)
        return null;
    return parseLrcToLyricData(syncedLyrics);
}
async function fetchRecord(url, fetchImpl) {
    const response = await fetchImpl(url, { cache: 'no-store' });
    if (!response.ok) {
        throw new Error(`LRCLIB request failed: ${response.status} ${response.statusText}`.trim());
    }
    return readJson(response);
}
async function fetchDirectLrcLibLyrics(songId, fetchImpl = fetch) {
    const identity = parseSongIdentity(songId);
    if (!identity) {
        throw new Error(`LRCLIB fallback could not parse song identity from "${songId}"`);
    }
    try {
        const getRecord = await fetchRecord(buildUrl(LRCLIB_GET_URL, {
            artist_name: identity.artist,
            track_name: identity.title,
        }), fetchImpl);
        const payload = extractSyncedLyrics(getRecord);
        if (payload)
            return payload;
    }
    catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        if (!message.includes('404')) {
            throw error;
        }
    }
    const searchRecord = await fetchRecord(buildUrl(LRCLIB_SEARCH_URL, {
        artist_name: identity.artist,
        track_name: identity.title,
    }), fetchImpl);
    const searchRows = Array.isArray(searchRecord) ? searchRecord : [];
    let picked = pickBestRecord(searchRows, identity);
    if (!picked && identity.artist) {
        const titleOnlyRecord = await fetchRecord(buildUrl(LRCLIB_SEARCH_URL, {
            track_name: identity.title,
        }), fetchImpl);
        picked = pickBestRecord(Array.isArray(titleOnlyRecord) ? titleOnlyRecord : [], identity);
    }
    const payload = extractSyncedLyrics(picked);
    if (!payload) {
        throw new Error(`LRCLIB fallback returned no synced lyrics for "${songId}"`);
    }
    return payload;
}

function isErrorEnvelope(value) {
    return Boolean(value
        && typeof value === 'object'
        && typeof value.code === 'string'
        && typeof value.message === 'string'
        && typeof value.retryable === 'boolean');
}
function formatErrorEnvelope(value) {
    const details = value.details && typeof value.details === 'object'
        ? Object.entries(value.details)
            .map(([key, entry]) => `${key}=${String(entry)}`)
            .join(' ')
        : '';
    return details
        ? `LyricFlow bridge backend error [${value.code}] ${value.message} | ${details}`
        : `LyricFlow bridge backend error [${value.code}] ${value.message}`;
}
function resolveEndpoint(baseUrl, path) {
    const normalizedPath = String(path).replace(/^\/+/, '');
    const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
    if (/^https?:\/\//i.test(normalizedBase)) {
        return new URL(normalizedPath, normalizedBase).toString();
    }
    const origin = typeof self.location?.origin === 'string' ? self.location.origin : 'http://127.0.0.1';
    return new URL(normalizedPath, new URL(normalizedBase, origin)).toString();
}
async function postJson(baseUrl, path, payload, headers = {}) {
    const response = await fetch(resolveEndpoint(baseUrl, path), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...headers,
        },
        body: JSON.stringify(payload),
    });
    const text = await response.text().catch(() => '');
    let parsed = null;
    if (text) {
        try {
            parsed = JSON.parse(text);
        }
        catch {
            parsed = null;
        }
    }
    if (isErrorEnvelope(parsed)) {
        throw new Error(formatErrorEnvelope(parsed));
    }
    if (!response.ok) {
        throw new Error(`LyricFlow bridge request failed: ${response.status} ${response.statusText} ${text}`.trim());
    }
    if (!parsed || typeof parsed !== 'object') {
        throw new Error('LyricFlow bridge request failed: invalid JSON response');
    }
    return parsed;
}
self.onmessage = async (event) => {
    const msg = event?.data && typeof event.data === 'object' ? event.data : {};
    const requestId = typeof msg.requestId === 'string' ? msg.requestId : undefined;
    const sessionId = typeof msg.sessionId === 'string' ? msg.sessionId : undefined;
    const baseUrl = typeof msg.baseUrl === 'string' && msg.baseUrl.trim() ? msg.baseUrl.trim() : '';
    const runtimeMode = msg.runtimeMode === 'android-wrapper' ? 'android-wrapper' : 'hosted-web';
    try {
        if (!baseUrl && runtimeMode !== 'android-wrapper') {
            throw new Error('LyricFlow bridge worker missing baseUrl');
        }
        if (runtimeMode === 'android-wrapper' && !baseUrl) {
            if (msg.type === 'init' || msg.type === 'dispose') {
                self.postMessage({
                    type: 'result',
                    requestId,
                    sessionId,
                    result: { ok: true, mode: 'direct-lrclib' },
                });
                return;
            }
            if (msg.type === 'process') {
                const result = await fetchDirectLrcLibLyrics(String(msg.songId ?? ''));
                self.postMessage({ type: 'result', requestId, sessionId, result });
                return;
            }
        }
        if (msg.type === 'init') {
            const result = await postJson(baseUrl, '/v1/lyricflow/initialize', {
                requestId,
                sessionId,
                sessionContext: msg.sessionContext,
            });
            self.postMessage({ type: 'result', requestId, sessionId, result });
            return;
        }
        if (msg.type === 'process') {
            const headers = {};
            if (Array.isArray(msg.settings?.adapterIds) && msg.settings.adapterIds.length > 0) {
                headers['X-Axolync-Adapter-Ids'] = msg.settings.adapterIds.join(',');
            }
            if (msg.settings?.parallelRace === true) {
                headers['X-Axolync-Parallel-Race'] = 'true';
            }
            const result = await postJson(baseUrl, '/v1/lyricflow/get-lyrics', {
                requestId,
                sessionId,
                songId: msg.songId,
                granularity: msg.granularity,
                chunkMeta: msg.chunkMeta,
            }, headers);
            self.postMessage({ type: 'result', requestId, sessionId, result });
            return;
        }
        if (msg.type === 'dispose') {
            const result = await postJson(baseUrl, '/v1/lyricflow/dispose', {
                requestId,
                sessionId,
            });
            self.postMessage({ type: 'result', requestId, sessionId, result });
            return;
        }
        throw new Error(`Unsupported LyricFlow bridge worker message type: ${String(msg.type ?? '<empty>')}`);
    }
    catch (error) {
        self.postMessage({
            type: 'error',
            requestId,
            sessionId,
            error: error instanceof Error ? error.message : String(error),
        });
    }
};
