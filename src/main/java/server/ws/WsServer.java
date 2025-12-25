package server.ws;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.config.AppConfig;

import java.time.Duration;

/**
 * WsServer — поднимает Jetty WS на /ws.
 *
 * ВАЖНО:
 *  - перед стартом сервера выполняем recovery tmp-блокчейнов.
 *  - если обнаружена несогласованность, которую сервер сам чинить не может —
 *    recovery бросает исключение и сервер не стартует.
 */
public final class WsServer {

    private static final Logger log = LoggerFactory.getLogger(WsServer.class);

    public static void main(String[] args) throws Exception {

        // ============================================================
        // 0) Восстановление консистентности blockchain файлов
        // ============================================================
        try {
            BlockchainTmpRecoveryOnStartup.runRecoveryOrThrow();
        } catch (Exception e) {
            // Уже должно быть “большое” уведомление через BlockchainAdminNotifier,
            // но на всякий случай логируем ещё раз.
            log.error("❌ Сервер НЕ будет запущен: критическая ошибка восстановления blockchain tmp-файлов.", e);
            throw e; // останавливаем запуск
        }

        // ============================================================
        // 1) Настройки порта
        // ============================================================
        AppConfig config = AppConfig.getInstance();
        int port = 7070;
        try {
            String portStr = config.getParam("server.port");
            if (portStr != null && !portStr.isBlank()) {
                port = Integer.parseInt(portStr.trim());
            }
        } catch (Exception e) {
            log.info("Не удалось прочитать параметр server.port, используем порт по умолчанию {}", port);
        }

        // ============================================================
        // 2) Запуск Jetty WS
        // ============================================================
        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        // Инициализация контейнера WebSocket
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            // Таймаут простоя соединения (Jetty 11 синтаксис)
            wsContainer.setIdleTimeout(Duration.ofMinutes(5));

            // Маппинг эндпоинта
            wsContainer.addMapping("/ws", (req, resp) -> new BlockchainWsEndpoint());
        });

        server.start();
        log.info("✅ WS сервер запущен на ws://localhost:{}/ws", port);
        server.join();
    }
}