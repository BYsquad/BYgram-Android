# Диагностика сборки BYgram Afat Debug · 24.11.2026

Рабочее дерево `BYgram-hotfix-r4`, базовый коммит `aaa8501cb`. Сборка приложения — `:TMessagesProj_App:assembleAfatDebug`. Среда — Windows, JDK 21, Gradle 8.13, AGP 8.13.2, NDK 27.2.12479018, CMake 3.22.1 и Python 3.11 для Chaquopy. Отчёт относится к варианту Afat Debug и наблюдавшимся диагностическим сообщениям.

Для повторной сборки задайте `JAVA_HOME`, `PATH` и `$buildPython` с путём к Python 3.11, затем выполните команду ниже.

```powershell
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain `
    :TMessagesProj_App:assembleAfatDebug --rerun-tasks --console=plain --warning-mode=all `
    "-PBYGRAM_BUILD_PYTHON=$buildPython"
```

## Результат сборки и проверки APK

`BUILD SUCCESSFUL` за 12 мин 2 с. Gradle выполнил 319 задач. Для C/C++ использованы журналы полной компиляции предыдущего прогона этого рабочего дерева. При повторной сборке задачи Gradle для четырёх ABI отработали с существующими выходами Ninja без повторного вывода предупреждений Clang.

Готовый файл `TMessagesProj_App/build/outputs/apk/afat/debug/BYgram-afatDebug.apk` весит 184 816 248 байт. Пакет `com.bygram.mobile`, версия `13.2.2 (73049)`, minSdk 24 и targetSdk 36. Проверены подпись APK Signature Scheme v2, выравнивание ZIP 16 КиБ, наличие `libtmessages.49.so` и `libtwidget.so` для четырёх ABI и бинарные строки BYgram для EN, RU и UK.

SHA-256 `e67b1c35d0336dda2c67fe8954bd65e073b471b5115c2c9dffc552c08645207a`.

APK доставлен в «Избранное» Telegram, сообщение `202974`.

## Ошибка регрессионной проверки

`Tools/check_bygram_settings_fixes.py` падал на трёх ожиданиях от прежней платной активации. В обновлённом тесте проверяется, что `activity_start` выдаёт токены после проверки сессии аккаунта, а `license` для старых клиентов подтверждает готовность уже авторизованной сессии. Проверка ссылки `?start=bind_` удалена вместе с ожиданиями `managerUrl()`. Прошли 54 условия проверки настроек и 18 сценариев хранения Spy.

В начале проверки `JAVA_HOME` указывал на отсутствующий JDK 17, а `javac` отсутствовал в `PATH`. Для команд сборки и тестов использован установленный JetBrains Runtime SDK 21 с `java` и `javac`. В Chaquopy передан установленный Python 3.11 через `-PBYGRAM_BUILD_PYTHON`.

Обе новые проверки отдельно отреагировали на удаление соответствующей проверки сессии из копии PHP-исходника в памяти.

## Нативная сборка

Источник — сохранённые журналы полной нативной сборки беты №4, включая конфигурацию CMake и компиляцию/линковку Ninja. Clang выдал **312 предупреждений**. Каждая из четырёх ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) дала по 78 сообщений. CMake дополнительно выдал по одному предупреждению на ABI. Ошибок компиляции или линковки в этих журналах нет.

Числа ниже включают повторения на разных ABI и предупреждения из заголовков, включённых в несколько единиц трансляции. Все перечисленные группы можно исправить в исходниках либо обновлением соответствующего подмодуля. В колонке «Исправление» указаны варианты для последующей проверки.

