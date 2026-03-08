const chunks = [];
let sampleRate = 44100;
let initialized = false;

function floatTo16BitPCM(input) {
  const output = new Int16Array(input.length);
  for (let i = 0; i < input.length; i += 1) {
    const s = Math.max(-1, Math.min(1, input[i]));
    output[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return output;
}

function buildWavBase64FromFloat32(frames, rate) {
  const totalSamples = frames.reduce((sum, frame) => sum + frame.length, 0);
  const pcm = new Int16Array(totalSamples);
  let offset = 0;
  for (const frame of frames) {
    const converted = floatTo16BitPCM(frame);
    pcm.set(converted, offset);
    offset += converted.length;
  }

  const bytesPerSample = 2;
  const blockAlign = bytesPerSample;
  const byteRate = rate * blockAlign;
  const dataSize = pcm.length * bytesPerSample;
  const wavSize = 44 + dataSize;
  const buffer = new ArrayBuffer(wavSize);
  const view = new DataView(buffer);

  const writeString = (position, value) => {
    for (let i = 0; i < value.length; i += 1) {
      view.setUint8(position + i, value.charCodeAt(i));
    }
  };

  writeString(0, 'RIFF');
  view.setUint32(4, 36 + dataSize, true);
  writeString(8, 'WAVE');
  writeString(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, rate, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, 16, true);
  writeString(36, 'data');
  view.setUint32(40, dataSize, true);

  let dataOffset = 44;
  for (let i = 0; i < pcm.length; i += 1) {
    view.setInt16(dataOffset, pcm[i], true);
    dataOffset += 2;
  }

  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

self.onmessage = (event) => {
  const msg = event.data;
  if (msg?.type === 'init') {
    sampleRate = Number(msg.sampleRate) || 44100;
    chunks.length = 0;
    initialized = true;
    self.postMessage({ type: 'inited' });
    return;
  }

  if (!initialized) {
    return;
  }

  if (msg?.type === 'process') {
    const frame = msg.buffer instanceof Float32Array ? msg.buffer : new Float32Array(msg.buffer);
    chunks.push(frame);
    return;
  }

  if (msg?.type === 'flush') {
    const base64Wav = buildWavBase64FromFloat32(chunks, sampleRate);
    self.postMessage({
      type: 'flushResult',
      base64Wav,
      sampleRate,
      chunkCount: chunks.length,
      sampleCount: chunks.reduce((sum, frame) => sum + frame.length, 0),
    });
  }
};
