# Аудит раздела «Настройки BYgram»

Дата проверки: 16 сентября 2026 года.

## Итог

> Статус после исправлений 16 сентября 2026: BYG-01…BYG-09 закрыты. Ниже сохранены исходные доказательства аудита; пометки `FAIL` в исходной матрице описывают состояние до исправлений.

| Пункт | Исправление |
|---|---|
| BYG-01 | `denied` возвращает безопасную ссылку на официальный бот без activation token; UI использует `BygramLicenseBuy`. |
| BYG-02 | ACL хранит dirty-состояние, запросы сериализованы, подтверждаются ответом сервера и повторяются; публикация ждёт ACK. |
| BYG-03 | `pruneSpyData()` запускается из `ApplicationLoader`. |
| BYG-04 | Source и русский перевод синхронизированы с Crowdin; approved-export даёт 100% и проверяется равенством ключей. |
| BYG-05 | Secret chats используют отрицательный message ID и ненулевую серверную дату в read-on-action. |
| BYG-06 | Ghost обновляет локальные content/mention/reaction/location/story-состояния, блокируя только сеть. |
| BYG-07 | Первый диапазон начинается после фактического предыдущего outbox max из controller/storage. |
| BYG-08 | Prune удаляет отдельные просроченные ревизии массива. |
| BYG-09 | Checks обновлены под Telegram Login Widget и текущие экраны; backend self-test использует временную локальную MySQL-базу. |

Раздел компилируется в редакциях Full и Lite, все 20 активных глобальных ключей имеют UI и хотя бы одного consumer. Быстрые проверки текста, UTF-8, Zalgo, ID и entity offsets проходят. При этом раздел нельзя считать полностью готовым: подтверждены два существенных дефекта лицензируемой Activity Sharing, три неполных сценария Ghost/Spy, рассинхронизация Crowdin и устаревшая тестовая инфраструктура.

Главные риски:

1. свежий пользователь без лицензии не может перейти из приложения к покупке/активации функции Activity Sharing;
2. отзыв доступа к активности может не попасть на сервер после сетевой ошибки, а повторной синхронизации нет;
3. заявленная 90-дневная очистка Spy-данных не запускается;
4. обязательная загрузка переводов Crowdin перед сборкой удаляет актуальные русские строки Activity Sharing.

## Область проверки

Проверены:

- корневой экран, переходы и внешние ссылки;
- «Кастомизация → Общие» и «Кастомизация → Профиль»;
- «Другое»;
- Ghost Mode;
- Spy Mode;
- Full-only Activity Sharing и лицензирование;
- хранение, defaults, export/reset и account scope;
- все найденные consumers настроек;
- сборка Full и компиляция Lite;
- документация и имеющиеся regression/smoke checks.

По указанию проекта не рассматривались функции ExteraGram AI, Camera X, вкладка плагинов и Vosk. Внешняя Extera badge-интеграция не оценивалась как функция настроек BYgram.

## Метод и уровень уверенности

Обозначения:

- **PASS-AUTO** — выполнена автоматизированная проверка реальной логики;
- **PASS-STATIC** — UI, persistence и consumer прослежены до реализации, проект компилируется;
- **BLOCKED-E2E** — нужен авторизованный Telegram-аккаунт или контролируемая серверная среда;
- **FAIL** — дефект воспроизведён детерминированной проверкой либо непосредственно следует из полного пути выполнения.

Динамический UI-smoke самих настроек не завершён: чистый эмулятор показывает экран ввода номера Telegram. Подделка авторизации не использовалась. Поэтому пункты, требующие реального аккаунта, не помечены как полностью проверенные.

## Подтверждённые дефекты

### BYG-01 — P1 — свежий пользователь не получает путь к покупке Activity Sharing

**Статус:** FAIL.

**Сценарий:** пользователь BYgram Full без действующей лицензии открывает Activity Sharing.

**Фактический путь:**

