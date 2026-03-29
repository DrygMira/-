const DEFAULT_TIMEOUT_MS = 12000;

function buildWsUrl(raw) {
  const value = (raw || '').trim();
  if (!value) return 'wss://shineup.me/ws';
  if (value.startsWith('ws://') || value.startsWith('wss://')) return value;
  if (value.startsWith('http://')) return `ws://${value.slice('http://'.length)}`;
  if (value.startsWith('https://')) return `wss://${value.slice('https://'.length)}`;
  return value;
}

function createRequestId(op) {
  return `${op}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export class WsJsonClient {
  constructor(url) {
    this.url = buildWsUrl(url);
    this.ws = null;
    this.pending = new Map();
    this.openPromise = null;
  }

  async open() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return;
    if (this.openPromise) return this.openPromise;

    this.openPromise = new Promise((resolve, reject) => {
      const ws = new WebSocket(this.url);
      this.ws = ws;

      ws.addEventListener('open', () => {
        resolve();
      }, { once: true });

      ws.addEventListener('error', () => {
        reject(new Error(`Не удалось подключиться к ${this.url}`));
      }, { once: true });

      ws.addEventListener('close', () => {
        this.failPending('Соединение WebSocket закрыто');
      });

      ws.addEventListener('message', (event) => {
        this.handleMessage(event.data);
      });
    }).finally(() => {
      this.openPromise = null;
    });

    return this.openPromise;
  }

  async request(op, payload = {}, timeoutMs = DEFAULT_TIMEOUT_MS) {
    await this.open();
    const requestId = createRequestId(op);
    const body = { op, requestId, payload };

    const responsePromise = new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error(`Таймаут ответа для операции ${op}`));
      }, timeoutMs);

      this.pending.set(requestId, {
        resolve: (value) => {
          window.clearTimeout(timer);
          resolve(value);
        },
        reject: (error) => {
          window.clearTimeout(timer);
          reject(error);
        },
      });
    });

    this.ws.send(JSON.stringify(body));
    return responsePromise;
  }

  close() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  handleMessage(raw) {
    let data;
    try {
      data = JSON.parse(raw);
    } catch {
      return;
    }

    const requestId = data?.requestId;
    if (!requestId) return;

    const slot = this.pending.get(requestId);
    if (!slot) return;
    this.pending.delete(requestId);
    slot.resolve(data);
  }

  failPending(message) {
    const error = new Error(message);
    for (const [, slot] of this.pending.entries()) {
      slot.reject(error);
    }
    this.pending.clear();
  }
}
