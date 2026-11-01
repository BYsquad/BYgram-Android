# Отчёт о разработке плагинов Android

> **История проходов.** Актуальная реализация, артефакт и матрица — [надёжность и модерация 01.11.2026](reliability-and-moderation-2026-11-01.md). Старые FAIL/gate и обещания следующей упаковки не описывают нынешний код.

> Текущий проход завершения P0–P8: [результаты, матрица и ограничения](completion-2026-10-31.md). SDK gate теперь x86_64 API 28/35/36/37 и arm64 API 36; ниже сохранены исторические результаты r2–r4. Они не описывают текущие исходники.

> Итог текущего этапа 31.10.2026: закреплён исправленный Dobby в AAR
> `1.1.4-api37.1-bygram.1` для четырёх ABI. Разрешённый Full Debug r4
> проходит файл/signature/148 ELF64/16 KiB/localization, полный hooks fixture
> API 28 и core fixture API 35/36/37 без загрузки AliuHook. Recovery API 36 — PASS.
> Raw hooks разрешены только 64-bit процессу с primary x86_64 на API 28.
> Full compile/R8, JVM gate и Lite dependency graph — PASS. В задаче созданы
> диагностический failed r3 и один явно разрешённый дополнительный r4.
> r4 доставлен в Избранное (201367), имя/размер подтверждены повторным read.
> r3 доставлен отдельно с предупреждением (201365), он не считается готовым.
> Public v1 не принят: ARM/OEM, API 24, реальные аккаунты, Release runtime,
> accessibility, benchmark и recovery API 37 не проверены.

## 31.10.2026 — завершение импорта настроек и финальная приёмка

Подключён существующий `PluginSettingsFile` к импорту/экспорту менеджера.
JSON проверяется потоком до построения дерева: UTF-8, дубликаты ключей,
глубина, число узлов и размер (8 MiB); затем ID, schema и scopes. Отказ
происходит до остановки плагина и записи настроек. Экспорт использует те же
правила, чтобы созданный файл можно было импортировать обратно.
`Tools/PluginSettingsCheck.java`: PASS, round-trip и 14 отказов, включая
20 000 вложенных массивов. Общий `Tools/check_plugins.py`: PASS, 79
отрицательных manifest, 30 ZIP fixtures, SDK examples и network helper.
Лог: `output/plugin-core-final.log`. Системные страницы Telegram этим
исправлением не менялись; исправлена граница чтения файлов менеджера.

Предыдущие сохранённые финальные прогоны: Full compile/Release R8 — PASS
(`output/plugin-final-prepackage.log`, 32m20s); Lite compile — PASS
(`output/plugin-lite-final.log`, 5m32s). После текущего исправления нужен
повтор затронутой compilation/R8. Запущены тестовые AVD API 36 и 37/16 KiB.
Состояние runtime и доставки будет дополнено после фактических проверок.

## 31.10.2026 — продолжение реализации

Внесены: ZIP staging с лимитами/CRC/central directory и запретом symlink,
атомарный registry, установка выключенным, pending update, rollback пакета и
settings snapshot, durable журнал миграции. Добавлены Full-only lazy Chaquopy,
SDK `bygram`, scope/generation, cleanup, очереди, native менеджер и карточка
плагина, выбор аккаунтов по slot/user ID, журнал, safe mode и независимая
recovery Activity. Реализованы меню списка чатов/профиля/чата, декларативные
страницы и настройки, cached theme overrides, экспериментальный hook dispatcher.

Chaquopy 17.2.0 / Python 3.11 подключены в `TMessagesProj` только Full.
Выбранный AAR BYxarek/hook взят из локальной сборки чистого checkout
`28137a6287e78ec55aaaa439c3c8e4cae55c0cf0` и сохранён в ограниченном Maven repo
`third_party/maven`. SHA-256 AAR:
`01fa427a1e6d460069cc32c772d47219677885528697e5dfd91cfc823eda4189`.
NDK Telegram не менялся. Новых pip-зависимостей нет.

Проверки на этом снимке: `Tools/check_plugins.py` — PASS: 78 отрицательных
manifest, 26 ZIP fixtures, generation/cleanup и два независимых Python namespace.
Full `:TMessagesProj:compileDebugJavaWithJavac` — PASS (3m18s), затем разработка
продолжилась; финальный прогон ещё предстоит. API 37 / 16 KiB эмулятор запущен,
runtime-прогон нового BYgram ещё не выполнен. Другие среды NOT TESTED.

Изменённые системные интерфейсы: `ApplicationLoader` (ранний recovery gate),
`LaunchActivity` (lazy resume), `BaseFragment` (page events/menu refresh),
`Theme.getColor` (lookup Java overrides, без Python в отрисовке), manifest
(recovery launcher и отключённая в release fixed-fixture диагностическая Activity).
Добавлена строка после «Кастомизация», общий page header/insets сохранён;
PIN/licensing routes других настроек не менялись.

Это промежуточный отчёт: полнота P0–P8, аппаратная совместимость и готовность
выпуска пока не заявляются. APK ещё не создан.