1. `refreshLicense()` при отсутствии access token вызывает `authenticate()` (`BygramActivityController.java:144-153`).
2. `device_start` для нелицензированного Telegram ID немедленно возвращает `state=denied` до создания activation и `bot_url` (`server/activity-api/index.php:149-157`).
3. `markDenied()` возвращает UI `false, ""` (`BygramActivityController.java:246-250`).
4. экран добавляет положительную кнопку только при непустом `botUrl` (`BygramSettingsActivity.java:313-323`).

В результате пользователь видит сообщение о покупке через официального бота, но диалог содержит только отмену. Строка `BygramLicenseBuy` существует, однако не используется.

**Ожидание:** для `denied` сервер или клиент должен вернуть безопасный URL официального бота без activation token; после выдачи лицензии должен запускаться отдельный device binding flow.

### BYG-02 — P1 — отзыв ACL Activity Sharing не гарантирован после ошибки сети

**Статус:** FAIL, риск приватности.

При изменении разрешения UI сразу сохраняет новую маску локально и вызывает `updateViewers()` (`BygramSettingsActivity.java:521-533`). `updateViewers()` отправляет запрос с `callback=null` (`BygramActivityController.java:340-349`): код не знает, принят ли ACL сервером, не хранит dirty-флаг и не повторяет синхронизацию после восстановления сети.

Если при включённом master пользователь отозвал доступ офлайн, сервер сохраняет старый ACL. После возврата сети heartbeat активности снова начнёт отправляться, но старый viewer ACL может остаться действующим неограниченно долго.

**Ожидание:** хранить pending/dirty ACL по account, подтверждать успешный ответ и повторять замену ACL до подтверждения. До успешного отзыва безопаснее не возобновлять публикацию активности.

### BYG-03 — P2 — 90-дневный prune Spy-данных не вызывается

**Статус:** FAIL.

`BygramConfig.pruneSpyData()` объявлен (`BygramConfig.java:446-469`), но во всём Java-коде нет ни одного caller. Следствия:

- старые `bygram_edit_*` могут храниться бессрочно;
- старые `bygram_online_*` физически остаются в SharedPreferences, хотя getter перестаёт их показывать;
- старые read ranges очищаются только при поступлении нового read update для того же диалога.

Документация `privacy-modes.md` ошибочно утверждает, что prune выполняется при старте приложения.

### BYG-04 — P2 — Crowdin откатывает актуальные русские строки перед обязательной сборкой

**Статус:** FAIL в обязательном build workflow.

Команда

```powershell
crowdin download translations --export-only-approved --skip-untranslated-strings
```

успешно завершается, но изменяет `values-ru/bygram_strings.xml`: возвращает старое название «С кем они общаются?», старую модель приватности и удаляет 13 новых строк категорий/ACL. После download в русской локали отсутствуют 26 BYgram-строк, включая `BygramActivity*`, device-limit, Firebase, Material 3 и activation labels; Android использует английский fallback.

Изменение Crowdin после проверки откатано, чтобы аудит не менял продуктовые строки.

**Ожидание:** загрузить текущие source strings/context в Crowdin, утвердить русские переводы и добавить проверку равенства обязательного набора ключей для `values`/`values-ru` после download.

### BYG-05 — P2 — «Читать при действиях» не работает в secret chats

**Статус:** FAIL по трассировке пути.

Отправка сообщения вызывает `markDialogAsReadByAction(peer, dialog.top_message)` (`SendMessagesHelper.java:4325-4329`). Secret-chat message IDs локальные и отрицательные (`UserConfig.lastSendMessageId` начинается с `-210000`). Helper сразу возвращает при `maxId <= 0` (`MessagesController.java:14891-14895`). Кроме того, он передаёт `maxDate=0`, а encrypted-ветка `markDialogAsRead()` требует ненулевую дату (`MessagesController.java:14798-14805`).

Подпись настройки обещает чтение при отправке сообщения без исключения для secret chats.

### BYG-06 — P2 — локальное unread-состояние Ghost Mode обрабатывается непоследовательно

**Статус:** частично реализовано, требуется уточнение контракта.

Основной `markDialogAsRead()` при Ghost Mode обновляет локальный unread state, но не создаёт серверный read task. Другие пути возвращаются до локального обновления:

