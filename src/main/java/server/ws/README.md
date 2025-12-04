# server.ws

Пакет `server.ws` отвечает за сетевой уровень: WebSocket-сервер и обработку соединений.

Он принимает бинарные сообщения от клиентов, передаёт их в логику сервера и отправляет бинарные ответы обратно.

---

## Классы

### 1. `BlockchainWsEndpoint`
WebSocket-эндпоинт для одного соединения.

Роль:
- держит сессию с конкретным клиентом,
- принимает сообщения,
- вызывает бизнес-логику,
- отправляет ответ.

Публичные методы (Jetty WebSocket lifecycle):

- `onConnect(Session session)`  
  Вызывается Jetty при подключении клиента.  
  Сохраняет `session`, пишет лог.

- `onBinary(byte[] payload, int offset, int length)`  
  Клиент прислал бинарные данные.  
  Логика:
    1. Копируем полезные байты.
    2. Передаём их в `InboundMessageProcessor.process(...)`.
    3. Асинхронно отправляем ответ обратно через `session.getRemote().sendBytes(...)`.

  Ответ сервера — это либо `[4]statusCode`, либо `[4]OK + ...payload...` (в зависимости от операции).

- `onClose(int statusCode, String reason)`  
  Логируем закрытие сессии.

- `onError(Throwable cause)`  
  Логируем ошибку.

Внутренние (служебные):
- `trySendCode(int code)` — отправить просто код ошибки, если что-то пошло не так.

Замечание: сам `BlockchainWsEndpoint` не знает протокола. Он просто прокидывает байты в `InboundMessageProcessor`.

---

### 2. `WsServer`
Отдельный класс-ланчер. Поднимает Jetty WebSocket сервер.

Роль:
- стартует HTTP-сервер Jetty на порту `8080`,
- вешает WebSocket endpoint `/ws`,
- задаёт таймаут бездействия.

Публичный метод:
- `public static void main(String[] args)`  
  Запуск сервера. Делает:
    - `new Server(8080)`
    - создаёт `ServletContextHandler`
    - через `JettyWebSocketServletContainerInitializer.configure(...)` регистрирует маппинг `/ws` → `BlockchainWsEndpoint`
    - `server.start(); server.join();`

После запуска сервер слушает `ws://localhost:8080/ws`.

---

## Как это стыкуется с остальной системой

1. Клиент открывает WebSocket на `/ws`.
2. Шлёт бинарный пакет: `[4 байта opCode][дальше payload]`.
3. `BlockchainWsEndpoint.onBinary()` → `InboundMessageProcessor.process(...)`.
4. `InboundMessageProcessor` разбирает команду:
    - добавить блок
    - выдать блокчейн
    - поиск пользователей
    - ping
5. Ответ упаковывается в бинарный формат и отправляется обратно через `BlockchainWsEndpoint`.

---

## Кратко

- `WsServer` = сервер, который слушает порт и вешает `/ws`.
- `BlockchainWsEndpoint` = обработчик одного WebSocket-подключения, мост между сетью и логикой.
