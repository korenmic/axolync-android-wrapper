let firstProcessAt = 0;

self.onmessage = async (event) => {
  const msg = event.data;
  if (msg.type === 'init') {
    firstProcessAt = 0;
    return;
  }
  if (msg.type === 'process') {
    if (!firstProcessAt) firstProcessAt = Date.now();
    const elapsed = Date.now() - firstProcessAt;
    const result = elapsed >= 3000
      ? {
          songId: 'house-of-the-rising-sun-demo',
          title: 'House of the Rising Sun',
          artist: 'Anonimo (Instrumental Demo)',
          confidence: 0.95,
        }
      : null;
    self.postMessage({ type: 'result', requestId: msg.requestId, sessionId: msg.sessionId, result });
    return;
  }
  if (msg.type === 'dispose') {
    firstProcessAt = 0;
  }
};