| Где и что | Сколько | Исправление |
| --- | ---: | --- |
| `-Wreturn-type`, `tgnet/ConnectionsManager.cpp:2228` | 4 | У задачи `failNotRunningRequest` тип возврата выводится из `return true`, но при отсутствии запроса функция доходит до конца. Проверить сигнатуру `scheduleTask` и использовать `return;` в ветке найденного запроса для `void`-обработчика. Проверить оба сценария отмены. |
| `-Wformat-security`, `gifvideo.cpp:143` | 4 | Передавать строку журнала FFmpeg через фиксированный формат `LOGE("%s", line)`. Проверить вывод сообщений со знаками `%`. |
| `-Wstring-plus-int`, `voip/org_telegram_messenger_voip_Instance.cpp:407` | 4 | Заменить сложение строкового литерала с числом явным преобразованием через `std::to_string`. Проверить диагностическую ветку. |
| `-Wchar-subscripts`, `tgnet/ConnectionsManager.cpp:3352` | 4 | Преобразовать индекс в `unsigned char`. Таблица `url_char_to_value` содержит 256 элементов и даёт обработать байты вне алфавита. Проверить корректный Base64URL и обработку посторонних байтов. |
| `-Wuninitialized`, `twidget/twidget/core/src/renderer.h:239` | 8 | Инициализировать исходные координаты касания до присваивания `sent_x/sent_y`. Проверить создание `Touch` и первые события движения. Изменение находится в подмодуле `twidget`. |
| `-Wformat`, `tgnet`, WebRTC, GIF и VoIP JNI | 30 | Использовать `PRId64` для `int64_t` и подходящее приведение для `jlong`, `%p` с указателем для адресов, в `gifvideo.cpp:152` передавать значение `*p` для `%d`. Проверить на 32- и 64-разрядных ABI. |
| `-Wmissing-declarations` и `-Wignored-attributes`, WebRTC `rtp_video_stream_receiver2.h:393` и `gifvideo.cpp:50` | 44 | Присоединить `RTC_GUARDED_BY` к объявлению `h265_tracker_` и проверить соблюдение аннотации. Оформить безымянный `typedef` как корректное объявление структуры после проверки использований. |
| `-Wdeprecated-declarations`, конструктор `RtcEventLogFactory` | 16 | Использовать `std::make_unique<webrtc::RtcEventLogFactory>()` в `CallCoreHost.cpp:601`, `GroupInstanceReferenceImpl.cpp:692`, `InstanceV2CompatImpl.cpp:442` и `InstanceV2ReferenceImpl.cpp:522`. Передаваемый `TaskQueueFactory*` игнорируется в текущем заголовке WebRTC. Изменение находится в подмодуле `tgcalls`. Проверить сборку и звонки. |
| `-Wunknown-attributes`, `v2wasm/wasm_module_entry.cpp` | 24 | Android Clang игнорирует атрибуты WASM `import_module`, `import_name`, `export_name`. Проверить, нужен ли этот файл Android-цели из `jni/voip/CMakeLists.txt`. При наличии нужных Android-символов ограничить атрибуты WASM-целью. Проверить линковку обоих вариантов. |
| `-Wc++20-extensions` и `-Wvla-cxx-extension`, tgcalls, `image.cpp`, VoIP JNI | 36 | Заменить `[=, this]` явными C++17-захватами. Заменить массивы с размером времени выполнения подходящими контейнерами и учесть нулевой размер, JNI-указатели и срок жизни. |
| `-Wlogical-op-parentheses` и `-Wreorder-ctor`, tgnet, tgcalls, GIF | 64 | Добавить скобки по текущему порядку вычисления `&&` и `||`. Привести списки инициализации к порядку объявления полей. |
| `-Wpotentially-evaluated-expression`, `tgnet/ConnectionsManager.cpp` | 8 | Проверить выражения `typeid` и вынести вычисление операнда перед вызовом, сохранив динамический тип и семантику диагностики. |
| `-Wnull-arithmetic` и `-Wnull-conversion`, `gifvideo.cpp` | 18 | Для числового JNI-дескриптора `jlong` использовать `0`, для указателей — `nullptr`. Проверить возвраты на 32-разрядных ABI. |
| `-Wimplicit-const-int-float-conversion` и `-Wreturn-type-c-linkage`, `image.cpp` | 8 | Делать сравнение с `INT_MAX` без округления границы до `float`. При отсутствии внешних C-вызовов дать `gatherPositions` с результатом `std::vector` C++-линковку. |
| `-Wmacro-redefined`, `ffmpeg_jni.cc`, и `-Wunused-*`, несколько модулей | 40 | Оставить одно определение `__STDC_CONSTANT_MACROS`. Удалить лишние переменные, захваты и приватные поля после проверки платформенных веток и побочных эффектов инициализаторов. |
| CMake `No project() command is present` | 4 | Добавить явный верхнеуровневый `project(...)` после `cmake_minimum_required(...)`, затем переконфигурировать CMake для четырёх ABI. |

Строки `unique_ptr.h:601` и `CallCoreHost.cpp:601:64` относятся к одной инстанциации шаблона, а `rtc_event_log_factory.h:29` указывает место объявления устаревшего конструктора. Четыре его вызова дают по четыре предупреждения на ABI. Надписи `note`, `1 warning generated` и `arm64-v8a` в логе описывают контекст и итог вывода компилятора. Замена конструктора проверена на временных копиях всех четырёх файлов. Clang для `arm64-v8a` принял каждую копию с флагами проекта, `-fsyntax-only` и `-Werror=deprecated-declarations`.

<details>
<summary>Все сообщения Clang с координатами в jni/</summary>

Для повторов из одного заголовка указано число сообщений на ABI.

