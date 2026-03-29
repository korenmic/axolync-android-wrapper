import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const repoRoot = resolve(import.meta.dirname, '..');

test('android native splash layout uses a cover backdrop plus contained foreground artwork', () => {
  const layout = readFileSync(resolve(repoRoot, 'app', 'src', 'main', 'res', 'layout', 'activity_splash.xml'), 'utf8');
  assert.match(layout, /android:id="@\+id\/splash_image_backdrop"/);
  assert.match(layout, /android:scaleType="centerCrop"/);
  assert.match(layout, /android:id="@\+id\/splash_scrim"/);
  assert.match(layout, /android:background="#94000000"/);
  assert.match(layout, /android:id="@\+id\/splash_image_foreground"/);
  assert.match(layout, /android:scaleType="fitCenter"/);
  assert.match(layout, /android:src="@drawable\/axolync_logo"/);
  assert.doesNotMatch(layout, /android:padding=/);
  assert.doesNotMatch(layout, /@drawable\/splash_fullscreen/);
  assert.doesNotMatch(layout, /android:scaleX=/);
  assert.doesNotMatch(layout, /android:scaleY=/);
});
