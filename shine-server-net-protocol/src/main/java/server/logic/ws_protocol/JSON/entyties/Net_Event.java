package server.logic.ws_protocol.JSON.entyties;

/**
 * Базовый класс для всех событий (event).
 * Общие поля: op и payload.
 *.
 * Формат JSON (event):
 * {
 *   "op": "...",
 *   "payload": { ... }
 * }
 */
public abstract class Net_Event {

    /** Имя операции / события (op). */
    private String op;

    /**
     * Произвольные данные.
     * В JSON это поле "payload".
     */
    private Object payload;

    // --- getters / setters ---

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
