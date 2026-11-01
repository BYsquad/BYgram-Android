# Плагины BYgram: проверки и выпуск

> Последний проход: [hook bygram.3 и аудит незакрытой приёмки](hook-update-and-audit-2026-11-01.md).

> **Ниже сохранён исходный план и исторические результаты.** Предыдущий протокол: [приёмка 01.11.2026](reliability-and-moderation-2026-11-01.md); актуальное продолжение — [hook и аудит](hook-update-and-audit-2026-11-01.md). Прежние блокировки выпуска и gate относятся к указанным кандидатам; не использовать их как текущую матрицу.

> Текущий проход завершения P0–P8: [результаты, матрица и ограничения](completion-2026-10-31.md). SDK gate теперь x86_64 API 28/35/36/37 и arm64 API 36; ниже сохранены исторические результаты r2–r4. Они не описывают текущие исходники.

> Итог этапа 31.10.2026: исправленный `Aliuhook 1.1.4-api37.1-bygram.1`
> закреплён; Full Debug r4 проходит hooks fixture API 28 и core fixture
> API 35/36/37 с незагруженным backend. Gate: только 64-bit primary x86_64
> API 28. Recovery API 36 — PASS; public v1 и аппаратная матрица не приняты.
> r3 остался диагностическим failed кандидатом; [подробный отчёт](development-report.md).

> Обновление 31.10.2026: реализация уже находится в рабочем дереве. Текущий контракт — [SDK прототипа](sdk-v1.md), фактические PASS и оставшиеся проверки — [отчёт](development-report.md). Исторические формулировки «будущий/ещё нет» ниже описывают исходный план, а не текущий статус. Полная приёмка v1 ими не подтверждается.


Исходный план: 30.10.2026. Результаты приёмки 31.10: native fixture API 28
и recovery API 36 — PASS; hooks API 36/37 — FAIL, native probe API 35 — FAIL.
Выпуск заблокирован; точные версии, hash и source gate — в [отчёте](development-report.md).

Начаты проверки реализации формата: JVM harness `Tools/PluginManifestCheck.java`
и Gradle `Tools/check_plugin_build.init.gradle`. Это проверки метаданных и
конфигурации, не Python/hooks/ZIP installer; результаты — [отчёт разработки](development-report.md).

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

Выбран backend BYxarek/hook `1.1.4-api37.1`, commit `28137a6287e78ec55aaaa439c3c8e4cae55c0cf0`. [Внешний отчёт о тестах библиотеки](https://github.com/Aliucord/hook/pull/21) учтён в разделе 3.2.1 плана; это не результаты проверки готового BYgram APK. API 36/37 входят в целевую поддержку, ограничение старого upstream до API 35 к выбранному форку не применяется. Android 7+ для Full уже принят владельцем; проверяется реализация этого решения, повторное согласие не требуется.

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
| Original exactly once | Replacement вызвал оригинал и затем бросил: повторного вызова нет |
| Hook cleanup | После 100 enable/disable нет повторных вызовов и не растёт число handles |
| Safe mode | Recovery flag читается до Python/backend; import crash; native crash probe; startup marker ambiguity |
| Updates | Pending version; новые возможности; неподписанный файл того же ID; same version/different hash; migration crash; downgrade |
| Lite | Нет Python/AliuHook в dependencies/APK, нет startup side effects и работающего plugin deep link |

Crash/ANR probes исполнять на выделенной тестовой установке без личных аккаунтов. Не проверять «спасение» клиента разрушением пользовательских auth/storage данных. Для сетевых сценариев использовать fake controller/тестовый account и явно маркированные сообщения; production backend не нужен.

Для recovery отдельный тестовый плагин зависает до первого кадра основного процесса. Recovery shortcut должен открываться независимо, устанавливать safe-mode flag; после холодного restart основной клиент не загружает плагин. Проверить процессный gate и отсутствие runtime в recovery process.

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

Ход реализации с 30.10, ранние failed кандидаты и текущий r4 описаны
в [отчёте](development-report.md); полной приёмки P0–P8 нет.

Проверка готового Full артефакта: `python Tools/check_plugin_apk.py <apk> --report output/plugin-apk-report.json`. Она не заменяет подпись/zipalign, Android runtime и ARM hardware checks.

31.10.2026: причина native-падения локализована в Dobby 1.2 (MAP_FIXED
заменял ART shadow map). Отдельная исправленная библиотека прошла native
probe на API 28/35/36/37 x86_64, 4/16 KiB. Патч, контрольное воспроизведение
и границы проверки — в [native-hook-investigation.md](native-hook-investigation.md).
Это не переаттестация APK r2: pinned AAR и его gate не менялись. Перед
расширением gate нужны закреплённая исправленная зависимость и runtime-проверка
в полном приложении; ARM остаётся непроверенным.


31.10.2026, r4: закреплён bygram.1 AAR с исправленным Dobby; итоговый APK
прошёл подпись, ZIP/zipalign, 148 ELF64/16 KiB, локализации и Android fixture
API 28 (hooks) / 35,36,37 (core без backend). Maps и выживание PID проверены.
Recovery API 36 при реальном Python UI hang — PASS; первый прогон с
«System UI isn't responding» сохранён отдельно и не засчитан.
Raw hooks вне 64-bit primary x86_64 API 28 закрыты; ARM/OEM/API 24,
Release runtime, реальные аккаунты, accessibility и benchmark не приняты.
Полная исходная матрица P8 не выполнена. Metadata, SHA-256 и доставка — в отчёте.
Для повторного core/hooks fixture: `python Tools/check_plugin_device.py SERIAL APK --report output/result.json`;
добавлять `--expect-hooks` только для ожидаемого raw backend (сейчас API 28 x86_64).
Тест устанавливает Debug APK с заменой и force-stop приложения: использовать
только выделенный AVD без личных аккаунтов. Он не делает uninstall и не удаляет auth.
