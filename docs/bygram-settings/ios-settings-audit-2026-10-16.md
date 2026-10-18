# Повторная проверка переноса настроек BYgram Android → iOS

> Это зафиксированный результат проверки **до исправлений**. По следующему запросу пользователя подготовлен код недостающих настроек с адаптацией под iOS. Изменения, проверка и команды для Mac: [отчёт о реализации](../../../BYgram-ios/docs/bygram-native-settings.md). Сборка и runtime пока не подтверждены.

Дата: 16 октября 2026 года. Проверка выполняется по текущим локальным исходникам.

## Основание и границы проверки

- Android: `C:/project/BYgram`, HEAD `a2ab53df41d17b0540bae95983bb6f7c92a11e5b`.
- iOS: `C:/project/BYgram-ios`, HEAD `f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838`.
- Проверяется фактическое рабочее дерево, включая изменения сверх HEAD.
- Старые отчёты о переносе не используются как доказательство реализации или полноты.
- Единица проверки: доступный из настроек Android пункт, его варианты, сохранение и применение; для вложенных экранов — их собственные действия.
- Наличие строки, поля модели или переключателя само по себе не подтверждает перенос поведения.
- Это статическая проверка. Сборка iOS и запуск на устройстве в Windows не выполняются. Production API и FTP не изменяются.

## Ход проверки

1. Создан новый отчёт до сопоставления функций. Прочитаны правила проекта и iOS CLAUDE.md.
2. Прочитаны Android `BygramSettingsActivity.java` целиком по разделам, iOS `BygramSettingsController.swift`, `BygramServicesController.swift`, модель `BygramSettings` и серверные consumers.
3. Найдено воспроизводимое по контракту расхождение замены устройства: сервер отдаёт `devices[].id`, iOS UI требует `devices[].device_id` и пропускает все такие строки.
4. Проверены определения и найденные вызовы Ghost/Spy, activity, typing, profile visits, приватных чатов; отдельно прочитаны участки применения и серверный контракт.
5. Выполнены локальные assert-проверки исходников: несовпадение поля устройства и ограничения длины, отсутствие семи Android-ключей в iOS-модели и действий 6/9 в typing mapper. Проверены существующие XCTest и подключение их файлов к BUILD; сами XCTest не запускались.
6. Составлены окончательная матрица, список расхождений и обязательные сценарии проверки на macOS/iPhone.

Поиск Semble MCP и предусмотренный CLI fallback были запущены; результаты MCP поступили уже после составления матрицы. Для чтения использованы точные пути из документации Android и `git status` iOS; `rg` применялся для всех вхождений уже известных ключей и вызовов. Старый iOS-отчёт о переносе не читался. Сервисная заметка iOS была просмотрена при первичном ознакомлении с docs, но её утверждения не использованы как доказательства: результаты ниже установлены заново из кода.

## Результаты

**Полный перенос всех возможностей настроек BYgram Android на iOS не выполнен.**

У iOS есть реальный вход в BYgram Settings, сохранение основных параметров, подключённые Ghost/Spy и часть серверных функций. Однако отсутствуют доступные пользователю приватные чаты, несколько настроек оформления и телеметрии; просмотры профиля и custom typing перенесены не полностью. В выборе замены лицензионного устройства найден дефект контракта, блокирующий этот сценарий.

В основной матрице 46 проверяемых пунктов: 27 со статусом «Код», 5 «Частично», 13 «Нет», 1 «Дефект». Это число строк сценариев, а не число независимых переключателей.

Статус «Код» в матрице означает подтверждённую статическую цепочку UI → значение → consumer, **а не** успешный запуск или доказательство отсутствия любых обходов. Процент «полного переноса» не вычисляется: вес строк различается, часть функций требует платформенного решения и вся iOS-сборка не проверена.

## Карта источников

Сокращения в матрице относятся к этим файлам. Номера строк — в текущем рабочем дереве; после редактирования могут сместиться.