### Проверки перед упаковкой

- Full Debug compile и Release/R8 без APK: PASS на первом полном проходе,
  `output/plugin-release-check.log` (9m41s); после исправлений lifecycle выполняется
  финальный проход `output/plugin-final-prepackage.log`.
- Lite Java compilation и `afatDebugRuntimeClasspath`: PASS, 1m58s;
  `output/plugin-lite-check.log`. Python/AliuHook отсутствуют в dependency report.
- `Tools/check_plugins.py`: PASS, 78 отрицательных manifest, 30 ZIP fixtures
  (включая UTF-8 и три упакованных examples), namespace/lifecycle cleanup.
- Стандартные проверки editions/page headers/header clearance: PASS.
- 23 plugin resource keys одинаковы в en/ru/uk; проверка APK translations впереди.
- Эмуляторы: API 37 / 16 KiB загружен; создан отдельный API 36 AVD из уже
  установленного system image. Ни один runtime PASS для нового APK пока не заявлен.
- Fingerprints разрешённых кэшем Java runtime/Gradle/Dobby артефактов:
  `output/plugin-dependency-fingerprints.json`; native Python fingerprint будет
  дополнен после native merge/упаковки.

Добавлены `sdk-v1.md` (реальные API вместо обещания всего draft), `.pyi`,
три исходных examples и упаковщик без imports. Для fixed-fixture проверки
bridge/hooks/registry в Full Debug есть диагностическая Activity, отключённая
в Release/Lite. Она не принимает внешние пакеты/пути и не работает с аккаунтами.

## 30.10.2026 — начало реализации, P0 и формат P1

Статус: разработка начата; исполняющего движка и страницы управления ещё нет.
P0 не завершён. Этот отчёт учитывает только изменения текущего задания,
а не остальные незакоммиченные изменения рабочей папки.

### Выполнено

1. В `TMessagesProj` и четырёх пользовательских app-модулях Full получает
   minSdk 24, Lite — 23. `TMessagesProj_AppTests` сохраняет более строгий API 26.
   Существующий native toolchain Telegram и версия клиента сохранены.
2. Добавлен edition-specific Java source root `src/full/java` / `src/lite/java`.
   Новый `org.telegram.messenger.plugins.PluginManifest` доступен только Full;
   зависимостей Python/AliuHook пока нет.
3. Реализована проверка manifest без импорта и исполнения Python: строгий
   UTF-8/JSON, лимит 64 KiB и глубина JSON 8, дубликаты/unknown поля, типы,
   обязательные поля, ID/version/entrypoint, возможности, диапазоны SDK/build,
   декларации raw hooks. Результат выдаёт копию метаданных; коды ошибок
   доступны отдельно от текста. Пути проверяются лексически.
4. Добавлены JSON Schema, положительный fixture, JVM regression harness
   с отрицательными случаями и Gradle-проверка фактической конфигурации шести
   модулей и выбранного source root.

### Граница реализации

Это валидатор **метаданных**, ещё не установщик `.byplugin`. Наличие entrypoint
и icon внутри ZIP, canonical containment, ZIP bomb/symlink/duplicates, размеры
изображений и доступность backend должны проверяться следующим слоем P1/P0.
Произвольный файл пока нельзя установить через клиент. Валидатор не обещает
безопасность Python-кода и не является sandbox.

JSON Schema служит редакторам; исполняемый Java-валидатор дополнительно
проверяет min ≤ max, совместимость с конкретным host, keyword имени Python
класса, дубликаты JSON-ключей, UTF-8, глубину и число компонентов пути.

### Проверки

- JVM harness на JDK 21 и установленном Gson 2.11.0: PASS, 78 отклонённых
  некорректных случаев, положительные metadata/method/constructor сценарии.
- `python Tools/check_bygram_editions.py`: PASS.
- Gradle Full и Lite configuration: PASS, все шесть модулей и source roots;
  логи `output/plugin-build-full.log`, `output/plugin-build-lite.log`.
- Production-класс компилируется с `javac --release 8`: PASS.
- JSON Schema: синтаксис JSON проверен; отдельный JSON Schema engine не запускался.
- Android device, Python, native hooks, R8, API 36/37 и 16 KiB: NOT TESTED.

Команды воспроизведения из корня проекта (PowerShell):

```powershell
$gsonJar = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0" -Recurse -Filter *.jar | Select-Object -First 1).FullName
New-Item -ItemType Directory -Force output/plugin-manifest-check | Out-Null
javac -encoding UTF-8 -cp $gsonJar -d output/plugin-manifest-check TMessagesProj/src/full/java/org/telegram/messenger/plugins/PluginManifest.java Tools/PluginManifestCheck.java
java -ea -cp "output/plugin-manifest-check;$gsonJar" PluginManifestCheck
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -PBYGRAM_EDITION=full -I Tools/check_plugin_build.init.gradle help --offline --console=plain
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -PBYGRAM_EDITION=lite -I Tools/check_plugin_build.init.gradle help --offline --console=plain
```

### Следующая работа

