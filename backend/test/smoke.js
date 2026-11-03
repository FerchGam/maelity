'use strict';
// Prueba de humo: levanta un "proveedor" falso compatible con OpenAI y comprueba las 3 rutas del backend.
const http = require('node:http');
const assert = require('node:assert');

function wavSilence() { // WAV valido de 0.2 s
  const n = 3200, b = Buffer.alloc(44 + n * 2);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n * 2, 4); b.write('WAVEfmt ', 8); b.writeUInt32LE(16, 16);
  b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(16000, 24); b.writeUInt32LE(32000, 28);
  b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(n * 2, 40);
  return b;
}

const seen = {};
const fake = http.createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const raw = Buffer.concat(chunks);
    seen[req.url] = { auth: req.headers.authorization, ctype: req.headers['content-type'], raw };
    if (req.url === '/chat/completions') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ choices: [{ message: { content: '{"say":"hola","mood":"friendly","action":"none","find":null}' } }] }));
    }
    if (req.url === '/audio/transcriptions') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ text: process.env.FAKE_STT_TEXT || 'dónde hay diamantes' }));
    }
    if (req.url === '/audio/speech') { res.writeHead(200, { 'Content-Type': 'audio/wav' }); return res.end(wavSilence()); }
    res.writeHead(404); res.end();
  });
});

(async () => {
  await new Promise((r) => fake.listen(9101, r));
  process.env.CHAT_BASE_URL = process.env.STT_BASE_URL = process.env.TTS_BASE_URL = 'http://127.0.0.1:9101';
  process.env.CHAT_API_KEY = 'ck'; process.env.TTS_API_KEY = 'tk'; process.env.MAELITY_TOKEN = 'secret'; process.env.PORT = '0';
  const { server } = require('../server.js');
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const H = { 'X-Maelity-Token': 'secret' };

  // health
  assert.equal((await fetch(base + '/health')).status, 200);
  // auth
  assert.equal((await fetch(base + '/v1/chat', { method: 'POST', body: '{}' })).status, 401);

  // chat
  let r = await fetch(base + '/v1/chat', { method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
    body: JSON.stringify({ lang: 'es', messages: [{ role: 'system', content: 's' }, { role: 'user', content: 'hola' }] }) });
  assert.equal(r.status, 200);
  const chat = await r.json();
  assert.equal(JSON.parse(chat.content).say, 'hola');
  const sent = JSON.parse(seen['/chat/completions'].raw.toString());
  assert.equal(sent.response_format.type, 'json_object');
  assert.equal(seen['/chat/completions'].auth, 'Bearer ck');

  // chat validation
  r = await fetch(base + '/v1/chat', { method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages: [{ role: 'hacker', content: 'x' }] }) });
  assert.equal(r.status, 400);

  // stt
  r = await fetch(base + '/v1/stt?lang=es', { method: 'POST', headers: { ...H, 'Content-Type': 'audio/wav' }, body: wavSilence() });
  assert.equal(r.status, 200);
  assert.equal((await r.json()).text, 'dónde hay diamantes');
  const form = seen['/audio/transcriptions'].raw.toString('latin1');
  assert.ok(form.includes('name="language"') && /\r\nes\r\n/.test(form), 'language=es fijado');

  // tts
  r = await fetch(base + '/v1/tts', { method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
    body: JSON.stringify({ text: 'Hola', lang: 'en', mood: 'angry' }) });
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('content-type'), 'audio/wav');
  assert.equal((await r.arrayBuffer()).byteLength, wavSilence().length);
  const tts = JSON.parse(seen['/audio/speech'].raw.toString());
  assert.equal(tts.response_format, 'wav');
  assert.ok(tts.instructions.toLowerCase().includes('angry'));

  // tts texto demasiado largo
  r = await fetch(base + '/v1/tts', { method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
    body: JSON.stringify({ text: 'a'.repeat(400), lang: 'en' }) });
  assert.equal(r.status, 400);

  console.log('backend smoke test OK');
  if (process.env.KEEP_ALIVE) { console.log('PORT=' + server.address().port); return; }
  server.close(); fake.close();
})().catch((e) => { console.error(e); process.exit(1); });