| Обозначение | Источник |
|---|---|
| A | [Android: BygramSettingsActivity.java](../../TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java) — все пользовательские экраны раздела |
| AC | [Android: BygramConfig.java](../../TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java) — ключи, defaults, export/reset, Spy |
| AT | [Android: BygramTypingController.java](../../TMessagesProj/src/main/java/org/telegram/messenger/BygramTypingController.java) — typing и посещения |
| AM | [Android: MessagesController.java](../../TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java) — применение custom typing, в частности строки 11408–11420 и 21985–22006 |
| I | [iOS: BygramSettingsController.swift](../../../BYgram-ios/submodules/SettingsUI/Sources/BygramSettingsController.swift) — доступные пользователю настройки |
| IS | [iOS: BygramServicesController.swift](../../../BYgram-ios/submodules/SettingsUI/Sources/BygramServicesController.swift) — Full, зрители, typing, посещения |
| IC | [iOS: BygramSettings.swift](../../../BYgram-ios/submodules/TelegramCore/Sources/Settings/BygramSettings.swift) — global settings, export/reset |
| IR | [iOS: BygramRemoteFeatures.swift](../../../BYgram-ios/submodules/TelegramCore/Sources/Settings/BygramRemoteFeatures.swift) — account settings и применение серверных функций |
| API | [iOS: BygramService.swift](../../../BYgram-ios/submodules/TelegramCore/Sources/Settings/BygramService.swift) — подпись запросов, Keychain, лицензия |
| SPY | [iOS: BygramSpyStore.swift](../../../BYgram-ios/submodules/TelegramCore/Sources/Settings/BygramSpyStore.swift) — история, read ranges, online |
| PRIV | [iOS: BygramPrivacyController.swift](../../../BYgram-ios/submodules/SettingsUI/Sources/BygramPrivacyController.swift), [BygramChatPrivacy.swift](../../../BYgram-ios/submodules/TelegramCore/Sources/Settings/BygramChatPrivacy.swift) — недоступный из Settings прототип |
| STATE | [iOS: AccountStateManagementUtils.swift](../../../BYgram-ios/submodules/TelegramCore/Sources/State/AccountStateManagementUtils.swift) — сохранение наблюдаемых событий |
| PROFILE | [iOS: PeerInfoProfileItems.swift](../../../BYgram-ios/submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/Sources/PeerInfoProfileItems.swift) — ID, online, activity, badge |
| MENU | [iOS: ChatInterfaceStateContextMenus.swift](../../../BYgram-ios/submodules/TelegramUI/Sources/ChatInterfaceStateContextMenus.swift) — история, сведения, read date и медиа |
| SERVER | [Android/backend: index.php](../../server/activity-api/index.php) — локальный контракт сервера, не проверка production |

Область отчёта — **добавленный раздел «Настройки BYgram»** со всеми его вложенными экранами и поведением, которое они включают. Полная сверка штатных настроек Telegram Android/iOS (кошелёк, обычные темы, уведомления Telegram и т. п.) сюда не входит. Брендинг и дополнительные действия сообщений отмечены отдельно, чтобы не смешивать их с переключателями.

## Матрица всех настроек и пользовательских сценариев

Статусы: **Код** — цепочка реализации подтверждена; **Частично** — есть конкретное расхождение; **Нет** — сценарий из Android-настроек недоступен в iOS-разделе; **Дефект** — присутствующий код не соответствует необходимому контракту.