Исходный HEAD: `5d169b67f2d713f3f2f7eff9256d6578beec2118` плюс существующее
изменённое дерево. JDK Temurin 21.0.12.1+1, wrapper Gradle 8.13,
AGP 8.13.2, Gson 2.11.0 (имеющаяся зависимость). Python/AliuHook ещё не
включены, fingerprints AAR не получены. Полная Android compilation не запускалась.

P0: закреплённые Chaquopy 17.2.0 / Python 3.11 и BYxarek/hook, hashes/notices,
bridge-прототип и интеграционная матрица. Параллельно допустим P1: ZIP validator,
staging/atomic registry с восстановлением, затем native страницы.
Ни один полный этап P0–P8 пока не объявлен завершённым.

Системные страницы Telegram и UI BYgram в этом шаге не менялись;
изменена системная совместимость установки Full (Android 7+).
APK не создавался: единственная пользовательская сборка остаётся на P8 после
реализации и приёмки. Telegram/FTP/MySQL не использовались.

### 31.10.2026 — исполняемые account/hook примеры

Добавлены `examples/accounts` и `examples/hooks` с manifest. Общий harness
проверяет пять упакованных примеров (всего 32 ZIP fixtures), поведение account
example с отбрасыванием чужого аккаунта и устаревшей авторизации, а также hook
adapter на fake Java bridge. Native PASS по fake bridge не заявляется.
Все проверки `Tools/check_plugins.py` проходят. Системный UI не менялся.

### Проверка финального APK

Добавлен воспроизводимый `Tools/check_plugin_apk.py`: проверяет CRC/дубликаты
ZIP, наличие Python/hooks для четырёх ABI, Python assets и PT_LOAD всех ELF64
в самом APK, пишет размеры/SHA-256 в JSON. Self-check принимает синтетический
16 KiB ELF и отклоняет 4 KiB. Проверка файла APK последует после упаковки.
Проверки исходников editions/page headers/header clearance повторены — PASS.

### Android fixture двух владельцев hooks

Расширена встроенная diagnostic fixture: два независимых plugin ID/namespace,
порядок priority/reverse-after, конфликт replacement, закрытие handle одного
владельца при сохранении второго. Это проверка синтетического target без
аккаунтов; результат Android будет зафиксирован после запуска финального APK.
Production extension points не менялись.

### Незакрытые критерии исходного плана

Эти пункты нельзя отмечать выполненными по compilation или fake fixtures:

- P0: настоящий ARM/OEM, API 24, runtime всех ABI; перед public release — review notices/redistribution.
- P3: полный набор native crash/ANR/process-death сценариев; отдельный recovery smoke не заменяет всю матрицу.
- P4/P6: ручная приёмка меню/страниц после входа, TalkBack/RTL/крупный шрифт, два реальных аккаунта и штатные send/observe.
- P7: process death в каждой точке обновления/миграции и реальные logout/relogin.
- P8: Release/R8 runtime, сравнимый benchmark до/после и длительный leak/jank прогон на физическом устройстве.

Доступный итог этого прохода — проверяемый внутренний Full Debug артефакт и
протокол выполненных проверок; это не объявление public v1 готовым. Второй
Release APK не создаётся в обход ограничения одного APK. Production/FTP/MySQL
для локального движка не требуются и в этом проходе не используются.

### Финальная сборочная проверка 31.10.2026

Full Java + Release/R8: PASS, 14m54s,
`output/plugin-validation-final.log`. Mapping сохраняет публичные имена
PluginContext/PluginScope/HookBridge/Call/Probe.

Первая команда assemble Debug остановилась **до упаковки APK**: offline cache
не содержал `com.chaquo.python.runtime:bootstrap:17.2.0`. Старый APK от 29.10
не изменился и не считается новым артефактом этой задачи. Повтор выполняется
с обычным Gradle dependency resolution; лог `output/plugin-apk-build-online.log`.

## 31.10.2026 — блокирующий результат Android приёмки

Создан ровно один новый APK: `output/BYgram-full-plugins-13.1.1-2026-10-31.apk`,
Full Debug 13.1.1 (72379), 157277815 байт,
SHA-256 `44e7b63dcff264ba161b6a7be22a7903c94391f5fafc16969a1f166a50a19a06`.
Подпись v2 (прежний ключ), package/minSdk 24/target 36, CRC ZIP, zipalign,
все 28 ELF64 PT_LOAD 16 KiB, Full runtime четырёх ABI и localization
(en 366/366, ru 366/366, uk 233/233) — PASS.

**Runtime hooks FAIL:** API 37 x86_64 / 16 KiB и API 36 x86_64 / 4 KiB
падают native SIGSEGV после AliuHook/LSPlant init. Логи:
`output/plugin-api37-crash.log`, `output/plugin-api36-crash.log`.
Файл не считается готовым и пока не отправлен как готовый APK.