| Файл | Сообщение | ABI |
| --- | --- | --- |
| `ffmpeg_jni.cc:31:9` | '__STDC_CONSTANT_MACROS' macro redefined `-Wmacro-redefined` | все |
| `gifvideo.cpp:50:1` | typedef requires a name `-Wmissing-declarations` | все |
| `gifvideo.cpp:143:10` | format string is not a string literal (potentially insecure) `-Wformat-security` | все |
| `gifvideo.cpp:146:27` | unused function 'get_format' `-Wunused-function` | все |
| `gifvideo.cpp:152:37` | format specifies type 'int' but the argument has type 'const enum AVPixelFormat *' `-Wformat` | все |
| `gifvideo.cpp:416:86` | format specifies type 'long long' but the argument has type 'jlong' (aka 'long') `-Wformat` | `arm64-v8a`, `x86_64` |
| `gifvideo.cpp:643:13` | comparison between NULL and non-pointer ('jlong' (aka 'long long') and NULL) `-Wnull-arithmetic` | `armeabi-v7a`, `x86` |
| `gifvideo.cpp:643:13` | comparison between NULL and non-pointer ('jlong' (aka 'long') and NULL) `-Wnull-arithmetic` | `arm64-v8a`, `x86_64` |
| `gifvideo.cpp:643:16` | implicit conversion of NULL constant to 'jlong' (aka 'long long') `-Wnull-conversion` | `armeabi-v7a`, `x86` |
| `gifvideo.cpp:668:13` | comparison between NULL and non-pointer ('jlong' (aka 'long long') and NULL) `-Wnull-arithmetic` | `armeabi-v7a`, `x86` |
| `gifvideo.cpp:668:13` | comparison between NULL and non-pointer ('jlong' (aka 'long') and NULL) `-Wnull-arithmetic` | `arm64-v8a`, `x86_64` |
| `gifvideo.cpp:668:16` | implicit conversion of NULL constant to 'jlong' (aka 'long long') `-Wnull-conversion` | `armeabi-v7a`, `x86` |
| `gifvideo.cpp:676:13` | comparison between NULL and non-pointer ('jlong' (aka 'long long') and NULL) `-Wnull-arithmetic` | `armeabi-v7a`, `x86` |
| `gifvideo.cpp:676:13` | comparison between NULL and non-pointer ('jlong' (aka 'long') and NULL) `-Wnull-arithmetic` | `arm64-v8a`, `x86_64` |
| `gifvideo.cpp:676:16` | implicit conversion of NULL constant to 'jlong' (aka 'long long') `-Wnull-conversion` | `armeabi-v7a`, `x86` |
| `gifvideo.cpp:768:39` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `gifvideo.cpp:768:104` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `image.cpp:652:15` | variable length arrays in C++ are a Clang extension `-Wvla-cxx-extension` | все |
| `image.cpp:655:14` | variable length arrays in C++ are a Clang extension `-Wvla-cxx-extension` | все |
| `image.cpp:685:9` | variable 'yw' set but not used `-Wunused-but-set-variable` | все |
| `image.cpp:923:49` | implicit conversion from 'int' to 'float' changes value from 2147483647 to 2147483648 `-Wimplicit-const-int-float-conversion` | все |
| `image.cpp:1075:38` | 'gatherPositions' has C-linkage specified, but returns incomplete type 'std::vector<std::pair<float, float>>' which could be incompatible with C `-Wreturn-type-c-linkage` | все |
| `tgcalls/tgcalls/desktop_capturer/DesktopCaptureSourceHelper.cpp:258:35` | explicit capture of 'this' with a capture default of '=' is a C++20 extension `-Wc++20-extensions` | все |
| `tgcalls/tgcalls/desktop_capturer/DesktopCaptureSourceHelper.cpp:264:30` | explicit capture of 'this' with a capture default of '=' is a C++20 extension `-Wc++20-extensions` | все |
| `tgcalls/tgcalls/desktop_capturer/DesktopCaptureSourceHelper.cpp:338:41` | explicit capture of 'this' with a capture default of '=' is a C++20 extension `-Wc++20-extensions` | все |
| `tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp:550:11` | field '_e2eEncryptDecrypt' will be initialized after field '_userIds' `-Wreorder-ctor` | все |
| `tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp:692:39` | 'RtcEventLogFactory' is deprecated: Use default constructor `-Wdeprecated-declarations` | все |
| `tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp:769:101` | lambda capture 'opusPayloadType' is not required to be captured for this use `-Wunused-lambda-capture` | все |
| `tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp:2523:10` | private field '_disableAudioInput' is not used `-Wunused-private-field` | все |
| `tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp:2524:10` | private field '_enableSystemMute' is not used `-Wunused-private-field` | все |
| `tgcalls/tgcalls/group/GroupInstanceReferenceImpl.cpp:2531:9` | private field '_minOutgoingVideoBitrateKbit' is not used `-Wunused-private-field` | все |
| `tgcalls/tgcalls/v2/InstanceV2CompatImpl.cpp:354:5` | field '_videoCapture' will be initialized after field '_signalingTranslator' `-Wreorder-ctor` | все |
| `tgcalls/tgcalls/v2/InstanceV2CompatImpl.cpp:442:68` | 'RtcEventLogFactory' is deprecated: Use default constructor `-Wdeprecated-declarations` | все |
| `tgcalls/tgcalls/v2/InstanceV2ReferenceImpl.cpp:522:68` | 'RtcEventLogFactory' is deprecated: Use default constructor `-Wdeprecated-declarations` | все |
| `tgcalls/tgcalls/v2wasm/CallCoreHost.cpp:601:64` | 'RtcEventLogFactory' is deprecated: Use default constructor `-Wdeprecated-declarations` | все |
| `tgcalls/tgcalls/v2wasm/wasm_module_entry.cpp:16:16` | unknown attribute 'import_module' ignored `-Wunknown-attributes` | все |
| `tgcalls/tgcalls/v2wasm/wasm_module_entry.cpp:16:38` | unknown attribute 'import_name' ignored `-Wunknown-attributes` | все |
| `tgcalls/tgcalls/v2wasm/wasm_module_entry.cpp:28:16` | unknown attribute 'export_name' ignored `-Wunknown-attributes` | все |
| `tgcalls/tgcalls/v2wasm/wasm_module_entry.cpp:33:16` | unknown attribute 'export_name' ignored `-Wunknown-attributes` | все |
| `tgcalls/tgcalls/v2wasm/wasm_module_entry.cpp:38:16` | unknown attribute 'export_name' ignored `-Wunknown-attributes` | все |
| `tgcalls/tgcalls/v2wasm/wasm_module_entry.cpp:48:16` | unknown attribute 'export_name' ignored `-Wunknown-attributes` | все |
| `tgnet/Connection.cpp:216:93` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Connection.cpp:527:223` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Connection.cpp:660:57` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Connection.cpp:715:62` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Connection.cpp:715:147` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/ConnectionSocket.cpp:688:29` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/ConnectionsManager.cpp:997:215` | format specifies type 'unsigned long long' but the argument has type 'long' `-Wformat` | `armeabi-v7a`, `x86` |
| `tgnet/ConnectionsManager.cpp:1003:214` | format specifies type 'unsigned long long' but the argument has type 'long' `-Wformat` | `armeabi-v7a`, `x86` |
| `tgnet/ConnectionsManager.cpp:1264:127` | format specifies type 'long long' but the argument has type 'int64_t' (aka 'long') `-Wformat` | `arm64-v8a`, `x86_64` |
| `tgnet/ConnectionsManager.cpp:1268:132` | format specifies type 'long long' but the argument has type 'int64_t' (aka 'long') `-Wformat` | `arm64-v8a`, `x86_64` |
| `tgnet/ConnectionsManager.cpp:1268:143` | format specifies type 'long long' but the argument has type 'int64_t' (aka 'long') `-Wformat` | `arm64-v8a`, `x86_64` |
| `tgnet/ConnectionsManager.cpp:1974:101` | expression with side effects will be evaluated despite being used as an operand to 'typeid' `-Wpotentially-evaluated-expression` | все |
| `tgnet/ConnectionsManager.cpp:2050:52` | format specifies type 'long long' but the argument has type 'int64_t' (aka 'long') `-Wformat` | `arm64-v8a`, `x86_64` |
| `tgnet/ConnectionsManager.cpp:2228:5` | non-void lambda does not return a value in all control paths `-Wreturn-type` | все |
| `tgnet/ConnectionsManager.cpp:3127:155` | expression with side effects will be evaluated despite being used as an operand to 'typeid' `-Wpotentially-evaluated-expression` | все |
| `tgnet/ConnectionsManager.cpp:3352:43` | array subscript is of type 'char' `-Wchar-subscripts` | все |
| `tgnet/Datacenter.cpp:200:62` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Datacenter.cpp:258:62` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Datacenter.cpp:378:62` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Datacenter.cpp:447:62` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/NativeByteBuffer.cpp:17:12` | unused variable 'buffersCount' `-Wunused-variable` | все |
| `tgnet/Request.cpp:82:19` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `tgnet/Request.cpp:82:77` | '&&' within '\|\|' `-Wlogical-op-parentheses` | все |
| `twidget/twidget/core/src/renderer.h:239:25` | field 'x0' is uninitialized when used here `-Wuninitialized` | все |
| `twidget/twidget/core/src/renderer.h:239:38` | field 'y0' is uninitialized when used here `-Wuninitialized` | все |
| `voip/org_telegram_messenger_voip_Instance.cpp:407:88` | adding 'jint' (aka 'int') to a string does not append to the string `-Wstring-plus-int` | все |
| `voip/org_telegram_messenger_voip_Instance.cpp:557:34` | variable length arrays in C++ are a Clang extension `-Wvla-cxx-extension` | все |
| `voip/org_telegram_messenger_voip_Instance.cpp:558:38` | variable length arrays in C++ are a Clang extension `-Wvla-cxx-extension` | все |
| `voip/org_telegram_messenger_voip_Instance.cpp:559:39` | variable length arrays in C++ are a Clang extension `-Wvla-cxx-extension` | все |
| `voip/org_telegram_messenger_voip_Instance.cpp:608:30` | variable length arrays in C++ are a Clang extension `-Wvla-cxx-extension` | все |
| `voip/org_telegram_messenger_voip_Instance.cpp:1386:39` | format specifies type 'int' but the argument has type 'jlong' (aka 'long long') `-Wformat` | `armeabi-v7a`, `x86` |
| `voip/org_telegram_messenger_voip_Instance.cpp:1386:39` | format specifies type 'int' but the argument has type 'jlong' (aka 'long') `-Wformat` | `arm64-v8a`, `x86_64` |
| `voip/webrtc/modules/utility/source/jvm_android.cc:78:11` | unused variable 'jni' `-Wunused-variable` | все |
| `voip/webrtc/modules/utility/source/jvm_android.cc:82:10` | unused variable 'ret' `-Wunused-variable` | все |
| `voip/webrtc/video/rtp_video_stream_receiver2.h:393:7` | attribute guarded_by ignored, because it is not attached to a declaration `-Wignored-attributes` | все ×5 |
| `voip/webrtc/video/rtp_video_stream_receiver2.h:393:7` | declaration does not declare anything `-Wmissing-declarations` | все ×5 |
| `voip/webrtc/video/video_source_sink_controller.cc:40:55` | format specifies type 'long' but the argument has type 'rtc::VideoSourceInterface<VideoFrame> *' `-Wformat` | все |
| `voip/webrtc/video/video_source_sink_controller.cc:67:60` | format specifies type 'long' but the argument has type 'rtc::VideoSourceInterface<VideoFrame> *' `-Wformat` | все |