- content/mention: `MessagesController.java:14445-14507`;
- all mentions: `MessagesController.java:14717-14729`;
- reactions: `MessagesController.java:21681-21695`;
- live locations: `LocationController.java:904-918`;
- stories: `StoriesController.java:1284-1300`.

Из-за этого основной счётчик диалога может исчезнуть локально, а mention/reaction/content/story продолжит выглядеть непрочитанным. Нужно либо разделить local update и network receipt во всех путях, либо явно документировать единый режим «оставлять непрочитанным локально» и привести основной путь к нему.

### BYG-07 — P3 — первая сохранённая дата чтения покрывает только один message ID

**Статус:** известная неполная реализация.

При первом outbox read update `rememberReadDate()` создаёт диапазон `start=maxMessageId, end=maxMessageId` (`BygramConfig.java:381-385`). Если update подтверждает пачку сообщений, более ранние сообщения этой пачки не получают дату.

Корректное решение требует baseline, снятого в момент включения функции, либо надёжного предыдущего outbox max. Ограничение уже описано в `privacy-modes.md`, но UI не сообщает о нём.

### BYG-08 — P3 — prune истории правок не соблюдает retention для отдельных ревизий

**Статус:** известная неполная реализация.

`pruneSpyData()` удаляет весь `bygram_edit_*` только если последняя ревизия старше cutoff (`BygramConfig.java:454-456`). Если есть свежая ревизия, более старые 90 дней элементы массива остаются. Лимит 20 ревизий ограничивает объём, но не срок хранения.

### BYG-09 — P2 — regression/smoke инфраструктура не соответствует текущей реализации

**Статус:** FAIL для воспроизводимой приёмки.

- `Tools/check_bygram_settings_fixes.py` ожидает старую challenge-ссылку `?text=` и старые фрагменты UI; падают 2 из 14 контрактов.
- `Tools/check_bygram_md3_geometry.py` требует Mini App (`telegram-web-app.js`, `validateMiniApp`), хотя текущая админка правильно использует Telegram Login Widget; скрипт падает после успешных MD3/Liquid Glass assertions.
- `Tools/check_bygram_preferences.py` проверяет удалённые экраны «Основные / Внешний вид / Чаты» и на текущем эмуляторе завершается на `uiautomator dump` с exit 137.
- `server/activity-api/self-test.php` не изолирован: локально пытается подключиться к production-style MySQL config и завершается `Access denied` вместо запуска на fixture/test DB.
- `docs/bygram-settings/README.md` ссылался на отсутствующий аудит.

Без обновления этих checks зелёная сборка не доказывает работоспособность раздела.

## Матрица функций

### Корневой экран

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| Переход в настройки BYgram | PASS-STATIC | `SettingsActivity` открывает `BygramSettingsActivity`; Full APK компилируется и устанавливается |
| Кастомизация / Другое / Ghost / Spy | PASS-STATIC | Все click handlers ведут в существующие fragments |
| Activity Sharing | FAIL | Экран Full-only подключён, но BYG-01 и BYG-02 блокируют готовность |
| Канал / чат / Crowdin | PASS-STATIC | URL сопоставлены правильно; live-open не выполнялся без аккаунта |
| Lite скрывает Activity Sharing | PASS-AUTO | `Tools/check_bygram_editions.py` проходит; Lite-компиляция успешна |

