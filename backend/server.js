'use strict';
/**
 * Maelity backend: proxy delgado entre el mod y los proveedores de IA.
 *  - Guarda las claves API (el jugador no configura nada).
 *  - POST /v1/chat   {messages, lang}        -> {content}  (JSON del modelo)
 *  - POST /v1/stt?lang=es|en  (audio/wav)   -> {text}
 *  - POST /v1/tts    {text, lang, mood}      -> audio/wav
 * Todos los proveedores son compatibles con la API de OpenAI (OpenAI, Groq, Ollama, Kokoro-FastAPI, LiteLLM...).
 * Sin dependencias: solo Node 20+.
 */
const http = require('node:http');
const crypto = require('node:crypto');

const env = (k, d) => (process.env[k] !== undefined && process.env[k] !== '' ? process.env[k] : d);
const cfg = {
  port: parseInt(env('PORT', '8787'), 10),
  token: env('MAELITY_TOKEN', env('AMITY_TOKEN', ''))  /* AMITY_TOKEN = nombre antiguo, aún válido */,
  trustProxy: env('TRUST_PROXY', '0') === '1',
  chat: { base: env('CHAT_BASE_URL', 'https://api.groq.com/openai/v1'), key: env('CHAT_API_KEY', ''), model: env('CHAT_MODEL', 'llama-3.3-70b-versatile') },
  stt: { base: env('STT_BASE_URL', 'https://api.groq.com/openai/v1'), key: env('STT_API_KEY', env('CHAT_API_KEY', '')), model: env('STT_MODEL', 'whisper-large-v3-turbo') },
  tts: { base: env('TTS_BASE_URL', 'https://api.openai.com/v1'), key: env('TTS_API_KEY', ''), model: env('TTS_MODEL', 'gpt-4o-mini-tts'),
         voiceEs: env('TTS_VOICE_ES', 'nova'), voiceEn: env('TTS_VOICE_EN', 'nova') },
  limits: { perMinute: parseInt(env('RATE_PER_MIN', '60'), 10) },
};

// ---- estilos de voz por personalidad (gpt-4o-mini-tts usa "instructions"; tts-1 usa "speed")
const MOODS = {
  friendly:   { speed: 1.0,  instr: 'Warm, kind and cheerful, like a close friend.' },
  playful:    { speed: 1.12, instr: 'Playful, bouncy and mischievous, a little giggly.' },
  sarcastic:  { speed: 0.95, instr: 'Dry, deadpan and sarcastic, with a smirk in the voice.' },
  protective: { speed: 1.08, instr: 'Serious, alert and protective, firm but caring.' },
  mysterious: { speed: 0.88, instr: 'Low, slow and mysterious, whispering slightly, unsettling.' },
  angry:      { speed: 1.1,  instr: 'Irritated and angry, sharp and tense.' },
};

// ---- limitador de ritmo simple por IP (ventana de 60 s)
const hits = new Map();
function rateLimited(ip) {
  const now = Date.now();
  const arr = (hits.get(ip) || []).filter((t) => now - t < 60000);
  arr.push(now);
  hits.set(ip, arr);
  return arr.length > cfg.limits.perMinute;
}
setInterval(() => { const now = Date.now(); for (const [k, v] of hits) if (!v.some((t) => now - t < 60000)) hits.delete(k); }, 60000).unref();

function authOk(req) {
  if (!cfg.token) return true;
  const got = Buffer.from(String(req.headers['x-maelity-token'] || ''));
  const want = Buffer.from(cfg.token);
  return got.length === want.length && crypto.timingSafeEqual(got, want);
}

function send(res, status, body, type = 'application/json') {
  const buf = Buffer.isBuffer(body) ? body : Buffer.from(typeof body === 'string' ? body : JSON.stringify(body));
  res.writeHead(status, { 'Content-Type': type, 'Content-Length': buf.length });
  res.end(buf);
}
const fail = (res, status, msg) => send(res, status, { error: msg });

