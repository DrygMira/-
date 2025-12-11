package server.logic.ws_protocol.JSON.entyties;

/**
 * Ответ с ошибкой (любой отказ).
 *.
 * В payload будет:
 * {
 *   "code": "...",
 *   "message": "..."
 * }
 */
public class Net_Exception_Response extends Net_Response {

    private String code;
    private String message;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