### Кастомизация → Общие

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| 3 иконки приложения | PASS-STATIC | Используется существующий `AppIconsSelectorCell`/manifest aliases; reset возвращает `DEFAULT`; переключение на устройстве не выполнено |
| Фильтр Zalgo | PASS-AUTO | `BygramTextCheck` проверил обычный текст, accents, emoji, Zalgo, offsets и bidi controls |
| Material 3 loading | PASS-STATIC | Consumers: `CircularProgressDrawable`, `RadialProgressView`; geometry assertions прошли до несвязанного падения скрипта |
| Material 3 sliders | PASS-STATIC | Consumer: `SeekBarView`; намеренно только простой one-way slider |
| Material 3 switches | PASS-STATIC | Consumer: общий `Switch` |
| Углы аватаров 0…28 | PASS-STATIC / BLOCKED-E2E | Clamp и общий `ImageReceiver` подключены; preview/device pixel-check требует авторизованный экран |
| Liquid Glass: media | PASS-STATIC | Consumer в `PhotoViewer` |
| Liquid Glass: context panels | PASS-STATIC | Consumers в `ChatActivity`/`DialogsActivity` |
| Liquid Glass: floating buttons | PASS-STATIC | Consumer в `DialogsActivity` |
| Liquid Glass: search | PASS-STATIC | Consumers в chat/dialog search surfaces |
| Liquid Glass: selection | PASS-STATIC | Consumer в `ChatActivity` selection UI |
| Liquid Glass: popups | PASS-STATIC | Consumers в `ChatAttachAlert`/`ShareAlert` |

Изменения Liquid Glass требуют повторного открытия активного экрана; это правильно указано в UI.

### Кастомизация → Профиль

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| Preview badges | PASS-STATIC / BLOCKED-E2E | Использует production component; серверный ответ не проверялся |
| Custom profile music | PASS-STATIC | Consumers в `ProfileActivity`/`ProfileMusicView`, media не удаляется |
| Profile ID hidden/API/Bot API | PASS-AUTO | Формула `profileId()` покрыта JVM check; отображение и clipboard используют один helper |
| DC рядом с ID | PASS-STATIC | Берётся из photo `dc_id`, при отсутствии показывается `—` |

### Другое

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| Crashlytics toggle | PASS-STATIC | Применяется сразу и при старте; default `true` |
| Analytics toggle | PASS-STATIC | Применяется сразу и при старте; default `false` |
| Экспорт JSON | PASS-STATIC / BLOCKED-E2E | `ACTION_CREATE_DOCUMENT`, UTF-8, allowlist; provider flow не пройден |
| Импорт | Не реализован по текущему контракту | Экспорт нельзя использовать как полный backup/restore |
| Сброс | PASS-STATIC / BLOCKED-E2E | Сбрасывает UI keys/activity/icon/telemetry; accounts, tokens и Spy history сохраняются по документации |

### Ghost Mode

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| Master и сохранение поднастроек | PASS-STATIC | Все predicates зависят от master, значения поднастроек не удаляются |
| Не читать обычные сообщения | PASS-STATIC | Network read task блокируется, локальный основной unread state обновляется |
| Не читать stories | PASS-STATIC | Receipt блокируется; локальная story остаётся unread, см. BYG-06 |
| Не показывать online | PASS-STATIC | Timer predicate подключён; toggle вызывает `updateTimerProc()` |
| Не показывать typing | PASS-STATIC | Общий `sendTyping()` блокируется |
| Читать при действиях | FAIL частично | Cloud send/reaction/bot button подключены; secret chats не покрыты, см. BYG-05 |
| Secret-chat receipts | PASS-STATIC | `SecretChatHelper.sendMessagesReadMessage()` использует общий predicate |

### Spy Mode

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| Сохранять удалённые | PASS-STATIC / BLOCKED-E2E | Обычные, channel/push, clear history и secret random/flush пути подключены; реальная матрица чатов не выполнена |
| История правок | PASS-STATIC | Сохраняется предыдущая версия, dedupe, максимум 20; retention неполный (BYG-03/BYG-08) |
| Дата прочтения | PASS-STATIC | Cloud/channel ranges подключены; первый range неполный (BYG-07) |
| Последний online | PASS-STATIC | Consumers/getter подключены; физическая очистка не запускается (BYG-03) |
| Очистить Spy-данные | PASS-STATIC / BLOCKED-E2E | Удаляет edit/read/online keys; удалённые сообщения в SQLite намеренно остаются |

### Activity Sharing (Full)