Добавлен `Tools/PluginNativeProbe.java`, компилируемый в DEX для app_process
без создания APK. На API 36: контроль без backend проходит GC; одна
инициализация XposedBridge без регистрации hook затем падает при GC.
Один hook/unhook + GC также падает. Это воспроизводит сбой без Telegram и
Python, но не устанавливает точную ошибочную строку native backend.
Логи `output/plugin-native-probe-{baseline,init}-api36.log`.

В исходниках закрыты API 36/37 до native init, добавлена проверка отказа
в диагностической Activity. **Этого исправления нет в уже упакованном APK**.
Повторная упаковка создаст ещё один APK; ограничение AGENTS.md про один APK
должно быть разрешено перед этой операцией. Код и проверки можно продолжать
без второй упаковки. Полная готовность P0/P5/P8 не заявляется.

### Дополнительные результаты изоляции backend

API 35 x86_64 / 16 KiB: native probe также падает (при init/GC либо в начале
цикла hook/unhook), `output/plugin-native-probe-{init-,}api35.log`.
Gate исходников дополнительно закрывает API 35; остаётся проверить API 28.

API 36: независимое recovery — PASS (`output/plugin-recovery-emulator-5558.json`).
Реальный Python startup-плагин работал до freeze, recovery открылся с frozen
main, его maps не содержали Python/AliuHook/Telegram native; UI-кнопка записала
флаг. На cold restart safe mode пропустил одобренный startup-плагин; после
явного снятия флага и отдельного restart он выполнился, healthy-start marker
очистился. Исходный пустой registry восстановлен. Это отдельный PASS recovery,
не PASS hooks. В AVD остались только неисполняемые синтетические package/data.

### API 28 x86_64 — полный runtime PASS

API 28 / 4 KiB, OS `PSR1.180720.012/4923214`: текущий APK прошёл весь
fixed-fixture набор Java/Python, settings/migration, generation, two-plugin
hooks ordering/conflict/owner cleanup, primitive/overload/constructor/null,
reentrancy, 100 hook/unhook, exactly-once original, quarantine, registry backup,
pending update/rollback и безопасное удаление symlink. Два холодных запуска
подтвердили результат; metadata — `output/plugin-device-emulator-5562.json`.
Второй прогон 4.49 s. Отдельный native DEX-harness: 100 циклов + GC — PASS,
exit 0 (`output/plugin-native-probe-api28.log`).

Исправленный gate оставляет **только x86_64 API 28**. API 35/36/37, ARM и
прочие среды возвращают HookBackendUnavailable без загрузки native backend.
Stable SDK и Python без raw hooks не запрещаются этим gate. Начат compile/R8
исправленных исходников, `output/plugin-backend-gate-validation.log`; новой
упаковки APK нет. Public v1 по исходной матрице не готов.

### Проверка скомпилированного gate

`Tools/PluginBackendGateCheck.java` + JVM-only `Tools/fixtures/plugins/android/os/Build.java`
проверили настоящий `HookBridge.class` после Full compilation: x86_64 API 28
разрешён, 13 других OS/ABI сочетаний отклонены без загрузки Android/native code.
PASS. Harness запускается с fixture classes в начале classpath, compiled Debug
classes, hook.jar, Chaquopy Java jar, Gson и android.jar. Fixture не входит в APK.

### Вложенные native assets

Валидатор APK расширен на `.so` внутри Chaquopy `.imy` и bootstrap assets,
с self-check вложенного архива. На созданном APK все **148 ELF64** (28 в
`lib/`, остальные в Python assets) проходят 16 KiB alignment. Native crash
не объясняется отсутствием ELF PT_LOAD alignment. SHA-256 APK неизменен.

### Завершающая проверка исправления gate

Full Debug compilation и Release/R8 исправленных исходников — PASS,
7m34s, `output/plugin-backend-gate-validation.log`. Нового APK эта команда
не создаёт. Gradle daemon остановлен.

В изолированной копии native исходников (`output/hook-native-experiment`,
соседний `C:/project/Aliucord-hook` и pinned AAR не изменялись) собрана одна
экспериментальная x86_64 `.so`, без APK. Поэтапный app_process probe API 36:
стадии 0/1 проходят GC, начиная с установки Class::SetStatus hooks — SIGSEGV.
Исключение только Class hooks при остальных включённых также не даёт успешного
init. Прямой callback без startup wrapper, passthrough Class callback и
экспериментальное preserve_all не устранили сбой; эти изменения не перенесены
в поставляемый код. Точная native первопричина не доказана и исправление backend
не заявляется. Следующий native этап — анализ trampoline/ABI инициализационных
перехватов с проверкой на конкретных ART builds. Для native probe учитывать
**и строку результата, и exit code**: PASS перед последующим SIGSEGV/139 не PASS.

Нужно решение владельца об исключении из ограничения одного APK, чтобы
упаковать уже скомпилированный fail-closed gate и проверить его на Android.
До такого решения вторая упаковка и отправка неисправленного APK как готового
не выполняются. ARM/OEM, API 24, реальный multi-account, accessibility,
release runtime и benchmark по-прежнему NOT TESTED.

## 31.10.2026 — разрешённая повторная упаковка

