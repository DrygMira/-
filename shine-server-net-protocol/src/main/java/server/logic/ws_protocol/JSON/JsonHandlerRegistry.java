package server.logic.ws_protocol.JSON;

import server.logic.ws_protocol.JSON.entyties.*;
import server.logic.ws_protocol.JSON.entyties.Auth.NetAuthSessionNewStep1Request;
import server.logic.ws_protocol.JSON.entyties.Auth.NetSessionRefreshRequest;
import server.logic.ws_protocol.JSON.handlers.*;
import server.logic.ws_protocol.JSON.entyties.tempToTest.NetAddUserRequest;
import server.logic.ws_protocol.JSON.handlers.tempToTest.NetAddUserHandler;
import server.logic.ws_protocol.JSON.handlers.auth.NetAuthSessionNewStep1Handler;
import server.logic.ws_protocol.JSON.handlers.auth.NetSessionRefreshHandler;

import java.util.Map;

/**
 * JsonHandlerRegistry — единое место, где руками регистрируются
 * JSON-операции: op → handler и op → requestClass.
 *
 * Если нужно добавить новый запрос:
 *   1) создаёшь класс NetXXXRequest / NetXXXResponse,
 *   2) создаёшь JsonMessageHandler (NetXXXHandler),
 *   3) добавляешь оп в HANDLERS и REQUEST_TYPES.
 */
public final class JsonHandlerRegistry {

    private static final Map<String, JsonMessageHandler> HANDLERS = Map.of(
            "SessionRefresh", new NetSessionRefreshHandler(),
            "AddUser",        new NetAddUserHandler(),
            "AuthSessionNewStep1", new NetAuthSessionNewStep1Handler()
            // сюда потом добавишь другие операции
    );

    private static final Map<String, Class<? extends NetRequest>> REQUEST_TYPES = Map.of(
            "SessionRefresh", NetSessionRefreshRequest.class,
            "AddUser",        NetAddUserRequest.class,
            "AuthSessionNewStep1", NetAuthSessionNewStep1Request.class
    );

    private JsonHandlerRegistry() {
        // utility
    }

    public static Map<String, JsonMessageHandler> getHandlers() {
        return HANDLERS;
    }

    public static Map<String, Class<? extends NetRequest>> getRequestTypes() {
        return REQUEST_TYPES;
    }
}
