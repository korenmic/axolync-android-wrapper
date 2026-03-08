// Task 42.4 + 46.1: Demo SyncEngine with optional sync-loss simulation
let firstProcessAt = 0;
let simulateSyncLoss = false;
let desyncTimeline = null;

self.onmessage = async (event) => {
  const msg = event.data;
  if (msg.type === 'init') {
    firstProcessAt = 0;
    // Check for sync-loss simulation setting
    try {
      const settings = msg.settings || {};
      simulateSyncLoss = settings.simulateSyncLoss === true;
      // Task 46.3: Timeline: desync at 20s, relock after 5s, desync again at 35s permanently
      if (simulateSyncLoss) {
        desyncTimeline = { firstDesyncAt: 20000, relockAt: 25000, finalDesyncAt: 35000 };
      } else {
        desyncTimeline = null;
      }
    } catch {
      simulateSyncLoss = false;
      desyncTimeline = null;
    }
    return;
  }
  // Task 46.1: Support runtime settings update
  if (msg.type === 'updateSettings') {
    try {
      const settings = msg.settings || {};
      simulateSyncLoss = settings.simulateSyncLoss === true;
      if (simulateSyncLoss) {
        desyncTimeline = { firstDesyncAt: 20000, relockAt: 25000, finalDesyncAt: 35000 };
      } else {
        desyncTimeline = null;
      }
    } catch {
      simulateSyncLoss = false;
      desyncTimeline = null;
    }
    return;
  }
  if (msg.type === 'process') {
    if (!firstProcessAt) firstProcessAt = Date.now();
    const elapsed = Date.now() - firstProcessAt;
    
    // Task 46.3: Simulate sync-loss timeline if enabled
    if (simulateSyncLoss && desyncTimeline) {
      const { firstDesyncAt, relockAt, finalDesyncAt } = desyncTimeline;
      
      // First desync period: 20s-25s
      if (elapsed >= firstDesyncAt && elapsed < relockAt) {
        const result = {
          songOffsetAtBufferStartMs: 21000,
          confidence: 0.3, // Low confidence to trigger sync-loss
        };
        self.postMessage({ type: 'result', requestId: msg.requestId, sessionId: msg.sessionId, result });
        return;
      }
      
      // Final permanent desync: after 35s
      if (elapsed >= finalDesyncAt) {
        const result = {
          songOffsetAtBufferStartMs: 21000,
          confidence: 0.2, // Very low confidence, never recovers
        };
        self.postMessage({ type: 'result', requestId: msg.requestId, sessionId: msg.sessionId, result });
        return;
      }
    }
    
    const highConfidence = elapsed >= 2500;
    const result = {
      songOffsetAtBufferStartMs: 21000,
      confidence: highConfidence ? 0.93 : 0.83,
    };
    self.postMessage({ type: 'result', requestId: msg.requestId, sessionId: msg.sessionId, result });
    return;
  }
  if (msg.type === 'dispose') {
    firstProcessAt = 0;
    simulateSyncLoss = false;
    desyncTimeline = null;
  }
};
