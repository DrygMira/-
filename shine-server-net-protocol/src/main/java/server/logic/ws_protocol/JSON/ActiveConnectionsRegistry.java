package server.logic.ws_protocol.JSON;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Реестр активных подключений (только авторизованные).
 *
 * Позволяет:
 *  - получить ConnectionContext по sessionId;
 *  - получить все активные подключения пользователя по loginId;
 *  - удалить подключение при закрытии WebSocket.
 *
 *  найти все подключения пользователя:
 *      var set = ActiveConnectionsRegistry.getInstance().getByLoginId(loginId);
 *
 *  найти конкретное подключение по sessionId:
 *      ConnectionContext ctx = ActiveConnectionsRegistry.getInstance().getBySessionId(sessionId);
 *      Session ws = ctx != null ? ctx.getWsSession() : null;
 */
public final class ActiveConnectionsRegistry {

    private static final ActiveConnectionsRegistry INSTANCE = new ActiveConnectionsRegistry();

    public static ActiveConnectionsRegistry getInstance() {
        return INSTANCE;
    }

    private ActiveConnectionsRegistry() {
        // singleton
    }

    // sessionId (String) -> ConnectionContext
    private final ConcurrentHashMap<String, ConnectionContext> bySessionId = new ConcurrentHashMap<>();

    // loginId -> множество ConnectionContext для этого пользователя
    private final ConcurrentHashMap<Long, Set<ConnectionContext>> byLoginId = new ConcurrentHashMap<>();

    /**
     * Зарегистрировать авторизованное подключение.
     * Ожидается, что в ctx уже выставлены loginId и sessionId.
     */
    public void register(ConnectionContext ctx) {
        if (ctx == null) return;

        String sessionId = ctx.getSessionId();
        Long loginId = ctx.getLoginId();

        if (sessionId == null || loginId == null) {
            return;
        }

        bySessionId.put(sessionId, ctx);

        byLoginId
                .computeIfAbsent(loginId, id -> new CopyOnWriteArraySet<>())
                .add(ctx);
    }

    /**
     * Удалить подключение по контексту (например, при onClose).
     */
    public void remove(ConnectionContext ctx) {
        if (ctx == null) return;

        String sessionId = ctx.getSessionId();
        Long loginId = ctx.getLoginId();

        if (sessionId != null) {
            bySessionId.remove(sessionId);
        }

        if (loginId != null) {
            Set<ConnectionContext> set = byLoginId.get(loginId);
            if (set != null) {
                set.remove(ctx);
                if (set.isEmpty()) {
                    byLoginId.remove(loginId);
                }
            }
        }
    }

    /**
     * Удалить подключение по sessionId.
     */
    public void removeBySessionId(String sessionId) {
        if (sessionId == null) return;

        ConnectionContext ctx = bySessionId.remove(sessionId);
        if (ctx != null) {
            Long loginId = ctx.getLoginId();
            if (loginId != null) {
                Set<ConnectionContext> set = byLoginId.get(loginId);
                if (set != null) {
                    set.remove(ctx);
                    if (set.isEmpty()) {
                        byLoginId.remove(loginId);
                    }
                }
            }
        }
    }

    /**
     * Получить контекст по sessionId.
     */
    public ConnectionContext getBySessionId(String sessionId) {
        if (sessionId == null) return null;
        return bySessionId.get(sessionId);
    }

    /**
     * Получить все активные подключения пользователя по loginId.
     */
    public Set<ConnectionContext> getByLoginId(long loginId) {
        Set<ConnectionContext> set = byLoginId.get(loginId);
        if (set == null) {
            return Set.of();
        }
        // CopyOnWriteArraySet безопасно отдавать как есть
        return set;
    }
}
