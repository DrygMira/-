# 07. Channels Feature Runbook (человеческое описание + диагностика)

## 1) Что уже сделано простыми словами

Сейчас реализован полный минимальный контур для каналов:

1. **Серверные read API**:
   - `ListSubscriptionsFeed` — экран списка каналов.
   - `GetChannelMessages` — сообщения конкретного канала.
   - `GetMessageThread` — дерево обсуждения для сообщения.

2. **UI вкладки Каналы**:
   - при открытии пытается загрузить реальный feed с сервера;
   - если сервер недоступен — fallback на мок-данные;
   - группы каналов выводятся в нужном порядке;
   - есть кнопка «Добавить канал», модалки подписки, переход в канал.

3. **Проверка уникальности имени канала на сервере**
   - в `AddBlock` при `CreateChannelBody` добавлена проверка;
   - при дубле возвращается `409 channel_name_already_exists`.

---

## 2) Что тестировать в первую очередь (быстрый чеклист)

### Базовый smoke
1. Авторизоваться в UI.
2. Открыть вкладку «Каналы».
3. Убедиться, что данные загрузились с сервера (или виден fallback-баннер).
4. Нажать любой канал — должен открыться экран канала с сообщениями.

### API smoke
1. Вызвать `ListSubscriptionsFeed`.
2. Для канала `ownedChannels[0]` вызвать `GetChannelMessages`.
3. Для первого `messages[0]` вызвать `GetMessageThread`.

### Ошибки
1. `ListSubscriptionsFeed` с пустым login -> `bad_fields`.
2. `GetChannelMessages` с битым channel payload -> `bad_fields`.
3. `GetMessageThread` с несуществующим block -> `message_not_found`.
4. `AddBlock(CreateChannel)` с уже существующим именем -> `channel_name_already_exists`.

---

## 3) Готовые JSON-запросы для ручной диагностики

## 3.1 ListSubscriptionsFeed
```json
{
  "op": "ListSubscriptionsFeed",
  "requestId": "debug-feed-1",
  "payload": {
    "login": "TestUser1",
    "limit": 200
  }
}
```

## 3.2 GetChannelMessages
```json
{
  "op": "GetChannelMessages",
  "requestId": "debug-ch-1",
  "payload": {
    "channel": {
      "ownerBlockchainName": "TestUser1-001",
      "channelRootBlockNumber": 0,
      "channelRootBlockHash": ""
    },
    "limit": 200,
    "sort": "asc"
  }
}
```

## 3.3 GetMessageThread
```json
{
  "op": "GetMessageThread",
  "requestId": "debug-thread-1",
  "payload": {
    "message": {
      "blockchainName": "TestUser1-001",
      "blockNumber": 123,
      "blockHash": "<hash-from-GetChannelMessages>"
    },
    "depthUp": 20,
    "depthDown": 2,
    "limitChildrenPerNode": 50
  }
}
```

---

## 4) Что смотреть в ответах

### ListSubscriptionsFeed
- `payload.login` — канонический login.
- `ownedChannels / followedUsersChannels / followedChannels` — массивы.
- у каждой записи есть:
  - `channel.channelRoot.blockNumber`,
  - `messagesCount`,
  - `lastMessage` (может быть null, если сообщений нет).

### GetChannelMessages
- `payload.channel` заполнен;
- `payload.messages[]` содержит:
  - `likesCount`, `repliesCount`,
  - `versionsTotal`, `versions[]`,
  - `text` должен быть текущей (последней) версией.

### GetMessageThread
- `payload.ancestors[]`, `payload.focus`, `payload.descendants[]`.
- у узлов должны быть версии и счетчики.

---

## 5) Частые проблемы и как быстро локализовать

1. **`status != 200`, code=bad_fields**
   - проверить вложенность payload и обязательные поля.

2. **`message_not_found` в GetMessageThread**
   - обычно передали blockNumber/hash не из `messageRef`.

3. **Пустой список сообщений в GetChannelMessages**
   - проверить `ownerBlockchainName` и `channelRootBlockNumber`.

4. **`channel_name_already_exists` при AddBlock**
   - это ожидаемо: в этой цепочке уже есть канал с таким именем.

---

## 6) Для будущей доработки

1. Добавить курсоры (пагинацию) для больших каналов.
2. Перевести «Подписаться»/«Добавить канал» в UI с демо-заглушек на реальные write RPC.
3. Добавить batch-агрегации для thread/versions (оптимизация).
4. Добавить полноценные интеграционные тесты на негативные кейсы и нагрузку.