| № | Android: функция или сценарий | iOS | Статус | Доказательство / ограничение |
|---:|---|---|---|---|
| 01 | Вход в настройки BYgram | Пункт в Settings и отдельный контроллер | Код | `PeerInfoSettingsItems.swift:260`, `PeerInfoScreenSettingsActions.swift:54`; I:86. Вложенность разделов другая, это само по себе не потеря функции. |
| 02 | Главный переключатель Ghost | Есть; дочерние флаги сохраняются отдельно | Код | A:1038; I:155; IC:7–27, 54–76. Defaults совпадают: master false, четыре запрета true, read-on-actions false. |
| 03 | Не отправлять прочтения сообщений | Есть блокировки обычной синхронизации и contents | Код | `SynchronizePeerReadState.swift:179,254`, `ApplyMaxReadIndexInteractively.swift:45,217`, `ManagedConsumePersonalMessagesActions.swift:256`, `ManagedSynchronizeConsumeMessageContentsOperations.swift:113`, `ReplyThreadHistory.swift:480`; секретный outgoing path тоже проверяет флаг. |
| 04 | Не отмечать истории просмотренными | Обычные и закреплённые истории | Код | `ManagedSynchronizeViewStoriesOperations.swift:122`, `Stories.swift:2075`. Локальное состояние и отправка серверу разделены. |
| 05 | Не показывать online | Реактивно меняет presence текущих аккаунтов | Код | `ManagedAccountPresence.swift:25–70`, IC:42–52. |
| 06 | Не отправлять typing | Проверяется до отправки activity | Код | `ManagedLocalInputActivities.swift:145`. |
| 07 | Прочитывать при действиях | Выделенный разрешённый запрос для действия | Код | `BygramReadActions.swift:7–80`; вызовы в `EnqueueMessage.swift:844`, `MessageReactions.swift:35,181`, `RequestMessageActionCallback.swift:172`, `TelegramEngineMessages.swift:933–986`. Сетевые ошибки и отложенная отправка требуют runtime-сценариев. |
| 08 | Сохранять удалённые сообщения | Есть признак удалённого сообщения и retention paths | Код | I:169; `SyncCore_BygramDeletedMessageAttribute.swift:19–43`, `DeleteMessages.swift:42,88`, cloud/secret call sites, `StringForMessageTimestampStatus.swift:254`. Сохраняются входящие обычные сообщения; TTL/autoclear/ephemeral исключены. Полнота всех вариантов server deletion без runtime не доказана. |
| 09 | История редактирования | Сохраняет предыдущую версию и открывает историю | Код | STATE:4645–4652, SPY:61–95, MENU:1282–1301, I:261+. Есть поддержка rich revisions, до 20 версий и 90 дней. |
| 10 | Запоминать дату прочтения | ReadOutbox → локальные диапазоны → меню | Код | STATE:4758–4772, SPY:97–124, MENU:1303. До 256 диапазонов, 90 дней; наблюдаемая дата, не восстановление неизвестных прошлых событий. |
| 11 | Запоминать последний замеченный online | Presence/свежие события → store → профиль | Код | STATE:5118–5123 и вызовы `bygramObservePeerActivity`; SPY:126–147; PROFILE:106. |
| 12 | Очистить данные Spy | Отдельное подтверждение и clear | Код | A:1100–1106, I:132–142, SPY:149–155. На обеих платформах удаляются edit/read/online keys, а не сохранённые сообщения из БД. |
| 13 | Кастомная музыкальная карточка профиля | Переключателя и BYgram-ключа нет | Нет | A:714, AC:39; Android consumer `ProfileMusicView.java:112,353,439`. Наличие обычной музыки Telegram iOS не заменяет эту настройку. |
| 14 | ID в профиле: скрыт / API / Bot API | Три режима и копирование ID | Код | A:717–735; I:115–130; IC:79–87; PROFILE:81–103; `BygramText.swift:4–17`. |
| 15 | Выбор иконки BYgram / Telegram | В BYgram-разделе выбора этой пары нет | Нет | A:639–641, 665; I:155–198. Наличие штатных альтернативных иконок iOS не засчитывается как подтверждение именно пары Android BYgram/Telegram. |
| 16 | Фильтр Zalgo | Алгоритм перенесён, область применения уже | Частично | A:667; Android `Emoji.java:590` и `BygramTextUtils.java:35`; iOS `StringWithAppliedEntities.swift:224` требует `!external && message != nil`. Нет основания считать фильтрацию остальных текстовых поверхностей равной Android. |
| 17 | Material 3: индикаторы загрузки | Управления нет | Нет | A:675–677; AC:43; Android `CircularProgressDrawable.java:70`. Требуется решение продукта о платформенном эквиваленте. |
| 18 | Material 3: слайдеры | Управления нет | Нет | A:678–679; AC:44; Android `SeekBarView.java:612` читает строковый ключ. |
| 19 | Material 3: переключатели | Управления нет | Нет | A:680–681; AC:45; Android `Switch.java:54,58,431`. `.glass` у iOS-строк — фиксированный стиль, не перенос независимого переключателя. |
| 20 | Скругление аватаров 0…28 и preview | Настройки и BYgram-ключа нет | Нет | A:642–662, 684–685; AC:217–220; Android consumers: ImageReceiver, ProfileActivity, ActionBar, Stories, reactions, avatar lists. |
| 21 | Редактировать кастомные статусы печати (Full) | Есть редактор, включая лишний Cancel | Частично | A:535–601 (action 2 исключён), IS:102,145–152; IR:65–78. iOS считает 64 grapheme clusters, сервер — `mb_strlen`, Android — UTF-16: Unicode-строки могут пройти UI и быть отвергнуты сервером. |
| 22 | Опубликовать статусы / подтвердить через бота | Есть signed typing_put и регистрация | Код | A:604–622; IS:150,154; IR:321–330; API:286+. Подтверждение бота требует повторной публикации, как и на Android. |
| 23 | Показать кастомную фразу для каждого Android typing action | Не все действия сопоставлены | Частично | AM:11414–11416 и 22001; IR:145–157 не возвращает 6 (record video) и 9 (upload audio). Ввод этих фраз есть, соответствующего применения нет. |
| 24 | Разделение Lite / Full | Build define и runtime gate | Код | `Telegram/BUILD:77–92,1867`, API:82; IS:109. Lite не показывает Full-действия; посещения и badges имеют отдельные пути. Проверка собранных редакций не выполнена. |
| 25 | Активация / проверка лицензии | Ключ P-256, bot flow, access/refresh в Keychain | Код | A:740–775; API:14–74,144–263; IS:54–70,110. Без живого сервера end-to-end не подтверждено. |
| 26 | При лимите устройств выбрать заменяемое устройство | UI пропускает устройства ответа сервера | Дефект | IS:111–112 требует `device_id`, SERVER:80–83 отдаёт `id`; A:778–794 читает `id`. Обработчик замены существует, но до него нельзя дойти по такому ответу. |
| 27 | Включить публикацию активности | Account-scoped master | Код | A:908,961–965; IS:120–122; IR:48–49,241–272. |
| 28 | Добавить / удалить зрителей, поиск пользователя | Нативный peer selector; удаление через обнуление прав | Код | A:911–940,979–984; IS:123–143; IR:50–60. iOS показывает ID вместо имени в строках разрешений; при снятии всех битов зритель удаляется. |
| 29 | Отдельные права: список чатов / профиль / чат | Маска 1/2/4 | Код | A:919–921; IS:136–142; IR:50–60; SERVER:377,390–401. Не только один общий список зрителей. |
| 30 | Публикация текущего экрана: список, профиль, чат | enter/leave, timer, clear/background | Код | `ChatListController.swift:2381,2866`, `PeerInfoScreen.swift:7088`, `ChatController.swift:7869,8305`; IR:200–272. TTL 60 s, heartbeat 7 s, ACL отправляется до update. |
| 31 | Смотреть разрешённую активность другого пользователя | Профиль, список, chat title | Код | PROFILE:121; `ChatListItem.swift:2640`, `ChatTitleView.swift:625`, `ChatTitleComponent.swift:744`; IR:275–307. |
| 32 | Скрытые чаты: список, добавить, удалить, открыть | Прототип есть, входа из Settings нет | Нет | A:109–119,330–491; PRIV:45–46 прямо указывает недоступность; поиск всех `bygramPrivacyController` дал только определение. Guards и модель не делают сценарий доступным. |
| 33 | Создать / сменить PIN, открыть скрытую вкладку из поиска | Нет законченного пользовательского маршрута | Нет | A:395–403,466–479; PRIV содержит редактор PIN, но не вызван из настроек; в iOS search нет соответствующей PIN-вкладки BYgram. |
| 34 | Запароленные чаты: добавить, открыть, снять пароль | Недоступный прототип и access gates | Нет | A:122,406–423,481–490; PRIV:136–149; IC.reset обращается к модели, но доступного settings flow нет. |
| 35 | Включить / отключить просмотры профиля (Lite и Full) | Отдельное согласие, запрос, account setting | Код | A:211–230; IS:156–164; IR:333–349. При offline disable локально запрещает запись и сохраняет pending cleanup. |
| 36 | Записать посещение профиля при взаимном согласии | Hook открытия user profile и signed record | Код | `PeerInfoScreen.swift:7088–7092`; IR:351–354; SERVER:296–315 проверяет согласие обоих. Production-поведение не проверено. |
| 37 | Список посетителей: время, новые, аватары, открытие неизвестного | Имя, время, ● и часть переходов есть | Частично | A:249–295; IS:174–192. Не используются avatar_url, нет fallback-карточки для неизвестного peer без username; клик заканчивается без действия. |
| 38 | Следы/аватар последнего нового посетителя и счётчик в своём профиле | Счётчик только на странице Services | Нет | Android `ProfileActivity.java:4087,9729` + AT; iOS все вызовы profileViewsSummary находятся в IS:92–100,197. В собственном профиле соответствующая кнопка/сводка не подключены. |
| 39 | Автоматические уведомления о новых посещениях, включая фон | Только ручной Check new visits | Нет | AT:78–85,92–186; IS:167–171 — ручной poll; timer IS:197 обновляет summary, не уведомления. BYgram-путь выдачи уведомления iOS не реализован. |
| 40 | Отдельный переключатель Firebase Crashlytics | Аналога в BYgram-настройках нет | Нет | A:818–819,842–845; AC:259. Не делается вывод о наличии/отсутствии любой иной телеметрии iOS. |
| 41 | Отдельный переключатель Firebase Analytics | Аналога в BYgram-настройках нет | Нет | A:820–821,846–849; AC:260. Нужен согласованный iOS-эквивалент либо явное исключение. |
| 42 | Экспорт настроек в JSON | Share sheet и allowlist есть; состав/форма отличаются | Частично | A:827–833,863–869, AC:231–237; I:212–239; IC:89–96, IR:79–85. Нет не перенесённых ключей; custom typing экспортируется словарём по userId вместо Android account/action keys. |
| 43 | Сброс настроек с подтверждением | Локальный reset + remote cleanup | Код | A:834–841, AC:239–254; I:102–111; IC:98–105; IR:92–104,366–374. История и Keychain не очищаются этим кодом; очистка сервера зависит от сети. Отсутствующие iOS-настройки не считаются реализованными за счёт reset. |
| 44 | Ссылка на канал | Совпадает | Код | A:128–129; I:196: `https://t.me/BYgram_official`. |
| 45 | Ссылка на чат | Совпадает | Код | A:130–131; I:197: `https://t.me/BYgram_community`. |
| 46 | Ссылка на переводы | Совпадает | Код | A:132–133; I:198: `https://github.com/BYxarek/BYgram-translations`. |

