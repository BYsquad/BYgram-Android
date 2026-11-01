# Android: обновление hook и аудит оставшихся работ — 01.11.2026

Объём: Android Full plugin manager, installer/store/signatures, Python SDK,
HookBridge/native dependency, recovery, host UI, PHP moderation/signatures и исходный план P0–P8.
Ожидаемая нагрузка — несколько доверенных плагинов в одном процессе клиента,
с конкурентными callbacks, отключением, сменой аккаунта и перезапуском.
Это аудит текущего рабочего дерева; прежние незакоммиченные изменения сохранены.
Не является утверждением об отсутствии любых других дефектов.

## Обновление зависимости

Форк [BYxarek/hook, 43b0d8d](https://github.com/BYxarek/hook/commit/43b0d8d01b580b8d4caa4aae7ed528ed80a35078)
обновляет LSPlant до `06bd3412d639d6b0d76fe6c7388066fe44e8963d` и применяет
кэширование backup JNI IDs через собственный `lsplant_backup_id.cmake`.
Закреплённый backend BYgram: `1.1.4-api37.1-bygram.3`. Версия клиента не меняется.

Локальный старый JNI-ID patch больше не применяется. Пока необходимы:

- Dobby: запрет MAP_FIXED поверх существующих ART mappings.
- XposedBridge: публикация callback под тем же lock, volatile backup, учёт
  активных вызовов перед native unhook.
- LSPlant: восстановление исходных access flags; новый fork сам этого не делает.
  Его CMake-патч адаптирован к дополнительному аргументу DoHook.

Сборка остаётся воспроизводимой из committed bytes и создаёт отдельный AAR;
прежние опубликованные локальные версии не перезаписываются. Четыре ABI,
Java public API и неизменность остальных AAR entries проверяет builder.
Provenance включает SHA-256 всех трёх применяемых patch-файлов.
Старое удержание пустых native dispatchers до restart удалено. Последний владелец
закрывает dispatcher после завершения активных вызовов, включая close из callback.
Это проверено отдельно на API 35/36/37 x86_64, а не предполагается по changelog форка.

## Найденные ошибки и недоработки

### 1. P1 — экспорт настроек может остановить управление всеми плагинами

`PluginManager.exportSettings()` открывает SAF OutputStream, пишет и закрывает
его внутри единственной management queue. Если облачный/сломанный DocumentsProvider
не отвечает, disable/remove/safe-mode обработка основного процесса ждут без срока.
Чтение URI уже вынесено в PluginFiles, запись — нет. Минимальное исправление:
снять согласованный snapshot в management queue, затем писать в отдельном bounded
I/O worker с deadline и отменой; ошибки/частичный файл явно сообщать владельцу.

### 2. P1 — resetRegistry обходит защиту живого зависшего поколения

`PluginManager.resetRegistry()` вызывает stopAll(), затем без проверки
restartRequired сбрасывает реестр и повторно запускает init()/collectPackages().
detach() при timeout оставляет авторский поток живым и возвращает null.
Такой вызов resetRegistry удаляет ссылки на его пакет и допускает сборку пакета
как мусора, хотя поток продолжает работать. Путь доступен через public manager API;
обычный UI показывает reset только при общей ошибке. Минимальное исправление:
при любом не завершившемся поколении отложить reset до холодного старта,
сохранив safe mode и durable restart-required.

### 3. P1 — новые серверные проверки подписей не развёрнуты на production

Read-only FTP подтвердил расхождение `/www/byxarek.site/TEST-PROJECT/BYgram/api/`
с рабочим деревом. `plugin-catalog.php`: 21858 bytes, SHA-256
`e7ef814ff2553422624c19def1c04bee62afed175ac6bc408c4a085eda166b4a`,
не содержит новой функции pluginAuthorSignature. Локальный файл имеет 26598 bytes
и проверяет подпись перед публикацией. Поэтому требование «новые публикации
только с подписью» пока реализовано в исходниках, но не в production moderation.
`api/index.php` также отличается; наличие маршрута plugin_submit подтверждено.
`api/plugins.php` и `plugins/index.json` побайтно совпадают с локальными.
Шесть старых неподписанных примеров — предусмотренная совместимость, не дефект.

Нужна отдельная согласованная поставка текущих серверных изменений и проверка
модерации/совместимости после неё. В этом задании FTP использован только для
чтения; ни один файл или таблица production не изменены. Машинный протокол без
credentials: `output/plugins-hook-production-readonly.json`.

### 4. P2 — локальные обновления не различают downgrade и замену той же версии

`PluginStore.install()` отклоняет идентичный hash и смену закреплённого ключа,
но любой другой пакет с тем же ID помещает в pending независимо от version.
Например, после 2.0.0 файл 1.0.0 или другой hash 2.0.0 становится обычным обновлением.
Каталог запрещает downgrade на сервере; локальная установка такой политики не имеет.
Нужно отдельно показывать downgrade/rebuild той же версии и требовать осознанного
решения, сохранив обычное подтверждение точного hash и явный rollback.

### 5. P2 — неподписанный установленный код не сверяется с одобренными bytes

`PluginManager.enable()` сравнивает approved с hash в registry и проверяет
PluginSignature, но у неподписанного пакета verify() возвращает null.
Изменённый после установки main.py из каталога packages/<hash> будет исполнен
при прежнем approved hash. Исходный hash относится к ZIP, который после staging
не хранится как executable package. Это пробел проверки целостности, не sandbox
и не защита от уже исполняемого доверенного Java-кода. Минимальное исправление:
сохранить проверяемый inventory содержимого при установке и сверять перед import,
либо хранить и повторно проверять исходный архив перед materialization.

### 6. P2 — независимое восстановление реализует только safe mode

`PluginRecovery.onCreate()` показывает последний stage и одну кнопку safe mode.
Из плана §5.3/§13 отсутствуют независимые от Python/main-process действия
«отключить все», сброс данных выбранного плагина и rollback. Они есть в обычном
менеджере, который при зависшем основном процессе недоступен. Безопасное
дополнение — отдельные durable recovery requests, исполняемые на холодном старте;
recovery-процесс не должен одновременно писать registry.

### 7. P2 — startup recovery не следует запланированной проверке здорового старта

`PluginManager.init()` включает safe mode после одного найденного startup marker,
а удаляет его через отложенные 10 секунд UI callback. Нет счётчика двух неудачных
стартов и подтверждения, что главный экран действительно устойчиво открылся.
Force-stop/перезагрузка в эти 10 секунд дают ложный recovery, а работающий Looper
сам по себе не подтверждает успешную навигацию. Нужно связать healthy marker
с готовностью главного экрана и явно принять политику количества попыток.

### 8. P2 — concurrent moderation не защищает переход статуса заявки

`approvePlugin()` держит файловый publish lock, но не блокирует строку заявки
в MySQL. `preparePluginReview()` и reject в `plugins.php` этот lock не используют,
а финальный UPDATE approve фильтрует только id. Если второй администратор
отклоняет/обновляет review после SELECT первого, первый всё равно публикует
свой кандидат и перезаписывает статус approved. При prepare поле candidate в БД
может уже описывать другой пакет, чем опубликованный index. Это статически
прослеженная гонка; конкурентный MySQL-прогон в этом задании не выполнялся.
Нужны общий порядок блокировок и row lock/сверка статуса и hash в транзакции;
prepare/reject/approve должны следовать одному контракту переходов.

### 9. P3 — часть обещанного представления менеджера отсутствует

`BygramPluginsActivity.fill()` использует один settings_account drawable для
каждого плагина. Manifest icon проверяется validateIcon(), но не отрисовывается.
Список не показывает отдельно автора/описание/версию, они доступны в карточке.
Нужно довести список до §5.2 плана с безопасным bounded image decode.
Поиск/фильтры были условным требованием «когда список оправдывает» и обязательным
дефектом при текущих шести примерах не считаются.

### 10. P3 — журнал можно скопировать, но нельзя экспортировать файлом

В обработчике item.id == 12 есть Copy/Clear/Cancel. Отдельного SAF-export журнала,
предусмотренного §5.3, нет. Экспорт настроек не экспортирует журнал.
Нужен экспорт по действию владельца с тем же предупреждением о чувствительных
данных, через независимый I/O worker из пункта 1.

### 11. P3 — SDK network.get_json не имеет общего deadline и отмены по scope

`bygram._Network.get_json()` ограничивает размер 1 MiB и socket timeout 10 s,
но response.read() может продолжаться при медленной непрерывной выдаче bytes.
Закрытие scope не закрывает urllib response; check() выполняется после чтения.
Work workers ограничены по числу, поэтому такой запрос удерживает worker после
disable и может потребовать restart. Нужны общий deadline и закрытие активного
response при закрытии handle/scope; socket timeout не равен общему deadline.

## Карта функций из плана, которых ещё нет в стабильном SDK

Raw Java reflection/hooks не считаются реализацией стабильного host API.
Это направления §6 плана; не все были условием минимального P4/v1.

| Область | Уже есть | Остаётся только в плане |
|---|---|---|
| Тема | Именованные цветовые overrides, обновление paints/ThemeDescription | Scoped drawables, радиусы, типографика |
| ActionBar/навигация | Три menu locations, события страниц | Заголовки и прочие именованные navigation contributions |
| Чаты/папки | dialogs.toolbar | Декорации строк, подписи/badges, локальный порядок |
| Чат | chat.actions, наблюдение IDs новых сообщений | Message context menu, панели, display transformations |
| Текст сообщений | Нет formatter API | Spans/entities, семантика copy/reply/accessibility |
| Профиль/настройки | profile.actions, собственные schema pages | Встраиваемые именованные секции/строки системных страниц |
| Медиа/сторис | Нет отдельного host API | Меню, overlays, controls и собственный lifecycle |
| Композер | draft_text, preview_text с подтверждением | Кнопки/команды и реестр преобразований |
| Уведомления | Нет host API | Локальное представление и действия |
| Telegram transport | network.completed только для собственного HTTPS helper | Наблюдение/трансформации TL requests/responses/updates с account context |
| Звонки/камера/платежи | Нет host API | Только запланированные UI extensions |
| Java hooks | Before/after/replace, private/constructor/overload, ordering | Аппаратная аттестация всех заявленных кандидатов; совместимость произвольных внутренних методов не гарантируется |

## Что уже реализовано и не должно оставаться в списке «не сделано»

Установка выключенным, namespaces/relative imports, scoped handles, global/account
settings, migrations, pending/cancel/rollback, registry recovery/process-death
checks, журнал, native schema pages, цвета/меню/page events, отправка текста,
наблюдение IDs сообщений, composer preview, подпись автора и key pinning,
каталог/заявки/модерация уже имеют код. Старые тексты плана «будущий компонент»
не означают отсутствия реализации. PluginInstaller/PluginRegistry — роли внутри
PluginPackage/PluginStore, а не недостающие классы.

Отсутствие sandbox — принятое решение: Python/Java/native выполняются с правами
клиента. Принудительно остановить произвольный доверенный код безопасно нельзя.
Runtime pip, paid plugins, cloud sync, exteraGram compatibility, произвольные Dex
и новые Android permissions исключены из v1 явно, это не забытые заглушки.

## Не закрытая приёмка P0/P8

- Полная OS/ABI/OEM матрица API 24–37, физический ARM64 и ARM64/16 KiB.
  Три доступных AVD дают x86_64 API 35/36/37, но не заменяют телефон.
- Runtime настоящего Release/R8 APK, отрицательные device-probe crash/timeout
  и повторная проверка независимого recovery при зависании UI на текущем APK.
  R8 compilation и положительная source-проба не заменяют эти сценарии.
- Реальные test accounts: смена/выход/повторный вход, сообщения, composer,
  hidden/protected dialogs и фон/возврат. Synthetic fixtures не подтверждают это.
- Отдельная compilation/runtime приёмка Huawei/HockeyApp/Standalone/Tests и
  полного Lite APK на текущем снимке. Проверка редакций по исходникам и общая
  Gradle configuration не заменяют эти прогоны; дополнительные APK не создаются.
- TalkBack, RTL, font scale, day/night, keyboard, rotation/tablet, reduced motion.
  Существующие source accessibility checks не равны ручной проверке.
- Измеренные cold start/первый Python start/RSS/APK delta и горячий hook path;
  зафиксированного сравнения baseline нет.
- Контрактные исполняемые проверки полного соответствия .pyi реальному SDK.
  Stubs уже существуют, их наличие не означает такую проверку.
- Лицензии/обязательства перераспространения native зависимостей: notices
  существуют, отдельная приёмка перед публичным выпуском не подтверждена.
- Production проверен read-only по FTP: серверный модуль отстаёт, см. пункт 3.
  Реальные admin/session операции, deployment и MySQL в этом задании не выполнялись.

## Проверки этого прохода

Проверено:

- `python Tools/check_plugins.py`: PASS до и после изменения native lifecycle;
  финальный лог `output/plugins-hook-update-core-final.log`.
- `python Tools/check_plugin_signatures.py`: PASS, Python/OpenSSL ↔ Java ↔ PHP,
  Unicode ordering и отрицательная проверка изменения содержимого.
- `python Tools/check_bygram_editions.py`: PASS.
- Builder: четыре ABI, public Java API, остальные AAR entries и ELF64/16 KiB — PASS.
  AAR SHA-256 `78827e37214b11b5ec4aa3e2e4afaabd28a9f1503c47c18294f2060b6c9d33f3`.
  Исходники, patch/native hashes: `third_party/maven/com/aliucord/Aliuhook/1.1.4-api37.1-bygram.3/provenance.json`.
- `Tools/check_plugin_unhook.py`: API 35/36/37 x86_64 — PASS; 400 циклов,
  concurrent/self-close, два владельца, exception, original access flags, GC и
  0 dispatchers. API 35/37: 16 KiB; API 36: 4 KiB. После переноса проверено
  равенство production source всем трём кандидатам и повторён API 36 уже
  исправленным runner, который компилирует production без подмены lifecycle.
  Логи `output/plugin-unhook-updated-{35,36,37}/runtime.log`,
  `output/plugin-unhook-production-36/runtime.log`.
- Текущие скомпилированные SDK/host/Service + текущий Python через source overlay
  без APK: API 35/36/37 — PASS. Проверены production device probe с 30 physical
  hook/unhook/GC, SDK lifecycle/storage/settings/migration, generations,
  hook ordering/conflicts/types/reentry, quarantine, backup/pending/rollback.
  Логи `output/plugins-hook-preflight/emulator-*-Plugin{ProbeProcessCheck,IntegrationProbe}.log`.
  app_process имеет Java debuggable=false и не проверяет Android window/theme
  renderer; это не приёмка Debug/Release UI.
- Full Debug Java compilation, Release Java compilation и Release/R8 — PASS.
  `output/plugins-hook-update-compile-r8.log`: BUILD SUCCESSFUL, 399 tasks,
  28m39s. Единственная итоговая упаковка Full Debug — PASS: 17m47s, 317 tasks.
- Готовый APK: `TMessagesProj_App/build/outputs/apk/afat/debug/BYgram-full-afatDebug.apk`,
  164867799 bytes, SHA-256 `fc5650e4700350fc612548b4876668e80a4585f41c99beabbbd0342b387bf145`.
  Full 13.1.1 (72379), com.bygram.mobile, minSdk 24 / targetSdk 36, четыре ABI.
  Прежний сертификат подписи, ZIP CRC, zipalign 16 KiB, 148 ELF64/16 KiB и
  совпадение всех четырёх libaliuhook.so с pinned AAR — PASS.
- Установленный итоговый Debug APK: полный Java/Python/SDK/hooks fixture API
  35/36/37 x86_64 — PASS, native loaded=true, process survived=true. Здесь уже
  проверен реальный Android Debug process, в том числе Service probe. После
  cleanup 0 registrations и 0 native dispatchers. Протоколы:
  `output/plugins-hook-final-device-{35,36,37}.json`. Ручной аудит обычных страниц,
  TalkBack и реальные аккаунты этим fixture не заменяются.
- APK собран из текущего рабочего дерева, включая прежние Android-изменения;
  дополнительный APK не создавался. Gradle daemon этого задания остановлен.
  Доставлен в Telegram «Избранное»: сообщение `201482`, 01.11.2026 22:20:13 Europe/Moscow.
  Повторное чтение точного ID подтвердило out=true, имя APK и 164867799 bytes.
  Протоколы `output/plugins-hook-telegram-send.json`, `output/plugins-hook-telegram-read.json`.
  Тестовые процессы клиента завершены, исходные AVD оставлены запущенными.

Незавершённая/незапущенная проверка не считается PASS.

Системные границы: меняются native ART hook/unhook и Full dependency packaging.
Стандартные страницы чатов/профиля Telegram аудитом не переписаны. Найденные
ошибки, не связанные с BYgram customization, перечислены выше отдельно.
