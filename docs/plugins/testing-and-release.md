# Плагины BYgram: проверки и выпуск

Фактический контракт SDK и условия запуска native hooks см. в [SDK v1](sdk-v1.md). Матрица ниже задаёт сценарии проверки для нового APK.

## 1. Обязательная матрица совместимости

| Среда | Минимальная проверка |
|---|---|
| API 23, Lite | Сборка/запуск без Python и AliuHook; отсутствие plugin entry/runtime |
| API 23, Full | Зафиксированное продуктовое решение: Full minSdk 24 не устанавливается; не обещать поддержку |
| API 24, arm32/arm64/x86/x86_64 при доступности среды | Запуск Python и корректность ABI packaging; не утверждать hardware coverage по одной эмуляции |
| API 28, x86_64 | Холодный старт Python, перезапуск и установленный пакет |
| API 30/33/34 | Python, host UI, accounts, hooks только для проверенного backend |
| API 35 | Hooks после холодного/прогретого старта и R8 |
| API 36 | Целевая интеграция BYxarek/hook: Python bridge, hooks, R8 и recovery; отдельный прогон BYgram |
| API 37, x86_64 4/16 KiB и arm64 | Целевая интеграция BYxarek/hook с Chaquopy/BYgram; повторить цепочку hooks и recovery |
| 16 KiB arm64/x86_64 | ELF headers/align + установка + runtime load всех новых native libs |
| Физический телефон arm64 | Реальный ART/OEM, scrolling, background/foreground, account switch |
| Full Debug и Full Release/R8 | Одинаковые lifecycle и hooks; не только compilation |
| Прочие app-модули | Gradle configuration/compile и согласованное minSdk/native packaging |

Матрицу вести с OS build, ABI, page size, edition, APK type, dependency fingerprints и итогом. Отсутствующая среда отмечается NOT TESTED; backend на ней не объявляется поддерживаемым. Emulator не заменяет реальный телефон для native hooking.


## 2. Содержательные автоматические проверки

Использовать существующие `Tools/` для чистых JVM/Python self-checks и `TMessagesProj_AppTests` для Android. Не ограничиваться поиском строк в исходнике: проверять поведение формата, lifecycle и callback chain. Пока создана документация, новых тестов движка не добавляется.

| Группа | Что проверять |
|---|---|
| Manifest | Все обязательные поля; unknown поля/версии; пустые строки; ID/entrypoint; числовые версии/диапазоны; capability names |
| ZIP | `../`, absolute/drive paths, backslash, symlink, duplicates, case collisions, encrypted entries, fake size, zip bomb, слишком много файлов |
| Установка | Cancel, плохой URI, нет места, нет разрешения, дубль, два одновременных install одного ID |
| Atomic registry | Process death до/после rename и каждого registry write; повреждённый JSON; backup recovery |
| Lifecycle | Load/enable/disable/unload по порядку; ошибка каждого callback; cleanup зарегистрированного до ошибки handle |
| Generation | Доставленный после disable результат не меняет UI/data; disable→enable не оживляет старую работу |
| Settings | Типы, defaults, лимиты, scope, migration success/fail, snapshot/rollback, импорт без enable |
| Account | Callback содержит нужный account; switch mid-task; logout; новый user в старом slot; новый account не auto-enabled |
| UI contributions | Уникальность `(plugin_id, local_id)`, двойное enable, restore после disable, уничтоженный fragment |
| Hooks | Static/instance/overload/primitive/null; изменение args/return; Java original throwable; invalid types |
| Цепочка хуков | Два before/after; early return; exception одного; replacement conflict; reentrant call; unhook одного owner |
| Операции Telegram | Приоритет и фильтры, отмена, замена и завершение цепочки. Отзыв аккаунта до отправки TL-запроса или ответа. В Full проверить контекстное меню сообщения и штатный процесс отправки на тестовом аккаунте |
| Внешний индекс | Проверка HTTPS URL, SHA-256 и manifest. Пакет внешнего индекса запрашивает подтверждение неизвестного источника даже при наличии полей модерации в индексе |
| Original exactly once | Replacement вызвал оригинал и затем бросил: повторного вызова нет |
| Hook cleanup | После 100 enable/disable нет повторных вызовов и не растёт число handles |
| Safe mode | Recovery flag читается до Python/backend; import crash; native crash probe; startup marker ambiguity |
| Updates | Pending version; новые возможности; неподписанный файл того же ID; same version/different hash; migration crash; downgrade |
| Lite | Нет Python/AliuHook в dependencies/APK, нет startup side effects и работающего plugin deep link |

Crash/ANR probes исполнять на выделенной тестовой установке без личных аккаунтов. Не проверять «спасение» клиента разрушением пользовательских auth/storage данных. Для сетевых сценариев использовать fake controller/тестовый account и явно маркированные сообщения; production backend не нужен.

Для восстановления прервать тестовый плагин во время активации и снова открыть BYgram. Основной клиент должен открыться без плагинов, а уведомление должно вести в отдельный процесс восстановления. Повторить с зависшим lifecycle, повреждённым реестром и выключенными уведомлениями. В последнем случае проверить состояние через настройки плагинов. После выхода из безопасного режима плагины должны оставаться выключенными.

Проверить обновление BYgram между постановкой несовместимого пакета в очередь и холодным стартом. Убедиться, что старая версия и настройки сохранены. При зависшем провайдере записи проверить, что параллельное отключение плагина выполняется, а экспорт завершается по таймауту. Для каталога проверить отзыв точного SHA-256, удаление публичного пакета, блокировку повторной публикации и отключение установленной копии после обновления списка.

### Проверка 12.11.2026