Владелец явно разрешил одну повторную сборку Full Debug вместо APK, не прошедшего Android приёмку. Запущена упаковка исправленного gate, без экспериментальных native библиотек. Лог: `output/plugin-apk-rebuild-approved.log`. После неё проверяются файл, Android fixture и доставка в Избранное.

## 31.10.2026 — повторный APK r2: сборка и приёмка

Владелец разрешил исключение из ограничения одного APK. Всего создано два
кандидата: первый с native FAIL не доставлялся как готовый; второй:
`output/BYgram-full-plugins-13.1.1-2026-10-31-r2.apk`, Full Debug,
13.1.1 (72379), `com.bygram.mobile`, minSdk 24 / target 36,
**158445411 байт**, SHA-256
`b827647f58958541965b409e47c0ddef2337889ad9aaa944ab0b16f50f579b9e`.

Сборка `:TMessagesProj_App:assembleAfatDebug --offline` — PASS за 2m57s,
`output/plugin-apk-rebuild-approved.log`. Подпись v2 прежним сертификатом,
ZIP/zipalign, четыре ABI, все 148 ELF64 (включая вложенные Python assets),
localization en 366/366, ru 366/366, uk 233/233 — PASS.
Все native fingerprints совпадают с первым кандидатом и pinned AAR;
экспериментальные `.so` в r2 не включены. Metadata: `output/plugin-apk-r2-report.json`.

| Среда r2 | Результат |
|---|---|
| API 28 x86_64 / 4 KiB | Полный fixture, включая два владельца hooks, conflict, exactly-once original и cleanup — PASS; backend загружен |
| API 36 x86_64 / 4 KiB | Bridge/settings/migration/generation/registry/update/rollback — PASS; HookBackendUnavailable, backend не загружен |
| API 37 x86_64 / 16 KiB | Тот же core fixture — PASS; HookBackendUnavailable, backend не загружен |

Срезы: `output/plugin-r2-api28.json`, `output/plugin-r2-api36.json`,
`output/plugin-r2-api37.json`. Отсутствие загрузки backend проверено по maps
самого процесса, а не только по строке SKIPPED.

Recovery r2 API 36 — PASS, `output/plugin-r2-recovery-api36.json`:
синтетический плагин блокирует main thread настоящим Python callback;
независимый recovery Activity открывается, не содержит Python/AliuHook/Telegram
native libs, кнопка пишет safe mode. Cold restart пропускает одобренный плагин;
после явного тестового восстановления он стартует, startup marker очищается.
Исходный пустой registry восстановлен, процессы и AVD остановлены; синтетические
неисполняемые package/data остаются только в тестовом AVD.

Ранние повторы recovery с SIGSTOP всего процесса были перекрыты системным
ANR-диалогом System UI. Они не считаются PASS. Также устранена ошибка подготовки
теста: после прерванной записи оставался `plugin-safe-mode.new`; host правильно
сохранял safe mode. Очистка base/.bak/.new делается только для известного
синтетического fixture. После закрытия оставшегося системного диалога сценарий
с реальным Python UI hang прошёл на API 36. Recovery API 37 в r2 — NOT PASSED;
его core runtime PASS не означает PASS recovery. Физический ARM, API 24,
реальные аккаунты, TalkBack/RTL, Release runtime и benchmark — NOT TESTED.

### Доставка r2 подтверждена

31.10.2026 в 10:18 МСК отправлен один проверенный r2 APK через `telegram-cli`
в Telegram «Избранное», сообщение **201348**. Ответ send — ok; повторный
`read me --id 201348 --json` подтвердил out=true, имя
`BYgram-full-plugins-13.1.1-2026-10-31-r2.apk` и размер **158445411 байт**.
Результаты: `output/plugin-r2-delivery.json`,
`output/plugin-r2-delivery-verified.json`. Перед отправкой повторно проверен
SHA-256 артефакта. Первый failed кандидат не отправлялся.

Разрешённая повторная сборка, проверка её доступных сценариев и доставка
завершены. Это внутренний Full Debug прототип с ограничением raw hooks;
полная исходная приёмка v1 остаётся незавершённой по перечисленным выше пунктам.
Build daemon и тестовые AVD остановлены. FTP/production/MySQL не использовались.

### 31.10.2026 — установлена причина повреждения памяти raw hooks

По предоставленным исходникам Aliucord-hook выявлен дефект Dobby 1.2:
ошибочная проверка свободного диапазона и MAP_FIXED заменяют часть ART
dalvik-linear-alloc shadow map. Это подтверждено maps до/после первого
Class::SetStatus hook на API 36 и отдельным тестом реального Maven-архива,
который ожидаемо проваливает проверку сохранения занятой страницы.

Подготовлен патч `third_party/patches/dobby-1.2-preserve-mappings.patch`
и native regression `Tools/DobbyAllocationProbe.cpp`. Исправленная отдельная
сборка прошла 100 hook/unhook + HashMap stress + GC на API 28/35/36/37 x86_64
без диагностических изменений AliuHook. Тест защиты RW/PROT_NONE прошёл
на всех четырёх средах (4 и 16 KiB). Подробные адреса, provenance, ограничения и логи:
[native-hook-investigation.md](native-hook-investigation.md).