## Подтверждённые дефекты и недостающие части

### 1. Замена устройства при достижении лимита не работает через UI

**Приоритет: высокий, блокирует активацию третьего устройства.**

Локальный backend `deviceList()` возвращает `id`, `device_name`, `created_at`, `last_seen_at`. Android считывает `id`. iOS делает `guard let id = device["device_id"] as? String else { continue }`. В результате устройства не попадают в экран, хотя массив `devices` получен. Метод `replaceDevice` и подпись запроса уже есть; потеря происходит на преобразовании ответа в строки UI. Дополнительно `state=device_limit` с `ok=true` сейчас попадает в общий текст «Saved», а не в отдельное объяснение лимита.

Воспроизводимый сценарий по коду: ответ `{"ok":true,"state":"device_limit","devices":[{"id":"existing-device","device_name":"Android"}]}` → ноль строк Replace device. Сверка проведена с локальным сервером; фактическая версия production не запрашивалась.

### 2. Ввод статусов печати и поддержка действий не равны Android

**Приоритет: средний.**

- Android UI скрывает action 2 (Cancel); iOS показывает все 11 labels, включая Cancel. У Cancel нет соответствия в `typingAction`, так что такой редактор не даёт результата.
- Android сопоставляет record video с кодом 6 и upload audio с 9. В iOS mapper этих кодов нет. Это ограничение текущей модели/интеграции, а не отсутствие полей редактора.
- iOS `String.count` и `.prefix(64)` считают составной символ одной единицей. Backend `mb_strlen` ограничивает число Unicode code points, Android `String.length`/InputFilter — UTF-16 units. Например, 64 повторения `a` + U+0301 имеют 64 видимых составных символа, но 128 code points: iOS допустит значение, сервер отклонит `typing_put` до проверки подписи. В существующем iOS-тесте даже закреплено сохранение 64 сложных emoji, без сверки с серверным лимитом.