| Функция | Результат | Доказательство / ограничение |
|---|---|---|
| License/device binding | FAIL | Свежий unlicensed flow не даёт открыть бота (BYG-01) |
| Лимит/замена устройства | PASS-STATIC / BLOCKED-E2E | Signed replace path и server lock существуют; нужен лицензированный test dataset |
| Master per account | PASS-STATIC | Account-suffixed key, default off |
| Поиск пользователей | PASS-STATIC | `Locale.ROOT`, selected unknown ID отображается как `ID …` и остаётся удаляемым |
| Добавить/удалить viewer | FAIL по надёжности | Локально работает, server sync без ACK/retry (BYG-02) |
| Разрешение dialogs/profile/chat | FAIL по надёжности | Биты и server filtering корректны статически, доставка ACL ненадёжна |
| Heartbeat/TTL/clear | PASS-STATIC / BLOCKED-E2E | 20 s heartbeat, 60 s server TTL задокументирован; live integration не выполнена |
| Batch display cache | PASS-STATIC | 150 ms batching, cache 10 s |

## Хранение, export и reset

- Активные UI settings используют allowlist, а не prefix matching.
- Ghost/Spy/customization/telemetry глобальны для приложения; Activity master/viewers привязаны к индексу Telegram account.
- Export не включает Spy history, viewers, tokens, credentials и badges.
- Reset не является privacy wipe: он сохраняет access/refresh tokens и Spy history; UI сообщает о сохранении accounts/history.
- Legacy `bygram_activity_enabled` мигрируется только при открытии корневого экрана BYgram, где вызывается `clearRemovedCustomizationSettings()`.

## Выполненные проверки

| Проверка | Результат |
|---|---|
| Crowdin approved download | Команда успешна; выявлен BYG-04 |
| Full `assembleAfatDebug` | PASS, 5 min 5 s |
| Lite `compileAfatDebugJavaWithJavac` | PASS, 52 s |
| Установка Full APK | PASS, основной package `com.bygram.mobile`, 12.10.1 (70389) |
| `Tools/BygramTextCheck.java` | PASS |
| `Tools/check_bygram_editions.py` | PASS |
| `php -l index.php`, `php -l admin-api.php` | PASS |
| Таргетированная проверка BYG-01/02/03 | RED для всех трёх, детерминированно |
| `check_bygram_settings_fixes.py` | FAIL: 2 устаревших контракта |
| `check_bygram_md3_geometry.py` | FAIL на устаревшем admin auth assertion после прохождения geometry assertions |
| `activity-api/self-test.php` | BLOCKED: нет изолированной test DB, локальный MySQL отклонил server user |
| Android UI settings smoke | BLOCKED: эмулятор не авторизован; текущий экран — ввод номера; legacy smoke дополнительно падает на `uiautomator` exit 137 |

При сборке не менялись `APP_VERSION_*`, `APP_PACKAGE`, версии зависимостей и production server. FTP/MySQL production не использовались.

## Что проверить вручную после исправлений

Нужны чистый авторизованный test account и два лицензированных аккаунта Full:

1. пройти каждый switch с уходом/возвратом и force-stop/relaunch;
2. проверить три launcher icons и вернуть исходную;
3. проверить avatar radius 0, 1, промежуточное значение и 28, включая forum avatar;
4. проверить каждую MD3/Liquid Glass область на реальном consumer;
5. экспортировать JSON и проверить отсутствие history/viewers/tokens;
6. проверить Ghost в cloud/group/forum/channel/secret/story/bot callback;
7. проверить Spy delete/edit/read/online на контролируемых сообщениях;
8. для ACL отдельно смоделировать offline revoke → reconnect и убедиться, что старый viewer больше не видит heartbeat;
9. проверить unlicensed purchase → license grant → device bind → второй device → replace;
10. восстановить все исходные switches и тестовые ACL.

## Приоритет исправлений

1. BYG-01 и BYG-02 — блокеры релиза Full Activity Sharing.
2. BYG-03 — подключить prune при старте/периодическом безопасном maintenance и добавить regression check.
3. BYG-04 — синхронизировать Crowdin до следующей обязательной сборки.
4. BYG-05/BYG-06 — определить единый Ghost contract и покрыть secret/local unread paths.
5. BYG-07/BYG-08 — довести retention и baseline Spy data.
6. Переписать checks под текущую карту экранов и изолировать backend self-test.
