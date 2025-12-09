package server.logic.ws_protocol.JSON.entyties.tempToTest;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Успешный ответ на AddUser.
 *
 * Сейчас дополнительных полей нет — достаточно status=200.
 *
 * Пример:
 * {
 *   "op": "AddUser",
 *   "requestId": "test-add-1",
 *   "status": 200,
 *   "payload": { }
 * }
 */
public class NetAddUserResponse extends NetResponse {
    // При необходимости сюда можно добавить, например, флаг created/updated и т.п.
}