function readBody(req, max) {
  return new Promise((resolve, reject) => {
    const chunks = []; let size = 0;
    req.on('data', (c) => { size += c.length; if (size > max) { reject(Object.assign(new Error('payload too large'), { status: 413 })); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}
const langOf = (v) => (v === 'es' ? 'es' : 'en');

// Whisper alucina frases en silencio; las descartamos.
const HALLUCINATIONS = [/thanks for watching/i, /thank you for watching/i, /gracias por ver/i, /subt[ií]tulos/i, /subtitles by/i, /amara\.org/i, /^\W*you\W*$/i];

async function upstream(url, init, timeoutMs = 20000) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), timeoutMs);
  try { return await fetch(url, { ...init, signal: ctl.signal }); } finally { clearTimeout(t); }
}

// ---- handlers
async function handleChat(req, res) {
  const body = JSON.parse((await readBody(req, 64 * 1024)).toString('utf8'));
  const msgs = body.messages;
  if (!Array.isArray(msgs) || msgs.length < 1 || msgs.length > 30) return fail(res, 400, 'bad messages');
  let total = 0;
  for (const m of msgs) {
    if (!m || !['system', 'user', 'assistant'].includes(m.role) || typeof m.content !== 'string') return fail(res, 400, 'bad message');
    total += m.content.length;
  }
  if (total > 24000) return fail(res, 413, 'too long');
  const r = await upstream(`${cfg.chat.base}/chat/completions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${cfg.chat.key}` },
    body: JSON.stringify({ model: cfg.chat.model, messages: msgs, temperature: 0.8, max_tokens: 220, response_format: { type: 'json_object' } }),
  });
  if (!r.ok) return fail(res, 502, `chat upstream ${r.status}`);
  const j = await r.json();
  const content = j?.choices?.[0]?.message?.content;
  if (typeof content !== 'string') return fail(res, 502, 'chat upstream empty');
  send(res, 200, { content });
}

async function handleStt(req, res, url) {
  const lang = langOf(url.searchParams.get('lang'));
  const audio = await readBody(req, 2.5 * 1024 * 1024);
  if (audio.length < 1000) return send(res, 200, { text: '' });
  const form = new FormData();
  form.append('file', new Blob([audio], { type: 'audio/wav' }), 'speech.wav');
  form.append('model', cfg.stt.model);
  form.append('language', lang);                 // fijar idioma = transcripcion mas estable
  form.append('temperature', '0');
  form.append('response_format', 'json');
  form.append('prompt', lang === 'es'
    ? 'Conversación en español con Maelity en Minecraft: diamante, aldea, hierro, ayúdame, sígueme.'
    : 'Conversation in English with Maelity in Minecraft: diamond, village, iron, help me, follow me.');
  const r = await upstream(`${cfg.stt.base}/audio/transcriptions`, { method: 'POST', headers: { Authorization: `Bearer ${cfg.stt.key}` }, body: form });
  if (!r.ok) return fail(res, 502, `stt upstream ${r.status}`);
  const j = await r.json();
  let text = String(j.text || '').trim();
  if (HALLUCINATIONS.some((re) => re.test(text))) text = '';
  send(res, 200, { text });
}

async function handleTts(req, res) {
  const body = JSON.parse((await readBody(req, 8 * 1024)).toString('utf8'));
  const text = String(body.text || '').trim();
  if (!text || text.length > 300) return fail(res, 400, 'bad text');
  const lang = langOf(body.lang);
  const mood = MOODS[body.mood] || MOODS.friendly;
  const payload = { model: cfg.tts.model, voice: lang === 'es' ? cfg.tts.voiceEs : cfg.tts.voiceEn, input: text, response_format: 'wav' };
  if (/^tts-1/.test(cfg.tts.model)) payload.speed = mood.speed; else payload.instructions = mood.instr;
  const r = await upstream(`${cfg.tts.base}/audio/speech`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${cfg.tts.key}` }, body: JSON.stringify(payload),
  }, 30000);
  if (!r.ok) return fail(res, 502, `tts upstream ${r.status}`);
  send(res, 200, Buffer.from(await r.arrayBuffer()), 'audio/wav');
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    if (req.method === 'GET' && url.pathname === '/health') return send(res, 200, { ok: true });
    if (req.method !== 'POST') return fail(res, 404, 'not found');
    if (!authOk(req)) return fail(res, 401, 'unauthorized');
    const ip = (cfg.trustProxy && String(req.headers['x-forwarded-for'] || '').split(',')[0].trim()) || req.socket.remoteAddress || 'x';
    if (rateLimited(ip)) return fail(res, 429, 'slow down');
    if (url.pathname === '/v1/chat') return await handleChat(req, res);
    if (url.pathname === '/v1/stt') return await handleStt(req, res, url);
    if (url.pathname === '/v1/tts') return await handleTts(req, res);
    return fail(res, 404, 'not found');
  } catch (e) {
    const status = e.status || (e.name === 'AbortError' ? 504 : e instanceof SyntaxError ? 400 : 500);
    if (!res.headersSent) fail(res, status, status === 500 ? 'internal error' : e.message);
  }
});

if (require.main === module) {
  server.listen(cfg.port, () => console.log(`Maelity backend listening on :${cfg.port}`));
}
module.exports = { server, cfg };
