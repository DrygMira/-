package server.logic.ws_protocol.JSON.handlers.userParams.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ на UpsertUserParam.
 *
 * Успех:
 * {
 *   "op": "UpsertUserParam",
 *   "requestId": "req-123",
 *   "status": 200,
 *   "payload": { }
 * }
 */
public class Net_UpsertUserParam_Response extends Net_Response {
    // MVP: без payload. При желании позже можно добавить created/updated.
}