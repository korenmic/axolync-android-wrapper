const HOUSE_LINE_UNITS = [
  { text: 'There is a house in New Orleans', inSongMs: 14000, durationMs: 6500 },
  { text: 'They call the Rising Sun', inSongMs: 20500, durationMs: 6500 },
  { text: "And it's been the ruin of many a poor boy", inSongMs: 27000, durationMs: 7000 },
  { text: "And God, I know I'm one", inSongMs: 34000, durationMs: 7500 },
  { text: 'My mother was a tailor', inSongMs: 41500, durationMs: 6500 },
  { text: 'She sewed my new blue jeans', inSongMs: 48000, durationMs: 6500 },
  { text: "My father was a gamblin' man", inSongMs: 54500, durationMs: 6500 },
  { text: 'Down in New Orleans', inSongMs: 61000, durationMs: 7500 },
  { text: 'Now the only thing a gambler needs', inSongMs: 68500, durationMs: 6500 },
  { text: 'Is a suitcase and a trunk', inSongMs: 75000, durationMs: 6500 },
  { text: "And the only time he's satisfied", inSongMs: 81500, durationMs: 6500 },
  { text: "Is when he's on a drunk", inSongMs: 88000, durationMs: 7500 },
  { text: 'Oh mother tell your children', inSongMs: 116500, durationMs: 6500 },
  { text: 'Not to do what I have done', inSongMs: 123000, durationMs: 6500 },
  { text: 'Spend your lives in sin and misery', inSongMs: 129500, durationMs: 7000 },
  { text: 'In the House of the Rising Sun', inSongMs: 136500, durationMs: 8000 },
  { text: 'Well, there is a house in New Orleans', inSongMs: 144500, durationMs: 6500 },
  { text: 'They call the Rising Sun', inSongMs: 151000, durationMs: 6500 },
  { text: "And it's been the ruin of many a poor boy", inSongMs: 157500, durationMs: 7000 },
  { text: "And God, I know I'm one", inSongMs: 164500, durationMs: 7000 },
];