</details>

## Gradle, Android SDK и Java

| Сообщение | Как исправить |
| --- | --- |
| Gradle не находит каталог промежуточного проекта `:TMessagesProj_Modules:media3` | Привязать промежуточный `ProjectDescriptor` к существующему `TMessagesProj_Modules/media/libraries` в `settings.gradle` и проверить граф проектов. Gradle 9 требует существующий каталог. |
| Устаревшие присваивания Groovy DSL | Заменить распознанные как свойства `url`, `compileSdkVersion`, `namespace`, `minifyEnabled` и остальные указанные в приложении присваивания на `property = value`. Оставить вызовы методов, которые принимает DSL. Проверить все модули через `help --warning-mode=all` и сборку. Удаление старой формы запланировано на Gradle 10. |
| `Task.project` во время выполнения `checkVisibility`, `TMessagesProj/build.gradle:310,313,319` | Передать задаче вычисленные на этапе конфигурации параметры и `Provider` для пути. Проверить обычную сборку, ограничение `IS_PRIVATE` и HockeyApp-ветку. |
| `android.defaults.buildfeatures.buildconfig=true` | Включить `buildFeatures.buildConfig` в модулях, где генерируется или используется `BuildConfig`. Удалить глобальное свойство после проверки вариантов приложения, библиотек и тестов. |
| Экспериментальный `android.r8.optimizedResourceShrinking=true` | Проверить поведение флага на целевой версии AGP и сравнить release APK с сокращением ресурсов. Afat Debug не проверяет этот путь. |
| SDK XML версии 4 при поддержке до 3 | Согласовать SDK Command-line Tools и версию парсера Android Gradle Plugin. Сначала проверить, кто выводит сообщение при запуске на новом daemon, затем подобрать согласованные версии инструментов. |
| JDK 21 считает `source/target 8` устаревшими | Согласовать Java `sourceCompatibility/targetCompatibility` и Kotlin `jvmTarget` в соответствующих модулях. Можно скомпилировать модули, которым нужен Java 8 bytecode, подходящим toolchain, оставив Gradle на JDK 21. Для перехода на Java 17 проверить desugaring и запуск на minSdk 24. |
| `unknown enum constant When.MAYBE`, `Level.FULL`, `MigrationStatus.STRICT` | javac не видит `javax.annotation.meta.When`, `ReflectionSupport.Level` и `kotlin.annotations.jvm.MigrationStatus`. Подтверждено наличие классов в `com.google.code.findbugs:jsr305`, `com.google.j2objc:j2objc-annotations` и `org.jetbrains.kotlin:kotlin-annotations-jvm` соответственно. Уточнить источник аннотаций по classpath затронутых задач и выровнять транзитивные версии или добавить недостающие артефакты как `compileOnly`. |
| javac сообщает об устаревших API и unchecked-операциях | Запустить затронутые задачи с `-Xlint:deprecation` и `-Xlint:unchecked` для точных строк. Обновить поддерживаемые Android API с учётом minSdk. Исправлять потерю типов в исходниках приложения, Media3 и JLaTeXMath после проверки их контрактов. |
| Kotlin `Parameter 'layers' is never used`, `TelegramBuildAppPlugin.kt:117` | Заменить имя неиспользуемого параметра на `_` в `variant.sources.res?.all?.let` или упростить конструкцию после проверки обработки ресурсов. |