Pinned AAR, raw hook gate и APK r2 остаются прежними. Новый APK не собирался.
Системные страницы и настройки Android в рамках расследования не менялись.
Результат native probe ещё не означает приёмку интеграции Python/полного BYgram
или ARM; ограничения поставленной версии сохраняются.
Все AVD расследования остановлены.

## 31.10.2026 — интеграция исправленного Dobby (в работе)

Начата сборка локальной версии Aliuhook 1.1.4-api37.1-bygram.1 для четырёх ABI. Tools/build_plugin_backend.py экспортирует закреплённые git commits в отдельный каталог, применяет runtime-патч и совместимость logging с NDK 30; исходный соседний репозиторий не изменяется. Пересобирается только native payload, Java API и остальные entries AAR сверяются с исходным архивом. До Android приёмки gate остаётся прежним. Лог: output/plugin-backend-bygram1-build.log. В этой задаче запланирован ровно один итоговый APK после проверок; прежние APK не перезаписываются. Системные страницы Android пока не менялись.


### Закрепление backend и native regression — PASS

Новый локальный AAR `1.1.4-api37.1-bygram.1`: SHA-256
`06051b75f3251a1f70e986d160e77b3e0c3614ef8e5bc9913c814ee94b6628c8`.
Собраны arm64-v8a, armeabi-v7a, x86 и x86_64; ELF64 16 KiB — PASS.
Все entries, кроме четырёх libaliuhook.so, побайтно совпадают с прежним AAR.
Commit/provenance и per-ABI hashes сохранены рядом с Maven-артефактом;
исходная версия не перезаписана. Новая metadata не подтягивает старый Dobby.
Native 100 hook/unhook + HashMap + пять GC/finalization и проверка оригинала
после каждой GC — PASS с exit 0 на API 35/36/37 x86_64. Проверка занятых RW
и PROT_NONE адресов против заново собранного static Dobby — PASS там же,
page size соответственно 16/4/16 KiB. Логи: output/plugin-bygram1-{native,allocation}-api*.log.
Общий Tools/check_plugins.py — PASS. Full compile/R8 ещё выполняется.
Подготовленный gate допускает только 64-bit process с primary x86_64 на
API 28/35/36/37; прочие среды должны отказать до native init. Это пока не
заявление о приёмке полного BYgram на расширенной матрице.
Системные страницы Telegram и пользовательские настройки Android не менялись;
исправляется native-баг, не связанный с кастомными настройками BYgram.

### Compile/R8 и gate — PASS

Full Debug Java compilation и Release/R8 с новым pinned AAR — PASS за 9m17s,
400 tasks; `output/plugin-bygram1-compile-r8.log`. Gradle подтвердил Full
minSdk/source roots во всех шести модулях и checksum backend.
`Tools/PluginBackendGateCheck.java` проверил настоящий compiled HookBridge:
64-bit x86_64 API 28/35/36/37 разрешены, остальные OS/primary ABI, пустой ABI
и 32-bit process отклоняются без native init. Лог: output/plugin-bygram1-gate.log.
API 28 тоже прошёл обновлённые native/GC и allocation probes с exit 0
(page=4096); output/plugin-bygram1-{native,allocation}-api28.log.
На этом этапе APK не создан. Release runtime и ARM по-прежнему не проверены.

### Один итоговый APK r3 — файл PASS, Android приёмка начата

В рамках этой задачи выполнена ровно одна упаковка:
`output/BYgram-full-plugins-13.1.1-2026-10-31-r3.apk`, Full Debug,
13.1.1 (72379), com.bygram.mobile, minSdk 24 / target 36,
158445888 байт, SHA-256 `cd0f6f07abfab4c5614b3382626f58502db0c71c2e15394fe384354b057da0af`.
`assembleAfatDebug --offline` — PASS за 2m54s.
Подпись v2 прежним сертификатом, ZIP CRC/duplicates, zipalign 16 KiB,
четыре ABI и все 148 ELF64 (включая Python assets) — PASS.
Локализации en 366/366, ru 366/366, uk 233/233 — PASS.
Все четыре libaliuhook.so и libc++_shared.so в APK побайтно совпадают
с новым pinned AAR; экспериментальные библиотеки не попали в приложение.
Metadata: output/plugin-bygram1-apk-report.json; сборка: output/plugin-bygram1-apk-build.log.
Lite configuration/dependency graph — PASS за 9s, Python/AliuHook/Dobby
отсутствуют; Lite APK не создавался. Gradle daemon остановлен.

Первый Android прогон r3: API 28 x86_64 / 4 KiB — полный fixture PASS,
включая реальный Java/Python bridge, два владельца hooks, конфликты,
exactly-once original, 100 hook/unhook, quarantine и post-hook GC/HashMap.
Проверены maps (backend загружен) и сохранение того же процесса после
fixture; output/plugin-bygram1-device-api28.json. Остальные прогоны выполняются.

