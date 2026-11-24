# Исправления базы Telegram Android · 24.11.2026

Изменения внесены в рабочем дереве `BYgram-hotfix-r4` поверх коммита `aaa8501cb`. Они относятся к коду Telegram Android, встроенным библиотекам, ресурсам, сборке и тестам. Этот список можно использовать при переносе правок на обновлённую базу Telegram. Исходные сообщения компиляторов, конкретные строки Groovy DSL и история проверок находятся в [отчёте Afat Debug](apk-build-diagnostics-2026-11-24.md).

## Нативный код

| Файл | Изменение |
| --- | --- |
| [`ConnectionsManager.cpp`](../TMessagesProj/jni/tgnet/ConnectionsManager.cpp) | В `failNotRunningRequest` задача `scheduleTask` завершается через `return;` сразу после отмены найденного запроса. Если запрос не найден, `void`-обработчик завершается обычным выходом. Декодер Base64URL преобразует входной байт в `unsigned char` перед индексированием таблицы. Логи используют `PRId64` для знаковых 64-битных значений. ID запроса для `PRIx64` хранится в `uint64_t`, поэтому в журнале он сохраняется целиком и на 32-битных ABI. |
| [`GroupInstanceReferenceImpl.cpp`](../TMessagesProj/jni/tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp), [`InstanceV2CompatImpl.cpp`](../TMessagesProj/jni/tgcalls/tgcalls/v2/InstanceV2CompatImpl.cpp), [`InstanceV2ReferenceImpl.cpp`](../TMessagesProj/jni/tgcalls/tgcalls/v2/InstanceV2ReferenceImpl.cpp), [`CallCoreHost.cpp`](../TMessagesProj/jni/tgcalls/tgcalls/v2wasm/CallCoreHost.cpp) | Во всех четырёх местах вызывается `std::make_unique<webrtc::RtcEventLogFactory>()`. Устаревший конструктор игнорировал переданный `TaskQueueFactory*`. |
| [`DesktopCaptureSourceHelper.cpp`](../TMessagesProj/jni/tgcalls/tgcalls/desktop_capturer/DesktopCaptureSourceHelper.cpp) | Три захвата `[=, this]` заменены на явные `[this]` и `[this, guard]`, совместимые с C++17. Отложенный захват кадра сохраняет копию слабой ссылки `guard`. |
| [`renderer.h`](../TMessagesProj/jni/twidget/twidget/core/src/renderer.h) | В `Touch` инициализированы `x0`, `y0`, `x` и `y` до вычисления `sent_x` и `sent_y`. При начале касания начальные и текущие координаты берутся из события. |
| [`image.cpp`](../TMessagesProj/jni/image.cpp) | В `stackBlurBitmap` два временных массива переменной длины заменены на инициализированные нулём `std::vector<int>` прежних размеров. |
| [`org_telegram_messenger_voip_Instance.cpp`](../TMessagesProj/jni/voip/org_telegram_messenger_voip_Instance.cpp) | Неизвестное значение режима экономии данных преобразуется через `std::to_string` перед включением в сообщение исключения. В журнал `setConferenceCallId` передаётся полное значение `jlong` через `PRId64`. Четыре временных массива для уровней звука и описаний участников заменены на `std::vector`. Для пустого списка Java получает массивы длины ноль, а вызовы `Set*ArrayRegion` пропускаются. |
| [`gifvideo.cpp`](../TMessagesProj/jni/gifvideo.cpp) | Журнал списка форматов получает значение `AVPixelFormat` через `*p`. Смещение файла записывается в журнал как 64-битное число через `PRId64`. |
| [`video_source_sink_controller.cc`](../TMessagesProj/jni/voip/webrtc/video/video_source_sink_controller.cc), [`rtp_video_stream_receiver2.h`](../TMessagesProj/jni/voip/webrtc/video/rtp_video_stream_receiver2.h) | Адрес источника видео выводится через `%p`. `RTC_GUARDED_BY(packet_sequence_checker_)` прикреплена к объявлению H.265-трекера. |

## Строковые ресурсы Telegram

AAPT2 сообщал о 27 строках, которые имели значения только в отдельных локалях. В [`values/strings.xml`](../TMessagesProj/src/main/res/values/strings.xml) добавлены девять английских форм с текстом соответствующего `_other`. Существующие локальные формы продолжают иметь приоритет в бинарной локализации.

| Ключ | Добавленные формы |
| --- | --- |
| `Languages` | `_zero`, `_two`, `_few`, `_many` |
| `AddManyMembersAlertTitle` | `_two`, `_few`, `_many` |
| `AddManyMembersAlertNamesText` | `_two`, `_few` |

У остальных 18 ключей поиск в репозитории нашёл только объявления переводов. Удалены 45 таких объявлений из `values-ar`, `values-de`, `values-es`, `values-it`, `values-ko`, `values-nl`, `values-pt-rBR`, `values-ru` и `values-uk`.

- Вход и пароль — `EmailPasswordConfirmText3`, `PleaseEnterFirstPassword`, `PleaseEnterPassword`, `ResetAccountButton`, `ResetMyAccountText`, `YourEmailCode`, `YourEmailCodeInfo`, `YourPasswordSuccessText`.
- Контакты и приглашение — `NoContactsYet`, `NoContactsYetLine1`, `NoContactsYetLine2`, `NoContactsYetLine3`, `ShareTelegram`.
- Прокси звонков — `UseProxyForCalls`, `UseProxyForCallsInfo`.
- Виджет и даты — `WidgetPasscodeEnable2`, `formatterMonthName`, `formatterMonthYear2`.