В повторной сборке javac выдал предупреждения о Java 8 в 14 задачах. По отсутствующим аннотациям зарегистрированы четыре сообщения `When.MAYBE`, три `Level.FULL` и три `MigrationStatus.STRICT`.

Справка по миграции и настройке компилятора. [Gradle 8.13](https://docs.gradle.org/8.13/userguide/upgrading_version_8.html), [документация AGP](https://developer.android.com/build/releases/gradle-plugin), [Android и JDK](https://developer.android.com/build/jdks).

<details>
<summary>Все присваивания Groovy DSL в журнале сборки</summary>

125 уникальных мест в 43 файлах, включая проекты, которые Gradle конфигурирует при сборке Afat Debug.

| Файл | Свойства и номера строк |
| --- | --- |
| `TMessagesProj/build.gradle` | `aidl` 167 · `coreLibraryDesugaringEnabled` 180 · `multiDexEnabled` 200, 216, 229, 242, 255, 268, 281 · `namespace` 294 · `ndkVersion` 156 · `shrinkResources` 215, 280 · `version` 204 |
| `TMessagesProj/lib/jlatexmath/jlatexmath/build.gradle` | `abortOnError` 19 · `namespace` 5 |
| `TMessagesProj_App/build.gradle` | `checkReleaseBuilds` 188 · `coreLibraryDesugaringEnabled` 43 · `multiDexEnabled` 74, 86, 97, 169 · `namespace` 185 · `ndkVersion` 167 · `shrinkResources` 73, 85, 96 · `signingConfig` 71, 83, 94 · `version` 175 |
| `TMessagesProj_AppHockeyApp/build.gradle` | `checkReleaseBuilds` 185 · `coreLibraryDesugaringEnabled` 53 · `multiDexEnabled` 84, 95, 106, 166 · `namespace` 182 · `ndkVersion` 164 · `shrinkResources` 85, 96, 107 · `signingConfig` 82, 93, 104 · `version` 172 |
| `TMessagesProj_AppHuawei/build.gradle` | `checkReleaseBuilds` 165 · `coreLibraryDesugaringEnabled` 50 · `multiDexEnabled` 81, 91, 146 · `namespace` 162 · `ndkVersion` 144 · `shrinkResources` 80, 90 · `signingConfig` 78, 88 · `url` 9 · `version` 152 |
| `TMessagesProj_AppStandalone/build.gradle` | `checkReleaseBuilds` 154 · `coreLibraryDesugaringEnabled` 43 · `multiDexEnabled` 73, 84, 135 · `namespace` 151 · `ndkVersion` 133 · `shrinkResources` 83 · `signingConfig` 71, 81 · `version` 141 |
| `TMessagesProj_AppTests/build.gradle` | `checkReleaseBuilds` 171 · `coreLibraryDesugaringEnabled` 61 · `multiDexEnabled` 89, 98, 149 · `namespace` 168 · `ndkVersion` 147 · `signingConfig` 87, 96 · `testBuildType` 49 · `version` 155 |
| `TMessagesProj_Modules/media/common_config.gradle` | `checkTestSources` 36 · `execution` 51 · `includeAndroidResources` 49 |
| `TMessagesProj_Modules/media/libraries/common/build.gradle` | `namespace` 29 |
| `TMessagesProj_Modules/media/libraries/common_ktx/build.gradle` | `namespace` 23 |
| `TMessagesProj_Modules/media/libraries/container/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/database/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/datasource/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/datasource_cronet/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/datasource_okhttp/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/datasource_rtmp/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_av1/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_ffmpeg/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_flac/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_iamf/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_mpegh/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_opus/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/decoder_vp9/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/effect/build.gradle` | `checkTestSources` 37 · `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/effect_lottie/build.gradle` | `namespace` 17 |
| `TMessagesProj_Modules/media/libraries/exoplayer/build.gradle` | `namespace` 22 |
| `TMessagesProj_Modules/media/libraries/exoplayer_dash/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/exoplayer_hls/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/exoplayer_ima/build.gradle` | `coreLibraryDesugaringEnabled` 20 · `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/exoplayer_rtsp/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/exoplayer_smoothstreaming/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/exoplayer_workmanager/build.gradle` | `namespace` 20 |
| `TMessagesProj_Modules/media/libraries/extractor/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/inspector/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/inspector_frame/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/muxer/build.gradle` | `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/session/build.gradle` | `aidl` 32 · `namespace` 18 |
| `TMessagesProj_Modules/media/libraries/test_data/build.gradle` | `namespace` 17 |
| `TMessagesProj_Modules/media/libraries/test_utils/build.gradle` | `namespace` 22 |
| `TMessagesProj_Modules/media/libraries/test_utils_robolectric/build.gradle` | `namespace` 19 |
| `TMessagesProj_Modules/media/libraries/transformer/build.gradle` | `checkTestSources` 32 · `namespace` 17 |
| `build.gradle` | `ignoreAssetsPattern` 56 · `url` 6, 48 |

</details>

## Строковые ресурсы Android

AAPT2 удалил 27 ресурсов `string` с предупреждением `without required default value`. Для каждого имени проверены исходные `values*/` в библиотеке и APK-модуле. Значения есть в отдельных локалях, а записи в `values/` отсутствуют.

| Строки | Где объявлены |
| --- | --- |
| `AddManyMembersAlertNamesText_few`, `AddManyMembersAlertNamesText_two`, `AddManyMembersAlertTitle_few`, `AddManyMembersAlertTitle_many`, `AddManyMembersAlertTitle_two`, `Languages_few`, `Languages_many`, `Languages_two` | `values-ar` |
| `Languages_zero` | `values-ar`, `values-ko`, `values-nl` |
| `EmailPasswordConfirmText3`, `PleaseEnterFirstPassword`, `PleaseEnterPassword`, `ResetAccountButton`, `ResetMyAccountText`, `YourEmailCode`, `YourEmailCodeInfo`, `YourPasswordSuccessText`, `formatterMonthName`, `formatterMonthYear2` | `values-ru`, `values-uk` |
| `NoContactsYet`, `NoContactsYetLine1`, `NoContactsYetLine2`, `NoContactsYetLine3`, `ShareTelegram` | `values-ko`, `values-pt-rBR` |
| `UseProxyForCalls`, `UseProxyForCallsInfo` | `values-ar`, `values-de`, `values-es`, `values-it`, `values-ko`, `values-nl`, `values-pt-rBR` |
| `WidgetPasscodeEnable2` | `values-uk` |

Для действующих ключей добавить корректные значения в `values/`. Для суффиксов форм множественного числа проверить, какое резервное значение ожидает `LocaleController`. Для устаревших ключей сверить upstream и ссылки из кода, динамические имена и генератор бинарной локализации перед удалением переводов. Затем повторить `processAfatDebugResources` и проверить ресурсы APK и переключение затронутых локалей. [Правила Android для значений по умолчанию](https://developer.android.com/guide/topics/resources/localization#failing2).

## Компиляция Android-тестов

`:TMessagesProj_AppTests:compileAfatDebugAndroidTestKotlin` завершилась успешно. Генератор схем перезаписывает отслеживаемые файлы, поэтому его служебные изменения после проверки восстановлены. Тесты на устройстве этим запуском не выполнялись.

- `BaseSchemeTest.kt:139,147,160` — три обращения к потенциально nullable результату `ThreadLocal.get()`. Задать проверяемый ненулевой инвариант (`requireNotNull(typeStack.get())` для `withInitial`) и проверить рекурсивный сценарий и очистку `finally`.
- `BaseSchemeTest.kt:154,175,232,235` — четыре использования `FixtureConfiguration.copy()` при непубличном конструкторе. Перевести создание конфигурации на публичный API библиотеки Fixture или совместимую версию библиотеки перед обновлением Kotlin до версии, где диагностика становится ошибкой.
- Генератор схем печатает сообщения об изменениях старых слоёв (удаления, слои без изменений) и `multiple candidates for TL_help_premiumPromo`. Для первых уточнить уровень логирования, если изменения исторических схем ожидаемы. Для неоднозначного преобразования проверить таблицу имён и выбирать конструктор по идентификатору TL или явному маппингу.

## Последующие исправления

- `failNotRunningRequest` выходит из `void`-задачи после отмены найденного запроса. Случай ненайденного запроса завершается обычным выходом из функции.
- Четыре вызова в подмодуле `tgcalls` используют `RtcEventLogFactory()`.
- Промежуточный проект Media3 привязан к `TMessagesProj_Modules/media/libraries`. Это существующий каталог дочерних библиотек. Для `checkVisibility` список задач, признак `IS_PRIVATE` и путь к APK HockeyApp определяются при конфигурации.
- В `buildSrc` явно обозначен неиспользуемый параметр. Тесты TL проверяют ненулевой стек `ThreadLocal` и создают варианты фикстур через `ConfigurationBuilder`.
- Для класса и свойства, определённых через reflection во время теста, Fixture 1.2.0 предоставляет перегрузку `property(KClass, String, ...)` с `DEPRECATION_ERROR`. Подавление ограничено этим вызовом. За счёт этого сохраняется выбор свойства по имени и не требуется `Configuration.copy()`.

Пять изменённых файлов C++ прошли `-fsyntax-only` для всех четырёх ABI с `-Werror=return-type` и `-Werror=deprecated-declarations`. `compileAfatDebugAndroidTestKotlin` прошла без предупреждений в `BaseSchemeTest`. `checkVisibility` при `IS_PRIVATE=true` отклонила запрос без разрешённой задачи и разрешила явную Debug-задачу. Сборка APK не запускалась.

## Base64URL, касания, нативные логи и строки ресурсов

- Base64URL индексирует таблицу через `unsigned char`, чтобы любой входной байт оставался в диапазоне таблицы. В логах tgnet теперь используются форматы фиксированных 64-битных типов, а ID запроса сохраняется целиком на 32-битных ABI.
- `Touch` в подмодуле `twidget` задаёт значения координат по умолчанию. При начале указателя агрегат по-прежнему получает координаты события, от которых рассчитывается первое движение.
- VoIP преобразует неизвестную константу экономии данных в текст. В логах VoIP JNI и GIF исправлены форматы 64-битных значений, а GIF-формат берётся из значения перечисления. WebRTC печатает адрес источника через `%p` и применяет `RTC_GUARDED_BY` к полю H.265-трекера.
- Для девяти используемых вариантов `Languages` и `AddManyMembersAlert*` добавлены английские значения на основе их `_other`-форм. Для остальных 18 ключей поиск по проекту нашёл только XML-объявления. Эти записи удалены из локалей.

Шесть единиц трансляции прошли Clang `-fsyntax-only` на четырёх ABI. Проверки включали форматы логов, индексацию через `char`, инициализацию и атрибуты. Анализ `-Wthread-safety` не выявил нарушений для `h265_tracker_`. Декодер Base64URL проверен на корректных строках и всех байтах `0x80`–`0xff` при знаковом и беззнаковом `char`.

`processAfatDebugResources` и `generateAfatDebugStringResourceIdsAsset` прошли успешно. Сообщений `without required default value` больше нет. Проверены все девять ID, английские значения и локальные формы `ar`, `ko` и `nl`. Удалённые ключи отсутствуют в бинарных локализациях. Проверки строк BYgram прошли для EN (495), RU (495) и UK (352). APK не собирался.

## Расширения C++ и присваивания Groovy DSL

- В `DesktopCaptureSourceHelper.cpp` три захвата переписаны с явными `this` и `guard`. В `image.cpp` и VoIP JNI шесть массивов переменной длины заменены на `std::vector`. При пустом списке VoIP передаёт в Java пустые массивы без вызова `Set*ArrayRegion`.
- Все три единицы трансляции прошли `-fsyntax-only` на четырёх ABI с соответствующими `-Werror=c++20-extensions` и `-Werror=vla-cxx-extension`. В проверке размытия совпали пиксели и баланс блокировок для 80 сценариев. Проверка передачи данных JNI прошла для пустых и заполненных массивов.
- В 43 файлах заменены все 125 присваиваний из журнала, включая изменения в подмодулях Media3 и JLaTeXMath. `:help` и `:TMessagesProj_App:processAfatDebugResources` прошли с `--warning-mode=all` без предупреждений об устаревшей форме присваивания. Сборка APK не запускалась.