const HOUSE_WORD_UNITS = [
  { text: 'There', inSongMs: 14000, durationMs: 1300 },
  { text: 'is', inSongMs: 15300, durationMs: 520 },
  { text: 'a', inSongMs: 15820, durationMs: 260 },
  { text: 'house', inSongMs: 16080, durationMs: 1300 },
  { text: 'in', inSongMs: 17380, durationMs: 520 },
  { text: 'New', inSongMs: 17900, durationMs: 780 },
  { text: 'Orleans\n', inSongMs: 18680, durationMs: 1820 },
  { text: 'They', inSongMs: 20500, durationMs: 1300 },
  { text: 'call', inSongMs: 21800, durationMs: 1300 },
  { text: 'the', inSongMs: 23100, durationMs: 975 },
  { text: 'Rising', inSongMs: 24075, durationMs: 1950 },
  { text: 'Sun\n', inSongMs: 26025, durationMs: 975 },
  { text: 'And', inSongMs: 27000, durationMs: 656 },
  { text: "it's", inSongMs: 27656, durationMs: 875 },
  { text: 'been', inSongMs: 28531, durationMs: 875 },
  { text: 'the', inSongMs: 29406, durationMs: 656 },
  { text: 'ruin', inSongMs: 30062, durationMs: 875 },
  { text: 'of', inSongMs: 30937, durationMs: 438 },
  { text: 'many', inSongMs: 31375, durationMs: 875 },
  { text: 'a', inSongMs: 32250, durationMs: 219 },
  { text: 'poor', inSongMs: 32469, durationMs: 875 },
  { text: 'boy\n', inSongMs: 33344, durationMs: 656 },
  { text: 'And', inSongMs: 34000, durationMs: 1324 },
  { text: 'God,', inSongMs: 35324, durationMs: 1324 },
  { text: 'I', inSongMs: 36648, durationMs: 441 },
  { text: 'know', inSongMs: 37089, durationMs: 1765 },
  { text: "I'm", inSongMs: 38854, durationMs: 1324 },
  { text: 'one\n', inSongMs: 40178, durationMs: 1322 },
  { text: 'My', inSongMs: 41500, durationMs: 722 },
  { text: 'mother', inSongMs: 42222, durationMs: 2167 },
  { text: 'was', inSongMs: 44389, durationMs: 1083 },
  { text: 'a', inSongMs: 45472, durationMs: 361 },
  { text: 'tailor\n', inSongMs: 45833, durationMs: 2167 },
  { text: 'She', inSongMs: 48000, durationMs: 886 },
  { text: 'sewed', inSongMs: 48886, durationMs: 1477 },
  { text: 'my', inSongMs: 50363, durationMs: 591 },
  { text: 'new', inSongMs: 50954, durationMs: 886 },
  { text: 'blue', inSongMs: 51840, durationMs: 1182 },
  { text: 'jeans\n', inSongMs: 53022, durationMs: 1478 },
  { text: 'My', inSongMs: 54500, durationMs: 565 },
  { text: 'father', inSongMs: 55065, durationMs: 1696 },
  { text: 'was', inSongMs: 56761, durationMs: 848 },
  { text: 'a', inSongMs: 57609, durationMs: 283 },
  { text: "gamblin'", inSongMs: 57892, durationMs: 2261 },
  { text: 'man\n', inSongMs: 60153, durationMs: 847 },
  { text: 'Down', inSongMs: 61000, durationMs: 1875 },
  { text: 'in', inSongMs: 62875, durationMs: 938 },
  { text: 'New', inSongMs: 63813, durationMs: 1406 },
  { text: 'Orleans\n', inSongMs: 65219, durationMs: 3281 },
  { text: 'Now', inSongMs: 68500, durationMs: 696 },
  { text: 'the', inSongMs: 69196, durationMs: 696 },
  { text: 'only', inSongMs: 69892, durationMs: 929 },
  { text: 'thing', inSongMs: 70821, durationMs: 1161 },
  { text: 'a', inSongMs: 71982, durationMs: 232 },
  { text: 'gambler', inSongMs: 72214, durationMs: 1625 },
  { text: 'needs\n', inSongMs: 73839, durationMs: 1161 },
  { text: 'Is', inSongMs: 75000, durationMs: 650 },
  { text: 'a', inSongMs: 75650, durationMs: 325 },
  { text: 'suitcase', inSongMs: 75975, durationMs: 2600 },
  { text: 'and', inSongMs: 78575, durationMs: 975 },
  { text: 'a', inSongMs: 79550, durationMs: 325 },
  { text: 'trunk\n', inSongMs: 79875, durationMs: 1625 },
  { text: 'And', inSongMs: 81500, durationMs: 722 },
  { text: 'the', inSongMs: 82222, durationMs: 722 },
  { text: 'only', inSongMs: 82944, durationMs: 963 },
  { text: 'time', inSongMs: 83907, durationMs: 963 },
  { text: "he's", inSongMs: 84870, durationMs: 963 },
  { text: 'satisfied\n', inSongMs: 85833, durationMs: 2167 },
  { text: 'Is', inSongMs: 88000, durationMs: 833 },
  { text: 'when', inSongMs: 88833, durationMs: 1667 },
  { text: "he's", inSongMs: 90500, durationMs: 1667 },
  { text: 'on', inSongMs: 92167, durationMs: 833 },
  { text: 'a', inSongMs: 93000, durationMs: 417 },
  { text: 'drunk\n', inSongMs: 93417, durationMs: 2083 },
  { text: 'Oh', inSongMs: 116500, durationMs: 542 },
  { text: 'mother', inSongMs: 117042, durationMs: 1625 },
  { text: 'tell', inSongMs: 118667, durationMs: 1083 },
  { text: 'your', inSongMs: 119750, durationMs: 1083 },
  { text: 'children\n', inSongMs: 120833, durationMs: 2167 },
  { text: 'Not', inSongMs: 123000, durationMs: 975 },
  { text: 'to', inSongMs: 123975, durationMs: 650 },
  { text: 'do', inSongMs: 124625, durationMs: 650 },
  { text: 'what', inSongMs: 125275, durationMs: 1300 },
  { text: 'I', inSongMs: 126575, durationMs: 325 },
  { text: 'have', inSongMs: 126900, durationMs: 1300 },
  { text: 'done\n', inSongMs: 128200, durationMs: 1300 },
  { text: 'Spend', inSongMs: 129500, durationMs: 1250 },
  { text: 'your', inSongMs: 130750, durationMs: 1000 },
  { text: 'lives', inSongMs: 131750, durationMs: 1250 },
  { text: 'in', inSongMs: 133000, durationMs: 500 },
  { text: 'sin', inSongMs: 133500, durationMs: 750 },
  { text: 'and', inSongMs: 134250, durationMs: 750 },
  { text: 'misery\n', inSongMs: 135000, durationMs: 1500 },
  { text: 'In', inSongMs: 136500, durationMs: 667 },
  { text: 'the', inSongMs: 137167, durationMs: 1000 },
  { text: 'House', inSongMs: 138167, durationMs: 1667 },
  { text: 'of', inSongMs: 139834, durationMs: 667 },
  { text: 'the', inSongMs: 140501, durationMs: 1000 },
  { text: 'Rising', inSongMs: 141501, durationMs: 2000 },
  { text: 'Sun\n', inSongMs: 143501, durationMs: 999 },
  { text: 'Well,', inSongMs: 144500, durationMs: 897 },
  { text: 'there', inSongMs: 145397, durationMs: 1121 },
  { text: 'is', inSongMs: 146518, durationMs: 448 },
  { text: 'a', inSongMs: 146966, durationMs: 224 },
  { text: 'house', inSongMs: 147190, durationMs: 1121 },
  { text: 'in', inSongMs: 148311, durationMs: 448 },
  { text: 'New', inSongMs: 148759, durationMs: 672 },
  { text: 'Orleans\n', inSongMs: 149431, durationMs: 1569 },
  { text: 'They', inSongMs: 151000, durationMs: 1300 },
  { text: 'call', inSongMs: 152300, durationMs: 1300 },
  { text: 'the', inSongMs: 153600, durationMs: 975 },
  { text: 'Rising', inSongMs: 154575, durationMs: 1950 },
  { text: 'Sun\n', inSongMs: 156525, durationMs: 975 },
  { text: 'And', inSongMs: 157500, durationMs: 656 },
  { text: "it's", inSongMs: 158156, durationMs: 875 },
  { text: 'been', inSongMs: 159031, durationMs: 875 },
  { text: 'the', inSongMs: 159906, durationMs: 656 },
  { text: 'ruin', inSongMs: 160562, durationMs: 875 },
  { text: 'of', inSongMs: 161437, durationMs: 438 },
  { text: 'many', inSongMs: 161875, durationMs: 875 },
  { text: 'a', inSongMs: 162750, durationMs: 219 },
  { text: 'poor', inSongMs: 162969, durationMs: 875 },
  { text: 'boy\n', inSongMs: 163844, durationMs: 656 },
  { text: 'And', inSongMs: 164500, durationMs: 1235 },
  { text: 'God,', inSongMs: 165735, durationMs: 1235 },
  { text: 'I', inSongMs: 166970, durationMs: 412 },
  { text: 'know', inSongMs: 167382, durationMs: 1647 },
  { text: "I'm", inSongMs: 169029, durationMs: 1235 },
  { text: 'one\n', inSongMs: 170264, durationMs: 1236 },
];

