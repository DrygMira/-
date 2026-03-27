package server.logic.ws_protocol.JSON.entyties;

/**
 * Базовый класс для всех ответов (server → client).
 *.
 * Наследуется от NetRequest и добавляет status.
 *.
 * Формат JSON (response):
 * {
 *   "op": "...",
 *   "requestId": "...",
 *   "status": 200,
 *   "ok": true,
 *   "payload": { ... } // и для успеха, и для ошибки
 * }
 */
public abstract class Net_Response extends Net_Request {

    /** Статус результата (200 — успех, любое другое значение — ошибка). */
    private int status;

    // --- getters / setters ---

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isOk() {
        return status >= 200 && status < 300;
    }
}
