const DEFAULT_BASE_URL = 'http://127.0.0.1:8787';

function getBaseUrl() {
  if (typeof self.AXOLYNC_OPENAPI_BASE_URL === 'string' && self.AXOLYNC_OPENAPI_BASE_URL.length > 0) {
    return self.AXOLYNC_OPENAPI_BASE_URL;
  }
  return DEFAULT_BASE_URL;
}

async function post(path, payload) {
  const response = await fetch(`${getBaseUrl()}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`OpenAPI SongSense request failed: ${response.status} ${response.statusText} ${body}`.trim());
  }

  return response.json();
}

self.onmessage = async (event) => {
  const raw = event ? event.data : null;
  const msg = raw && typeof raw === 'object' ? raw : {};
  const requestId = typeof msg.requestId === 'string' ? msg.requestId : undefined;
  const sessionId = typeof msg.sessionId === 'string' ? msg.sessionId : undefined;
  const messageType = typeof msg.type === 'string' ? msg.type : '';
  try {
    if (messageType === 'init') {
      await post('/v1/songsense/initialize', {
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
        chunk: {
          bufferStartAudioMs: msg.chunk?.bufferStartAudioMs,
          captureEndAudioMs: msg.chunk?.captureEndAudioMs,
          durationMs: msg.chunk?.durationMs,
          receivedPerfMs: msg.chunk?.receivedPerfMs,
        },
      };
      const json = await post('/v1/songsense/query', payload);
      self.postMessage({
        type: 'result',
        requestId,
        sessionId,
        result: json.result ?? null,
      });
      return;
    }

    if (messageType === 'dispose') {
      await post('/v1/songsense/dispose', {
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

    throw new Error(`Unsupported SongSense worker message type: ${String(messageType || '<empty>')}`);
  } catch (error) {
    self.postMessage({
      type: 'error',
      requestId,
      sessionId,
      error: error instanceof Error ? error.message : String(error),
    });
  }
};
