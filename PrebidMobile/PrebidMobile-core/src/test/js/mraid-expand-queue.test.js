// Runs the real PBMJSLibraries/mraid.js against a fake "jsBridge" to check that an interstitial
// expand() does not leave later commands stuck in the mraid.js command queue.
//
//   node mraid-expand-queue.test.js fixed    # InterstitialJSInterface.expand() completes the native call
//   node mraid-expand-queue.test.js legacy   # expand() is empty (behaviour before the fix) -> fails
//
// Like the Android bridge, every command completes asynchronously (the real calls are posted to the
// main thread), so open() and close() are queued in mraid.js while expand() is still in flight.
// Only the no-URL interstitial expand() has no handler and relies on the explicit completion.
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const mode = process.argv[2] || 'fixed';
if (mode !== 'fixed' && mode !== 'legacy') {
    throw new Error('usage: node mraid-expand-queue.test.js [fixed|legacy]');
}

const calls = [];
const window = {};
const completeLater = () => setTimeout(() => window.mraid.nativeCallComplete(), 0);
const jsBridge = {
    expand(url) {
        calls.push(url ? 'expand:' + url : 'expand');
        if (!url && mode === 'fixed') {
            completeLater();
        }
    },
    open(url) {
        calls.push('open:' + url);
        completeLater();
    },
    close() {
        calls.push('close');
        completeLater();
    },
};

const sandbox = { window, jsBridge, console };
vm.createContext(sandbox);
const mraidJs = path.join(__dirname, '..', '..', '..', 'PBMJSLibraries', 'mraid.js');
vm.runInContext(fs.readFileSync(mraidJs, 'utf8'), sandbox, { filename: mraidJs });

window.mraid.expand();
window.mraid.open('https://example.com');
window.mraid.close();

assert.deepStrictEqual(calls, ['expand'], 'open() and close() must be queued while expand() is in flight');

setTimeout(() => {
    assert.deepStrictEqual(calls, ['expand', 'open:https://example.com', 'close'],
        mode + ': commands after expand() must reach the bridge in order');
    console.log('PASS (' + mode + '): ' + calls.join(' -> '));
}, 50);