### 3. Приватные чаты пока не являются перенесённой пользовательской функцией

**Приоритет: высокий по полноте продукта.**

В Android есть два входа, списки, picker, удаление, открытие, PIN и пароли. В iOS реализованы хранение, проверка секретов, разовые разрешения и ряд filters/gates, но Settings не вызывает `bygramPrivacyController`. Комментарий прямо говорит о незакрытых gallery/overlay/snapshot обходах. Отдельная PIN-вкладка поиска не реализована. Включать существующий прототип только ради наличия пункта меню нельзя считать завершением переноса; требуется завершённый и проверенный маршрут.

### 4. Просмотры профиля дают только часть Android-сценария

**Приоритет: средний.**

Есть opt-in, запись, list, summary и ручной poll. Но:

- список текстовый, без аватаров посетителей;
- неизвестный пользователь без username не открывает fallback-карточку;
- нет кнопки/индикатора новых посетителей в своём профиле;
- нет автоматических локальных/фоновых уведомлений;
- ручной `profile_views_poll` отмечает записи как notified на сервере (`SERVER:342–345`), хотя iOS не выдаёт соответствующего уведомления. Это может изменить последующую доставку на другом клиенте; требуется решение о назначении этой кнопки и межклиентская проверка;
- `profileViews()`/`profileViewsSummary()` не синхронизируют локальный enabled с серверным `enabled=false`; изменение с другого клиента обнаруживается не как полноценный state refresh. Это дополнительный сценарий для runtime-проверки, а не доказанный обход consent на сервере.

