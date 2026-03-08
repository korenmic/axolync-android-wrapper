const DEFAULT_BASE_URL = 'http://127.0.0.1:8787';

function isHttpOrigin(value) {
  return typeof value === 'string' && /^https?:\/\//i.test(value);
}

function getBaseUrls() {
  const candidates = [];
  if (typeof self.AXOLYNC_OPENAPI_BASE_URL === 'string' && self.AXOLYNC_OPENAPI_BASE_URL.length > 0) {
    candidates.push(self.AXOLYNC_OPENAPI_BASE_URL);
  }

  const origin = self.location && typeof self.location.origin === 'string'
    ? self.location.origin
    : '';
  if (isHttpOrigin(origin)) {
    candidates.push(origin);
  }
  candidates.push(DEFAULT_BASE_URL);
  return Array.from(new Set(candidates));
}

function isErrorEnvelope(value) {
  return Boolean(
    value
    && typeof value === 'object'
    && typeof value.code === 'string'
    && typeof value.message === 'string'
    && typeof value.retryable === 'boolean'
  );
}

function formatErrorEnvelope(value) {
  const details = value && typeof value.details === 'object' && value.details !== null
    ? Object.entries(value.details)
        .filter(([key, val]) => typeof key === 'string' && typeof val === 'string')
        .map(([key, val]) => `${key}=${val}`)
    : [];
  const suffix = details.length ? ` | ${details.join(' ')}` : '';
  return `LyricFlow backend error [${value.code}] ${value.message}${suffix}`;
}

async function postToBase(baseUrl, path, payload) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
  });
  const bodyText = await response.text().catch(() => '');
  let json = null;
  if (bodyText) {
    try {
      json = JSON.parse(bodyText);
    } catch {
      json = null;
    }
  }

  if (isErrorEnvelope(json)) {
    throw new Error(formatErrorEnvelope(json));
  }

  if (!response.ok) {
    throw new Error(`OpenAPI LyricFlow request failed: ${response.status} ${response.statusText} ${bodyText}`.trim());
  }

  if (!json || typeof json !== 'object') {
    throw new Error('OpenAPI LyricFlow request failed: invalid JSON response');
  }
  return json;
}

async function post(path, payload) {
  const baseUrls = getBaseUrls();
  const failures = [];
  for (const baseUrl of baseUrls) {
    try {
      return await postToBase(baseUrl, path, payload);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message.startsWith('LyricFlow backend error')) {
        throw new Error(message);
      }
      failures.push(`[${baseUrl}] ${message}`);
    }
  }
  throw new Error(`OpenAPI LyricFlow request failed across base URLs: ${failures.join(' ; ')}`);
}

self.onmessage = async (event) => {
  const raw = event ? event.data : null;
  const msg = raw && typeof raw === 'object' ? raw : {};
  const requestId = typeof msg.requestId === 'string' ? msg.requestId : undefined;
  const sessionId = typeof msg.sessionId === 'string' ? msg.sessionId : undefined;
  const messageType = typeof msg.type === 'string' ? msg.type : '';
  try {
    if (messageType === 'init') {
      await post('/v1/lyricflow/initialize', {
        requestId,
        sessionId,
        sessionContext: msg.sessionContext,
      });
      self.postMessage({
        type: 'result',
        requestId,
        sessionId,
        result: { ok: true },
      });
      return;
    }

    if (messageType === 'process') {
      const payload = {
        requestId,
        sessionId,
        songId: msg.songId,
        granularity: msg.granularity,
        chunkMeta: msg.chunkMeta ?? msg.chunk ?? undefined,
        settings: msg.settings ?? undefined,
      };
      const json = await post('/v1/lyricflow/get-lyrics', payload);
      self.postMessage({
        type: 'result',
        requestId,
        sessionId,
        result: json.result,
      });
      return;
    }

    if (messageType === 'dispose') {
      await post('/v1/lyricflow/dispose', {
        requestId,
        sessionId,
      });
      self.postMessage({
        type: 'result',
        requestId,
        sessionId,
        result: { ok: true },
      });
      return;
    }

    throw new Error(`Unsupported LyricFlow worker message type: ${String(messageType || '<empty>')}`);
  } catch (error) {
    self.postMessage({
      type: 'error',
      requestId,
      sessionId,
      error: error instanceof Error ? error.message : String(error),
    });
  }
};