### Интеграция r3 — FAIL вне API 28; исправление gate

| Среда r3 | Результат |
|---|---|
| API 28 x86_64 / 4 KiB | Полный Java/Python/hooks fixture и post-hook GC — PASS; backend загружен, процесс жив |
| API 35 x86_64 / 16 KiB | FAIL: two_plugins assertion; обнаружены два fixture после CONFIG_ASSETS_PATHS recreation; повторный warm запуск также FAIL (ConcurrentModificationException) |
| API 36 x86_64 / 4 KiB | FAIL: cold SIGSEGV в ART ExecuteSwitchImplCpp во время HookBridge.register из Python/Java; warm запуск тоже падает |
| API 37 x86_64 / 16 KiB | FAIL: _selftest.hooks:79, hook/unhook цикл; events подтверждают один onCreate |

Android metadata: output/plugin-bygram1-device-api{28,35,36,37}.json.
Crash/events: output/plugin-bygram1-api36-{crash,warm-crash}.log,
output/plugin-bygram1-api{35,36,37}-events.log. На API 35 повторное создание
Activity загрязняет первый результат; это не объясняет доказанный single-run
FAIL API 37 или crash API 36. Новая native первопричина не установлена;
гипотеза JIT/ART требует отдельной изоляции. Исходный дефект MAP_FIXED исправлен,
но этого недостаточно для расширения поддержки полного BYgram.

Gate возвращён к API 28 с проверкой primary ABI и разрядности процесса;
compiled JVM gate PASS, output/plugin-bygram1-failclosed-gate.log.
Debug diagnostic Activity теперь использует process-wide atomic single-flight
с освобождением в finally, чтобы recreation не запускал второй fixture поверх
первого. Изменён только внутренний диагностический экран, не страницы Telegram.
Runtime-проверка этого guard требует следующего APK и пока не заявляется.
Запущен повторный compile/R8: output/plugin-bygram1-failclosed-compile.log.
Новой упаковки нет: AGENTS.md требует только один APK после задачи.

Recovery r3 API 36 — PASS при настоящем Python UI hang: отдельный процесс
без Telegram/Python/hook libs, UI кнопка safe mode, cold restart пропускает
одобренный startup plugin, явное тестовое восстановление возвращает его,
healthy-start marker очищается. output/plugin-bygram1-recovery-api36.log;
metadata output/plugin-recovery-emulator-5558.json (перезаписан текущим прогоном).
Пустой registry восстановлен; синтетические package/data остаются только в AVD.
Recovery API 37 в этой задаче не проверялся; реальные аккаунты не использовались.

### Проверка окончательных исходников — PASS

После возврата fail-closed gate и добавления single-flight diagnostic:
Full Debug compile + Release/R8 PASS за 7m50s, 400 tasks,
output/plugin-bygram1-failclosed-compile.log. JVM проверяет именно обновлённый
HookBridge.class: только 64-bit primary x86_64 API 28 допускается;
output/plugin-bygram1-failclosed-gate.log. Нового APK нет, последнее ограничение
и diagnostic guard в r3 отсутствуют. Runtime guard и готового APK с этим gate
пока не проверены; это не замаскировано compile PASS.
Все AVD и Gradle daemon остановлены. FTP/production/MySQL не использовались.

### Разрешена одна дополнительная упаковка r4

Владелец явно разрешил один дополнительный APK после диагностического r3. Запущена упаковка окончательных исходников: gate только 64-bit primary x86_64 API 28, single-flight diagnostic. Библиотеки и pinned AAR не меняются. Лог: output/plugin-bygram1-r4-build.log. Это разрешённое исключение из ограничения одного APK, не тихая повторная сборка.


Диагностический r3 доставлен в Избранное с явной подписью «приёмку НЕ прошёл,
не использовать как рабочую версию» и перечислением FAIL/несовпадения gate.
Сообщение 201365; send ok и повторный read подтвердили out=true, имя r3
и размер 158445888. Результаты: output/plugin-bygram1-delivery.json,
output/plugin-bygram1-delivery-verified.json. Это доставка диагностического
артефакта по правилу отправки каждого APK, не объявление его готовым.

### Разрешённый r4 — сборка и файл PASS

Создан один разрешённый дополнительный APK после r3:
`output/BYgram-full-plugins-13.1.1-2026-10-31-r4.apk`, Full Debug 13.1.1 (72379),
158446049 байт, SHA-256 `350e3203e19187cc06f2fd92f35f9005e5b3fa561714c9b33961759b0fa8d861`.
Сборка assembleAfatDebug --offline PASS за 2m22s;
output/plugin-bygram1-r4-build.log. Package com.bygram.mobile, minSdk 24 / target 36,
подпись v2 прежним сертификатом, ZIP/zipalign, 148 ELF64/16 KiB,
четыре ABI, en 366/366, ru 366/366, uk 233/233 — PASS.
Hook/libc++ entries точно совпадают с pinned bygram.1 AAR.
В r4 уже включены gate только 64-bit primary x86_64 API 28 и diagnostic single-flight.
Первый полный Java/Python/hooks/GC fixture API 28 — PASS (8.47s),
maps подтверждают загрузку backend, тот же процесс жив после прогона;
output/plugin-bygram1-r4-device-api28.json. Другие среды ещё проверяются.

