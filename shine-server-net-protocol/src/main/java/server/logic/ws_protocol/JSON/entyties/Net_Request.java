package server.logic.ws_protocol.JSON.entyties;

/**
 * Базовый класс для всех запросов (client → server).
 *.
 * Наследуется от NetEvent и добавляет requestId.
 *.
 * Формат JSON (request):
 * {
 *   "op": "...",
 *   "requestId": "...",
 *   "payload": { ... }
 * }
 */
public abstract class Net_Request extends Net_Event {

    /** Идентификатор запроса, чтобы связать запрос и ответ. */
    private String requestId;

    // --- getters / setters ---

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
