package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Успешный ответ на SessionRefresh.
 *
 * Дополнительно к статусу 200 сервер возвращает storagePwd,
 * чтобы клиент мог восстановить/синхронизировать локальное хранилище.
 *
 * JSON:
 * {
 *   "op": "SessionRefresh",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "storagePwd": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class NetSessionRefreshResponse extends NetResponse {

    /** Пароль хранилища, сохранённый в сессии (storagePwd). */
    private String storagePwd;

    public String getStoragePwd() {
        return storagePwd;
    }

    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
    }
}
