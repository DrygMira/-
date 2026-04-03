const MAX_CONTEXT_LEN = 2000;
const RECENT_WINDOW_MS = 5000;

let transport = null;
let transportDepth = 0;
const recentFingerprints = new Map();

function nowTs() {
  return Date.now();
}

function cleanString(value, maxLen = 1000) {
  if (value == null) return '';
  const normalized = String(value).replace(/\s+/g, ' ').trim();
  if (normalized.length <= maxLen) return normalized;
  return `${normalized.slice(0, Math.max(0, maxLen - 3))}...`;
}

function stringifyContext(context) {
  if (context == null) return '';
  try {
    const raw = JSON.stringify(context);
    if (!raw) return '';
    if (raw.length <= MAX_CONTEXT_LEN) return raw;
    return `${raw.slice(0, MAX_CONTEXT_LEN - 3)}...`;
  } catch (error) {
    return cleanString(`context_json_error:${error?.message || error}`, MAX_CONTEXT_LEN);
  }
}

function makeFingerprint(payload) {
  return [
    payload.kind,
    payload.message,
    payload.sourceUrl,
    payload.lineNumber,
    payload.columnNumber,
    payload.requestOp,
  ].join('|');
}

function isDuplicate(fingerprint) {
  const ts = nowTs();
  const prev = recentFingerprints.get(fingerprint);
  recentFingerprints.set(fingerprint, ts);

  for (const [key, time] of recentFingerprints.entries()) {
    if (ts - time > RECENT_WINDOW_MS) {
      recentFingerprints.delete(key);
    }
  }

  return prev != null && ts - prev < RECENT_WINDOW_MS;
}

function buildPayload(details = {}) {
  return {
    kind: cleanString(details.kind || 'client_error', 64),
    message: cleanString(details.message || details.reason || 'Unknown client error', 500),
    stack: cleanString(details.stack || details.error?.stack || '', 8000),
    sourceUrl: cleanString(details.sourceUrl || details.fileName || '', 240),
    lineNumber: Number.isFinite(details.lineNumber) ? details.lineNumber : null,
    columnNumber: Number.isFinite(details.columnNumber) ? details.columnNumber : null,
    route: cleanString(details.route || window.location?.hash || '', 200),
    href: cleanString(details.href || window.location?.href || '', 240),
    userAgent: cleanString(details.userAgent || navigator.userAgent || '', 240),
    clientTs: Number.isFinite(details.clientTs) ? details.clientTs : nowTs(),
    requestOp: cleanString(details.requestOp || '', 64),
    requestIdRef: cleanString(details.requestIdRef || '', 128),
    contextJson: stringifyContext({
      title: document.title || '',
      pageVisibility: document.visibilityState || '',
      ...details.context,
    }),
  };
}

export function setClientErrorTransport(fn) {
  transport = typeof fn === 'function' ? fn : null;
}

export async function captureClientError(details = {}) {
  const payload = buildPayload(details);
  if (!payload.message) return false;

  const fingerprint = details.dedupeKey || makeFingerprint(payload);
  if (isDuplicate(fingerprint)) return false;

  console.error('[client-error]', payload.kind, payload.message, details.error || '');

  if (!transport || details.skipTransport === true || transportDepth > 0) {
    return false;
  }

  try {
    transportDepth += 1;
    await transport(payload);
    return true;
  } catch (error) {
    console.warn('client error transport failed', error);
    return false;
  } finally {
    transportDepth = Math.max(0, transportDepth - 1);
  }
}