### r4: API 35/36 core и recovery API 36 — PASS

Core fixture API 35 x86_64 / 16 KiB (12.37s) и API 36 x86_64 / 4 KiB
(36.80s) прошёл: Java/Python lifecycle/settings/generation, registry backup,
pending update/rollback. HookBackendUnavailable до native init; maps
подтвердили отсутствие libaliuhook.so, тот же процесс жив после fixture.
Metadata: output/plugin-bygram1-r4-device-api35.json и ...-api36.json.
Это не benchmark, не PASS raw hooks на этих API.

Recovery r4 API 36 при реальном Python UI hang — PASS после очистки среды:
output/plugin-bygram1-r4-recovery-api36-retry.log и
output/plugin-bygram1-r4-recovery-api36.json. Проверены отдельный процесс без
Telegram/Python/hooks libs, UI safe-mode flag, cold restart без плагина,
явное тестовое восстановление и очистка healthy-start marker.
Пустой registry восстановлен; процессы остановлены. Синтетические
package/data остаются только в тестовом AVD.
Первый прогон не засчитан: recovery перекрыл диалог «System UI isn't responding».
Сохранены output/plugin-bygram1-r4-recovery-api36.log и
...-recovery-blocking-dialog.xml. Закрыт только зависший System UI тестового
эмулятора, остальные AVD остановлены; повтор затем прошёл. Прерывание первого прогона сохранено отдельно; причина зависания
System UI не установлена и не приписывается BYgram или среде без доказательств. API 37 core ещё проверяется.

### r4: окончательная Android матрица — PASS в пределах прототипа

| Среда r4 | Результат |
|---|---|
| API 28 x86_64 / 4 KiB | Полный Java/Python/hooks fixture, два владельца, conflicts, exactly-once original, 100 hook/unhook, post-hook GC/HashMap — PASS; maps: backend загружен |
| API 35 x86_64 / 16 KiB | Core/settings/generation/registry/update/rollback — PASS; HookBackendUnavailable, maps: backend не загружен |
| API 36 x86_64 / 4 KiB | То же core — PASS; backend не загружен; recovery с настоящим Python UI hang — PASS после очистки System UI |
| API 37 x86_64 / 16 KiB | То же core — PASS; backend не загружен; recovery не проверялся |

Во всех четырёх случаях после fixture проверено сохранение того же PID.
Metadata: output/plugin-bygram1-r4-device-api{28,35,36,37}.json.
Итоговый свод: output/plugin-bygram1-r4-acceptance.json,
status INTERNAL_PROTOTYPE_PASS, public_v1_ready=false.
Перед отправкой повторно проверен SHA-256 r4; файл не менялся после приёмки.
Все AVD и Gradle daemon остановлены; процессы тестов завершены.

В задаче созданы два кандидата: r3 и явно разрешённый после его FAIL r4.
R4 содержит окончательные gate и single-flight diagnostic, которые отсутствуют
в r3. Форсированная recreation diagnostic в отдельном regression пока
не воспроизводилась; нормальный диагностический маршрут r4 проверен на четырёх API.
Не заявляются ARM/OEM, API 24 runtime, реальные аккаунты, TalkBack/RTL,
Release runtime, performance или общая готовность P0–P8.

Следующий native этап: изолировать сбой полного app/Python Java bridge на
API 35/36/37 (холодный/прогретый процесс, повторные hooks, ART/JIT), отдельно
от уже исправленного MAP_FIXED. Гипотезы не объявлять причиной без
reproducer. Gate не расширять по одному app_process PASS.

### Доставка r4 подтверждена — этап завершён

31.10.2026 r4 отправлен через telegram-cli в Telegram «Избранное»;
сообщение **201367**. Send ok и повторный `read me --id 201367 --json`
подтвердили out=true, имя BYgram-full-plugins-13.1.1-2026-10-31-r4.apk
и размер **158446049**. В подписи явно указаны внутренний прототип,
ограничение raw hooks и NOT TESTED; готовность public v1 не заявляется.
После доставки локальный SHA-256 снова совпал:
`350e3203e19187cc06f2fd92f35f9005e5b3fa561714c9b33961759b0fa8d861`.
Результаты: output/plugin-bygram1-r4-delivery.json,
output/plugin-bygram1-r4-delivery-verified.json;
свод дополнен delivery в output/plugin-bygram1-r4-acceptance.json.
Диагностический r3 не удалялся из Избранного; у него отдельное явное
предупреждение о FAIL, для проверки текущего этапа использовать r4.

Закрепление исправленного AAR, проверки/сужение gate, compile/R8,
приёмка доступной матрицы и разрешённая дополнительная поставка завершены.
Full v1 и raw hooks на новых ART остаются незавершённой работой по указанным
выше ограничениям; следующий шаг не подменяется обещанием готовности.