### 5. Семь Android-ключей оформления/телеметрии отсутствуют в iOS-модели

`bygram_custom_profile_music`, `bygram_avatar_corners`, `bygram_md3_loading`, `bygram_md3_sliders`, `bygram_md3_switches`, `bygram_firebase_crashlytics`, `bygram_firebase_analytics`.

Поиск точных строк в Swift/Objective-C/plist исходниках iOS не нашёл этих ключей; основной и сервисный UI также не предоставляют соответствующие настройки. Material 3 и Firebase могут потребовать решения по платформенной адаптации, но без явно принятого исключения их нельзя обозначить «полностью перенесено».

### 6. Export version 1 не означает равный набор параметров

Файл существует на обеих платформах, однако iOS экспортирует только собственную модель и typing dictionaries. Android использует сохранённые allowlisted keys и отдельные значения custom typing для account/action. Формат typing не одинаков, отсутствующие возможности не экспортируются. Импорт не обнаружен в этих экранах ни на Android, ни на iOS: его отсутствие не является регрессией переноса.

## Дополнительные возможности рядом с настройками

Эти пункты не добавлены в число строк основной матрицы: в проверенном Android settings UI у них нет отдельного переключателя.

| Возможность | Результат проверки iOS |
|---|---|
| Значки BYgram | API/cache есть в IR:377–424. `EnginePeer.bygramBadge` применяется в ChatListItem, ChatTitleComponent и строке профиля; используются ✦/текст. Полного аналога Android artwork, bulletin при нажатии и покрытия всех user lists не подтверждено. |
| Сведения о сообщении | MENU:1283–1295 добавляет ID сообщения/чата, время, edited и признак deleted. Это реальный пункт, а не только строка локализации. |
| Copy photo, в том числе фото с подписью | MENU:1540–1569 добавляет отдельное действие, читает уже доступный ресурс и пишет изображение в UIPasteboard. |
| Сохранение доступных защищённых cloud фото/видео | MENU:1540–1594 вызывает saveToCameraRoll, разрешение определяется `BygramMedia.swift:4–17`; исключены secret/TTL/autoclear/ephemeral/неоплаченные previews. Проверен этот context-menu path; это не подтверждает аналогичное поведение всех gallery/file/music routes. |
| Повторная отправка / автоматическая повторная загрузка защищённого медиа | В проверенном BYgram context-menu блоке самостоятельного действия Repeat и пути повторной загрузки нет. Не засчитывается как полный перенос действий Android. |

## Хранение, области действия и проверенные ограничения

- Ghost/Spy/filter/profile ID глобальные в обеих версиях. Android использует global main preferences, iOS — app-group UserDefaults; действующие defaults Ghost/Spy совпадают.
- Activity/viewers/typing/visits в iOS разделены по Telegram userId; Android часть этих ключей индексирует локальным account slot. Это допустимая платформенная реализация, но не одинаковый сериализованный export.
- iOS лицензия использует Keychain и подпись DER/X.509, совместимые по структуре с локальным backend. Secrets не входят в `exportData`; выводов о runtime Keychain/entitlements эта статическая проверка не даёт.
- Сброс и очистка Spy — разные действия. Сброс удаляет настройки приватных чатов, activity/typing/visits и локальные toggle values; clearSpy удаляет только наблюдения. Историю/токены reset не должен случайно выдавать в export.
- Наличие privacy-фильтров в NotificationService/Siri/Widget/Share не проверялось как полноценный аудит обходов: функция недоступна из settings, и безопасное включение требует отдельной проверки всех маршрутов.
- Документация Android `configuration.md` содержит устаревшие сведения о Liquid Glass mask; текущий `isActiveSetting` и UI не включают её. Поэтому старый mask не добавлен как отсутствующая iOS-настройка. Равным образом число typing labels не принято за число реально работающих сценариев.