const WORD_FIXTURE_LINE_UNITS = [
  { text: 'now we stand ready', inSongMs: 0, durationMs: 2000 },
  { text: 'for sync lyrics', inSongMs: 2000, durationMs: 1700 },
];

const WORD_FIXTURE_WORD_UNITS = [
  { text: 'now', inSongMs: 0, durationMs: 400 },
  { text: 'we', inSongMs: 400, durationMs: 400 },
  { text: 'stand', inSongMs: 800, durationMs: 500 },
  { text: 'ready\n', inSongMs: 1300, durationMs: 700 },
  { text: 'for', inSongMs: 2000, durationMs: 400 },
  { text: 'sync', inSongMs: 2400, durationMs: 600 },
  { text: 'lyrics\n', inSongMs: 3000, durationMs: 700 },
];

const SONG_IDS = {
  HOUSE: 'house-of-the-rising-sun-demo',
  FIXTURE: 'word-fixture-demo',
};

const LINE_LYRICS_BY_SONG = {
  [SONG_IDS.HOUSE]: { granularity: 'line', units: HOUSE_LINE_UNITS },
  [SONG_IDS.FIXTURE]: { granularity: 'line', units: WORD_FIXTURE_LINE_UNITS },
};

const WORD_LYRICS_BY_SONG = {
  [SONG_IDS.HOUSE]: { granularity: 'word', units: HOUSE_WORD_UNITS },
  [SONG_IDS.FIXTURE]: { granularity: 'word', units: WORD_FIXTURE_WORD_UNITS },
};

function normalizeSongId(value) {
  if (value === SONG_IDS.FIXTURE) return SONG_IDS.FIXTURE;
  return SONG_IDS.HOUSE;
}

function normalizeGranularity(value) {
  return value === 'word' ? 'word' : 'line';
}

function cloneLyrics(lyrics) {
  return {
    granularity: lyrics.granularity,
    units: lyrics.units.map((unit) => ({ ...unit })),
  };
}

function resolveLyrics(songId, requestedGranularity) {
  const lineLyrics = LINE_LYRICS_BY_SONG[songId];
  const wordLyrics = WORD_LYRICS_BY_SONG[songId] ?? null;

  // Best effort policy: request word if possible; otherwise fallback to line.
  if (requestedGranularity === 'word' && wordLyrics) {
    return cloneLyrics(wordLyrics);
  }

  return cloneLyrics(lineLyrics);
}

self.onmessage = async (event) => {
  const msg = event.data;
  if (msg?.type !== 'process') return;

  const songId = normalizeSongId(msg.songId ?? msg.settings?.songId);
  const requestedGranularity = normalizeGranularity(msg.granularity ?? msg.settings?.granularity);
  const lyrics = resolveLyrics(songId, requestedGranularity);

  self.postMessage({
    type: 'result',
    requestId: msg.requestId,
    sessionId: msg.sessionId,
    result: lyrics,
  });
};
