package server.logic.ws_protocol.JSON;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.entyties.Net_Exception_Response;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;

import java.util.Map;

/**
 * JsonInboundProcessor — обработка JSON-сообщений.
 *
 * 1) Парсит общий пакет (op, requestId, payload).
 * 2) По op выбирает класс запроса и хэндлер.
 * 3) Собирает "плоский" объект: op + requestId + поля из payload.
 * 4) Маппит его в NetRequest через ObjectMapper.
 * 5) Вызывает хэндлер, получает NetResponse.
 * 6) Собирает JSON-ответ:
 *    {
 *      "op": ...,
 *      "requestId": ...,
 *      "status": ...,
 *      "payload": { все поля response, кроме op/requestId/status/payload }
 *    }
 */
public final class JsonInboundProcessor {
    private static final Logger log = LoggerFactory.getLogger(JsonInboundProcessor.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final Map<String, JsonMessageHandler> JSON_HANDLERS =
            JsonHandlerRegistry.getHandlers();

    private static final Map<String, Class<? extends Net_Request>> JSON_REQUEST_TYPES =
            JsonHandlerRegistry.getRequestTypes();

    private JsonInboundProcessor() {
        // utility
    }

    public static String processJson(String json, ConnectionContext ctx) {
        String op = null;
        String requestId = null;

        try {
            if (json == null || json.isBlank()) {
                Net_Exception_Response err = NetExceptionResponseFactory.error(
                        null,
                        null,
                        WireCodes.Status.BAD_REQUEST,
                        "EMPTY_JSON",
                        "Пустое JSON-сообщение"
                );
                return writeResponse(err);
            }

            // 1. Парсим общий пакет
            JsonNode root = JSON_MAPPER.readTree(json);

            // 2. op и requestId из корня
            op = getTextOrNull(root, "op");
            requestId = getTextOrNull(root, "requestId");

            if (op == null || op.isEmpty()) {
                Net_Exception_Response err = NetExceptionResponseFactory.error(
                        null,
                        requestId,
                        WireCodes.Status.BAD_REQUEST,
                        "NO_OP",
                        "Поле 'op' отсутствует или пустое"
                );
                return writeResponse(err);
            }

            JsonMessageHandler handler = JSON_HANDLERS.get(op);
            Class<? extends Net_Request> reqClass = JSON_REQUEST_TYPES.get(op);

            if (handler == null || reqClass == null) {
                Net_Exception_Response err = NetExceptionResponseFactory.error(
                        op,
                        requestId,
                        WireCodes.Status.BAD_REQUEST,
                        "UNKNOWN_OP",
                        "Неизвестная операция: " + op
                );
                return writeResponse(err);
            }

            // 3. Берём payload
            JsonNode payloadNode = root.get("payload");
            if (payloadNode == null || payloadNode.isNull()) {
                Net_Exception_Response err = NetExceptionResponseFactory.error(
                        op,
                        requestId,
                        WireCodes.Status.BAD_REQUEST,
                        "NO_PAYLOAD",
                        "Поле 'payload' отсутствует"
                );
                return writeResponse(err);
            }
            if (!payloadNode.isObject()) {
                Net_Exception_Response err = NetExceptionResponseFactory.error(
                        op,
                        requestId,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_PAYLOAD",
                        "Поле 'payload' должно быть объектом"
                );
                return writeResponse(err);
            }

            // 3.1 Собираем "плоский" объект для маппинга в NetRequest:
            //     op + requestId + поля из payload
            ObjectNode merged = JSON_MAPPER.createObjectNode();

            // Добавляем op и requestId, чтобы они попали в NetRequest
            if (op != null) {
                merged.put("op", op);
            }
            if (requestId != null) {
                merged.put("requestId", requestId);
            }

            // Добавляем все поля из payload внутрь
            merged.setAll((ObjectNode) payloadNode);

            // 4. Маппим в конкретный класс NetRequest
            Net_Request request = JSON_MAPPER.treeToValue(merged, reqClass);

            Net_Response response;

            // 5. Вызываем хэндлер
            try {
                response = handler.handle(request, ctx);
            } catch (Exception handlerError) {
                log.error("💥 Ошибка внутри хэндлера '{}'", op, handlerError);
                Net_Exception_Response err = NetExceptionResponseFactory.error(
                        op,
                        requestId,
                        WireCodes.Status.INTERNAL_ERROR,
                        "INTERNAL_HANDLER_ERROR",
                        "Неожиданная ошибка при обработке операции: " + op
                );
                return writeResponse(err);
            }

            // На всякий случай: если хэндлер не выставил op/requestId
            if (response.getOp() == null) response.setOp(op);
            if (response.getRequestId() == null) response.setRequestId(requestId);

            // 6. Универсальная сборка ответа
            return writeResponse(response);

        } catch (Exception e) {
            log.error("Ошибка при обработке JSON-сообщения", e);
            Net_Exception_Response err = NetExceptionResponseFactory.error(
                    op != null ? op : "Unknown",
                    requestId,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
            return writeResponse(err);
        }
    }

    // --- helpers ---

    private static String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    /**
     * Унифицированная сериализация любого NetResponse в формат:
     * {
     *   "op": ...,
     *   "requestId": ...,
     *   "status": ...,
     *   "payload": { ... }
     * }
     */
    private static String writeResponse(Net_Response response) {
        try {
            // Конвертируем полный объект ответа в ObjectNode
            ObjectNode full = JSON_MAPPER.convertValue(response, ObjectNode.class);

            // То, что должно остаться наверху:
            String op = full.hasNonNull("op") ? full.get("op").asText() : null;
            String requestId = full.hasNonNull("requestId") ? full.get("requestId").asText() : null;
            int status = full.hasNonNull("status") ? full.get("status").asInt() : 0;

            // Удаляем базовые поля и payload из "полного" объекта,
            // всё остальное отправляем внутрь payload.
            full.remove("op");
            full.remove("requestId");
            full.remove("status");
            full.remove("payload");

            ObjectNode root = JSON_MAPPER.createObjectNode();
            if (op != null) root.put("op", op); else root.putNull("op");
            if (requestId != null) root.put("requestId", requestId); else root.putNull("requestId");
            root.put("status", status);

            // payload — это всё, что осталось от full (может быть пустым объектом {})
            root.set("payload", full);

            return JSON_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // Совсем аварийный случай — сериализация ответа сломалась.
            return "{\"op\":\"" + safe(response.getOp()) +
                    "\",\"requestId\":\"" + safe(response.getRequestId()) +
                    "\",\"status\":" + response.getStatus() +
                    ",\"payload\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"" +
                    "Ошибка сериализации ответа\"}}";
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