## Что проверено и что ещё обязательно проверить

Выполнено: чтение актуальных UI и моделей, сопоставление всех вложенных BYgram-экранов Android, проверка вызовов consumers, локального API-контракта, экспортов/reset, чтение существующих тестов и проверка `Tests/**/*.swift` в `TelegramCore/BUILD`. Небольшие исходниковые assert-проверки прошли и подтвердили указанные выше несовпадения. Они проверяют факты в текущем коде, а не компиляцию или работу приложения.

Не выполнялись: Swift typecheck, Bazel/Xcode build, XCTest, Android build, UI/VoiceOver, iPhone/симулятор, реальные лицензии и запросы production API, FTP/deploy. Код приложения не исправлялся в рамках отчёта.

После устранения пробелов необходимы:

1. Полные iOS build Lite и Full и `//submodules/TelegramCore:TelegramCoreTests` через Make.py по CLAUDE.md на macOS; отдельно проверить реально встроенный BYgramFull в plist.
2. Ghost: private/group/channel/topic/monoforum/secret read, contents/voice, stories/pinned stories; send/reaction/callback, offline/retry, смена master и restart. Убедиться, что локальный unread меняется, а запрещённые RPC не уходят.
3. Spy: remote delete/edit/push/channel range/secret flush, ручное удаление сохранённого сообщения, TTL/autoclear, media/rich edits, caps/retention, clear/reset и несколько аккаунтов.
4. Full: fresh activation → bot → license, третье устройство и replacement, refresh/revoke/offline/restart, безопасное хранение. Отдельно прогнать реальный ответ devices с полем id.
5. Activity: каждый экран, все комбинации ACL 1/2/4, add/remove/поиск, запрет публикации без consent/зрителей, revoke при offline, background/foreground и смена аккаунтов.
6. Typing: все Android action codes, длинные Unicode/composed emoji, публикация/сброс через бота и отображение iOS↔Android только при реальном Telegram typing event.
7. Visits: Lite/Full, согласие обоих, disable/reset/offline, 7 дней, unknown peer без username, аватары/новизна/summary, возвращение из списка и уведомления между клиентами.
8. Private chats после завершения реализации: Settings/PIN search/direct link/notification/share/Siri/widget/gallery/overlay/app switcher; отсутствие утечек до авторизации и relock после ухода в фон.
9. Оформление, telemetry, export/reset: определить iOS-эквиваленты Android-настроек, подтвердить исключения явно, затем проверить restart, локализацию и VoiceOver.

## Условие завершения переноса

Для утверждения «полный перенос завершён» должны исчезнуть статусы «Нет», «Частично», «Дефект» либо появиться явно согласованные платформенные исключения. Дополнительно требуется успешная сборка обеих редакций и проверка пользовательских сценариев на iOS. На дату этого отчёта эти условия не выполнены.

## Продолжение после запроса на реализацию — 16.10.2026

Исправлены контракт devices[].id/device_limit, typing actions 6/9 и Unicode-лимит; добавлены доступные приватные чаты/PIN search/privacy shield, оформление/аватары/музыка/иконки, MetricKit, посетители с аватарами и индикатором, системные уведомления/BGAppRefreshTask, export/reset новых значений. Material 3 заменён нативными компонентами iOS; Firebase-тумблеры — локальными opt-in MetricKit diagnostics/performance. Подробная привязка к строкам основной матрицы находится в отчёте о реализации. Исходная матрица выше сохранена как результат проверки на тот момент.

Дополнительная сверка Android уточнила пункт Repeat/Save/Details/Copy photo: ветки меню зависят от `bygram_menu_*`, отсутствующих в текущем `BygramConfig.isActiveSetting`; это недоступные настройки, а не подтверждённый обязательный пробел активного Settings UI. Существующие iOS menu actions сохранены. Badges/artwork/all-user-lists остаются отдельным сопутствующим различием вне 46 пунктов настроек.

Локально прошли генератор переводов (120 ключей / 3 языка), 3 Python tests, разбор Swift с сопоставлением двух существующих диагностик с HEAD, проверки plist/Bazel/icon sizes и diff whitespace. XCTest подготовлены, но не запускались. Пользователь подтвердил отсутствие Mac; команды и сценарии проверки на устройстве приложены в новом отчёте. Production/FTP не менялись.
