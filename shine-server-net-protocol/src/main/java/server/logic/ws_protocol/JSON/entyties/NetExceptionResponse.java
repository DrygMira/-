package server.logic.ws_protocol.JSON.entyties;

/**
 * Ответ с ошибкой (любой отказ).
 *
 * В payload лежит:
 * {
 *   "code": "...",
 *   "message": "..."
 * }
 */
public class NetExceptionResponse extends NetResponse {
    // Ничего дополнительного: код/текст ошибки лежат в payload (Map или DTO).
}
