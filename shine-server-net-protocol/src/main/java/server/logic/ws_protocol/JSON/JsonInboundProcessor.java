package server.logic.ws_protocol.JSON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.WireCodes;

import java.util.Map;

/**
 * JsonInboundProcessor — отдельный класс для обработки JSON-сообщений.
 *
 * 1) Парсит общий пакет (op, requestId, payload).
 * 2) По op выбирает класс запроса и хэндлер.
 * 3) Маппит JSON → NetRequest через ObjectMapper.
 * 4) Вызывает хэндлер, получает NetResponse.
 * 5) Собирает JSON-ответ и возвращает строкой.
 */
public final class JsonInboundProcessor {
    private static final Logger log = LoggerFactory.getLogger(JsonInboundProcessor.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * op → хэндлер.
     * Регистрация вынесена в JsonHandlerRegistry.
     */
    private static final Map<String, JsonMessageHandler> JSON_HANDLERS =
            JsonHandlerRegistry.getHandlers();

    /**
     * op → класс запроса.
     */
    private static final Map<String, Class<? extends NetRequest>> JSON_REQUEST_TYPES =
            JsonHandlerRegistry.getRequestTypes();

    private JsonInboundProcessor() {}

    /**
     * Обработка входящего JSON-сообщения.
     *
     * @param json исходная строка от клиента
     * @param ctx  контекст текущего WebSocket-соединения
     * @return JSON-строка ответа
     */
    public static String processJson(String json, ConnectionContext ctx) {
        try {
            if (json == null || json.isBlank()) {
                return buildErrorJson(null, null, WireCodes.Status.BAD_REQUEST,
                        "EMPTY_JSON", "Пустое JSON-сообщение");
            }

            // 1. Парсим общий пакет как дерево
            JsonNode root = JSON_MAPPER.readTree(json);

            // 2. Берём op и requestId
            String op = getTextOrNull(root, "op");
            if (op == null || op.isEmpty()) {
                return buildErrorJson(null, null, WireCodes.Status.BAD_REQUEST,
                        "NO_OP", "Поле 'op' отсутствует или пустое");
            }

            String requestId = getTextOrNull(root, "requestId");

            JsonMessageHandler handler = JSON_HANDLERS.get(op);
            Class<? extends NetRequest> reqClass = JSON_REQUEST_TYPES.get(op);

            if (handler == null || reqClass == null) {
                return buildErrorJson(op, requestId, WireCodes.Status.BAD_REQUEST,
                        "UNKNOWN_OP", "Неизвестная операция: " + op);
            }

            // 3. Маппим весь JSON в конкретный класс запроса
            NetRequest request = JSON_MAPPER.treeToValue(root, reqClass);

            // 4. Вызываем хэндлер, передавая контекст
            NetResponse response = handler.handle(request, ctx);

            // На всякий случай: если хэндлер не выставил op/requestId
            if (response.getOp() == null) {
                response.setOp(op);
            }
            if (response.getRequestId() == null) {
                response.setRequestId(requestId);
            }

            // 5. Собираем JSON-ответ
            ObjectNode out = JSON_MAPPER.createObjectNode();
            out.put("op", response.getOp());
            out.put("requestId", response.getRequestId());
            out.put("status", response.getStatus());

            if (response.getPayload() != null) {
                out.set("payload", JSON_MAPPER.valueToTree(response.getPayload()));
            } else {
                out.putNull("payload");
            }

            return JSON_MAPPER.writeValueAsString(out);

        } catch (Exception e) {
            log.error("Ошибка при обработке JSON-сообщения", e);
            return buildErrorJson("Unknown", null, WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR", "Внутренняя ошибка сервера");
        }
    }

    // --- helper'ы ---

    private static String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    /**
     * Генерация JSON-ошибки в формате ответа:
     * {
     *   "op": op,
     *   "requestId": requestId,
     *   "status": status,
     *   "payload": {
     *     "code": errorCode,
     *     "message": errorMessage
     *   }
     * }
     */
    private static String buildErrorJson(String op,
                                         String requestId,
                                         int status,
                                         String errorCode,
                                         String errorMessage) {
        try {
            ObjectNode root = JSON_MAPPER.createObjectNode();

            if (op != null) root.put("op", op); else root.putNull("op");
            if (requestId != null) root.put("requestId", requestId); else root.putNull("requestId");

            root.put("status", status);

            ObjectNode payload = root.putObject("payload");
            payload.put("code", errorCode);
            payload.put("message", errorMessage);

            return JSON_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"op\":\"" + (op != null ? op : "") +
                    "\",\"requestId\":\"" + (requestId != null ? requestId : "") +
                    "\",\"status\":" + status +
                    ",\"payload\":{\"code\":\"" + errorCode +
                    "\",\"message\":\"" + errorMessage + "\"}}";
        }
    }
}
