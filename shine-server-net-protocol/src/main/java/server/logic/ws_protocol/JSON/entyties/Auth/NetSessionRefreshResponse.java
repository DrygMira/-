package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Успешный ответ на SessionRefresh.
 *
 * Дополнительных полей нет, достаточно status=200 и (опционально) пустого payload.
 */
public class NetSessionRefreshResponse extends NetResponse {
    // Ничего дополнительного, вся информация в status.
}