Full Debug 13.2.1 на чистом Android API 37 x86_64. Обычный запуск исполнил разрешённый тестовый плагин. После имитации прерванной активации BYgram открылся без плагина, уведомление открыло `:plugin_recovery`. Повреждение основного реестра с рабочей резервной копией также включило безопасный режим и показало уведомление. В APK проверены четыре ABI и выравнивание ELF64 для 16 КиБ.

## 3. Ручная приёмка пользовательских сценариев

1. Full → настройки BYgram → отдельная строка «Плагины» → back; заголовок/список не перекрываются.
2. Первое открытие без сети: страница доступна, движок off, установка из локального файла возможна.
3. Выбор плохого пакета: точная причина, исходные пакеты/settings не изменились.
4. Установить hello: до отдельного enable ни код, ни migration не запускаются.
5. Enable, настройки, использование функции, disable; список/меню отражают реальное состояние.
6. Install/update требует согласия на точный новый файл; deep link и открытие вложения не включают код.
7. Повторить для двух аккаунтов, сменить account во время отложенной работы, выйти и войти другим user.
8. Два плагина меняют одну поверхность: приоритет/конфликт виден, отключение возвращает пользовательское значение.
9. Плагин бросает исключение: локальный журнал и quarantine; остальные host функции доступны.
10. Плагин падает/зависает при startup: recovery без Python, холодный restart, клиент открывается безопасно.
11. Update/migration fail: старая версия и settings доступны, новая не auto-enabled; rollback не скрывает причину.
12. Delete с сохранением/удалением settings; папки других плагинов и Telegram целы.
13. TalkBack, RTL, большой шрифт, dark/light, landscape/tablet, keyboard/insets, reduced animations.
14. Закрыть/открыть процесс, перезагрузить устройство, force-stop: сохранить желаемое состояние без ложного самовключения.
15. Android backup/restore: код и разрешение исполнения не восстанавливаются автоматически.

## 4. Производительность и утечки

Сравнить один и тот же снимок BYgram до/после движка, одинаковое устройство и тип сборки. Не сравнивать Debug старой базы с Release новой. Измерить отдельно: plugins off; runtime warm без plugins; один UI plugin; несколько plugins; raw-hook plugin.

Записать cold-start p50/p95 (например, 10 запусков на конфигурацию), время первого открытия страницы, длительность Python init/import/enable, PSS/RSS, количество threads/handles, dropped/coalesced events, scrolling jank и размер APK/installed size по ABI. Начальный budget при выключенном движке: не инициализировать Python/AliuHook и не делать I/O пакетов на main thread; дополнительные расходы фиксировать измерением, а не обещанием «0 ms».

После 100 переключений plugin и открытия/закрытия страниц проверить, что не удерживаются Activity, fragment/View и прежние account contexts. Межъязыковые циклы разрывать cleanup/weak references. Длительный CPU Python task не должен объявляться безопасным только из-за background thread: проверить влияние GIL на UI callbacks.

При выявленном ухудшении уменьшать число переходов Java↔Python и переносить готовые decoration/config в Java; не лечить проблему глобальным отключением оптимизаций ART.

## 5. Проверки перед первым пользовательским выпуском

- Реализовано принятое решение: Full minSdk 24, Lite 23; документирована потеря Android 6 для новой Full-сборки.
- Утверждены fingerprints зависимостей, лицензии/notices, фактические `.so` и доступность backend по устройствам.
- Recovery работает без плагинов, без входа в аккаунт и при зависшем main runtime.
- Нет silent enable, runtime pip и release dev server; пользователю честно объяснено доверие Python-коду.
- Не нарушены PIN/скрытые чаты, account lifecycle, лицензирование и серверные проверки.
- Release/R8 проверен; нет широкого отключения shrink/optimizations как обхода.
- Системные интерфейсы Telegram, которые получили extension points, перечислены в документации с датой и regression результатами.
- API reference, type hints, manifest schema и примеры соответствуют коду конкретного SDK; removed/experimental API помечены.
- `docs/readme.md`, `docs/bygram-settings/README.md`, `docs/plugins`, `docs/build.md` и Android `changelog.md` актуализированы по этапам.

## 6. Порядок сборки и доставки

С учётом инструкции проекта: **один итоговый пользовательский APK после всех задач**, затем отправка через навык `telegram-cli` в Telegram «Избранное» с проверкой результата доставки. Не собирать промежуточные distributable APK по каждому этапу.

Во время разработки использовать unit/JVM tests, Gradle configuration/compile, library AAR и existing test harness. Instrumentation/device проверки могут потребовать тестовые APK: заранее согласовать смысл требования «один APK», не создавать и не рассылать несколько пользовательских сборок под видом финальной. Не объявлять hooks проверенными на устройстве, если из-за ограничения только компилировали код.

До единственной финальной упаковки проверить release-совместимость максимально доступными compile/R8/harness средствами. После упаковки проверить подпись прежним ключом, package, edition, versionName/code, SDK, ABI, ZIP, zipalign/ELF 16 KiB, наличие переводов, отсутствие лишних runtime в Lite и smoke запуск. Версию клиента без запроса не менять.

Если итоговый APK не прошёл проверку, его не отправлять как готовый; исправление и повторная сборка требуют честно зафиксировать необходимость дополнительного APK, а не обходить проверку. Нельзя одновременно гарантировать отсутствие второй упаковки при любых ошибках и полноценную проверку ещё не созданного артефакта.

В `docs/build.md` записать команду, редакцию, окружение, hash и размер, пройденную матрицу, ограничения и подтверждение Telegram delivery без credentials. После проверки/доставки остановить лишние build daemons согласно обычному процессу проекта.

Проверка готового Full артефакта: `python Tools/check_plugin_apk.py <apk> --report output/plugin-apk-report.json`. Она не заменяет подпись/zipalign, Android runtime и ARM hardware checks.
