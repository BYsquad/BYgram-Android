# Ghost Mode и Spy Mode

## Ghost Mode

`GHOST_ENABLED` — master. Поднастройки сохраняются при выключении master, но predicates возвращают блокировку только при включённом master.

| Опция | Контракт | Основные потребители |
|---|---|---|
| Не читать сообщения | `shouldBlockReadReceipts()` | cloud/secret receipts, content, mentions, reactions, live location |
| Не читать истории | `shouldBlockStoryReads()` | `StoriesController` |
| Не показывать online | `shouldBlockOnline()` | `MessagesController` online timer |
| Не показывать typing | `shouldBlockTyping()` | sending action и bot callback |
| Читать при действиях | `shouldReadOnActions()` + `withReadReceipt()` | send/reaction и явный mark-as-read |

`withReadReceipt()` использует `ThreadLocal` и снимает override в `finally`. Новая отправка receipt должна использовать общий predicate; не читайте raw preference в каждом caller.

Ghost блокирует только сетевые receipts. Локальные счётчики сообщений, mentions, reactions, live locations и stories обновляются как прочитанные единообразно. «Читать при действиях» временно разрешает receipt и для cloud-, и для secret chats; encrypted path передаёт текущую серверную дату.

Toggle master или `GHOST_NO_ONLINE` вызывает `updateTimerProc()`, чтобы новое online-состояние применилось без перезапуска.

## Spy Mode

### Сохранение удалённых сообщений

`SPY_SAVE_DELETED` не даёт физически удалить входящее сообщение и ставит custom message flag. Значок удаления отображается в строке времени перед галочками доставки. Покрыты обычные сообщения, channel delete, push delete, clear history и secret-chat random IDs/flush history. Данные остаются в SQLite и не входят в `clearSpyData()`.

При повторном локальном удалении сообщения с этим флагом `MessagesController.deleteMessages()` оставляет его в истории. Повторное действие не переводит сообщение в ветку физического удаления.

### История правок

Перед применением cloud edit сохраняются предыдущие text/entities/date. Одинаковая последняя версия пропускается, максимум — 20 ревизий на message key. Контекстное меню показывает историю в обратном порядке.

90-дневный prune запускается при старте приложения и удаляет каждую просроченную ревизию, даже если у сообщения есть более свежая история.

### Дата прочтения

`rememberReadDate()` обрабатывает outbox read updates для cloud и channel dialogs. Диапазоны ограничены 256 элементами и 90 днями.

Первый update получает предыдущий `outbox max` из `MessagesController`/storage и сохраняет весь новый диапазон `previousMax + 1 … maxMessageId`. Старая история до baseline не получает новую дату.

### Последний online

`rememberLastOnline()` принимает наблюдаемые status/read update timestamps. `getLastOnline()` не возвращает значения старше 90 дней; `pruneSpyData()` удаляет их при старте приложения.

### Очистка

`clearSpyData()` удаляет только ключи `bygram_edit_*`, `bygram_read_*`, `bygram_online_*`. Сохранённые удалённые сообщения в SQLite остаются; UI явно говорит «история правок, чтения и онлайн».

## Обязательная матрица тестов

| Сценарий | Ghost | Spy |
|---|---|---|
| Личный cloud chat | read/typing/online/action override | delete/edit/read date/online |
| Группа и форум | read/mention/reaction | delete/edit/read date |
| Channel | read | delete/read date |
| Secret chat | receipt/typing | random delete/flush/TTL |
| Stories | story receipt | — |
| Bot callback | action override/typing | — |
| Background/foreground | online | prune/persistence |

После теста возвращайте пользовательские switches и не подтверждайте destructive clear на реальном аккаунте.