## Gradle и Kotlin-тесты Telegram

- В [`settings.gradle`](../settings.gradle) дескриптор промежуточного проекта `:TMessagesProj_Modules:media3` указывает на существующий каталог `TMessagesProj_Modules/media/libraries`. Каталог содержит дочерние библиотеки и позволяет подключить их с текущими путями Gradle-проектов.
- В [`TMessagesProj/build.gradle`](../TMessagesProj/build.gradle) проверка `checkVisibility` определяет список запрошенных задач, значение `IS_PRIVATE` и путь к приватному HockeyApp APK при конфигурации. При выполнении задача использует заранее вычисленные параметры. Правила допуска Debug, Release и HockeyApp, а также условие очистки приватного HockeyApp APK сохранены.
- Все 125 присваиваний Groovy DSL из журнала переведены на `property = value` с исходными правыми частями. Затронуты 43 файла — семь Gradle-файлов основного проекта и его APK-модулей, 35 файлов Media3 и один файл JLaTeXMath. Полный список свойств и исходных номеров строк приведён в разделе [Gradle, Android SDK и Java](apk-build-diagnostics-2026-11-24.md#gradle-android-sdk-и-java).
- В [`TelegramBuildAppPlugin.kt`](../buildSrc/src/main/kotlin/org/telegram/plugin/TelegramBuildAppPlugin.kt) неиспользуемый параметр лямбды помечен `_`.
- В [`BaseSchemeTest.kt`](../TMessagesProj_AppTests/src/androidTest/kotlin/org/telegram/tgnet/test/BaseSchemeTest.kt) `checkNotNull` закрепляет инвариант `ThreadLocal.withInitial` перед тремя обращениями к стеку. Четыре вызова `Configuration.copy()` заменены конфигурациями на основе `ConfigurationBuilder`. При динамическом выборе поля сохраняется точечное `@Suppress("DEPRECATION_ERROR")` на `property(KClass, String, ...)` из Fixture 1.2.0. Параметры рекурсии, nullable-сценарии и выбор поля сохраняются.

## Локальная регрессионная проверка BYgram

В [`Tools/check_bygram_settings_fixes.py`](../Tools/check_bygram_settings_fixes.py) три ожидания от прежней активации лицензии через бота заменены проверками действующего поведения API активности. `activity_start` выдаёт токены после проверки сессии аккаунта. Совместимый со старыми клиентами `license` подтверждает готовность авторизованной сессии. Обе проверки сработали при удалении соответствующего условия из копии PHP-исходника в памяти.

## Проверки

- Прошли 54 условия регрессионной проверки настроек, 18 сценариев хранения Spy и компиляция `:TMessagesProj_AppTests:compileAfatDebugAndroidTestKotlin`.
- `checkVisibility` проверена в обычном режиме и с `IS_PRIVATE=true`. Запрос без разрешённой задачи отклонён, явная Debug-задача разрешена.
- Затронутые единицы трансляции прошли Clang `-fsyntax-only` с целевыми предупреждениями как ошибками на `arm64-v8a`, `armeabi-v7a`, `x86` и `x86_64`. Анализ `-Wthread-safety` прошёл для H.265-трекера. Декодер Base64URL проверен на корректном вводе и всех байтах `0x80`–`0xFF` в режимах знакового и беззнакового `char`.
- Для нативного размытия совпали пиксели и баланс блокировок в 80 сценариях. Передача VoIP JNI проверена с пустыми и заполненными массивами.
- `:TMessagesProj_App:processAfatDebugResources` и `:TMessagesProj_App:generateAfatDebugStringResourceIdsAsset` прошли. Предупреждения `without required default value` исчезли. Проверены девять ресурсных ID, английские значения, локальные формы `ar`, `ko` и `nl` и бинарные строки BYgram для EN, RU и UK.
- Gradle успешно сконфигурировал проект с `--warning-mode=all`. Сообщений об устаревшей форме присваивания не было.
- APK Afat Debug `13.2.2 (73049)` был собран на первом этапе диагностики. Для последующих правок запускались точечные проверки C++, ресурсов, Gradle и компиляции тестов. Хеш ранее собранного APK подтверждён после последних правок.

На машине для проверок использован установленный JDK 21 с `java` и `javac` в `PATH`. Chaquopy получил Python 3.11 через `-PBYGRAM_BUILD_PYTHON`. Порядок настройки окружения описан в [инструкции сборки](build.md).

## Пути правок в подмодулях

| Подмодуль | Изменения |
| --- | --- |
| `TMessagesProj/jni/tgcalls` | Четыре вызова `RtcEventLogFactory` и три захвата в `DesktopCaptureSourceHelper.cpp`. |
| `TMessagesProj/jni/twidget/twidget` | Инициализация координат в `core/src/renderer.h`. |
| `TMessagesProj_Modules/media` | Присваивания Groovy DSL в 35 файлах, включая `common_config.gradle` и файлы библиотек. |
| `TMessagesProj/lib/jlatexmath` | Присваивания `namespace` и `abortOnError` в `jlatexmath/build.gradle`. |
