# Сборка BYgram на Windows

23.10.2026, итоговый APK кастомизации: цветной профиль с белым именем получает
тёмную подложку фона; сохранены служебные якоря Spy, защита от устаревших ответов
и корректное смещение свёрнутого поиска. Full Debug
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 6m 21s (306 tasks: 16 executed, 290 up-to-date).
APK: `output/BYgram-customization-FINAL-full-debug.apk`, 122635249 байт;
SHA-256 `a6ad92b6303798c42e4702f097a3dfa6a108240d77dfa3ce758d8f6a02dadc51`.
Контейнер APK создан заново штатной задачей Gradle: прежний generated APK
предварительно удалён после сохранения предыдущих сборок в output; это исключает
рост ZIP при последовательной инкрементальной упаковке.
Подтверждены `com.bygram.mobile`, 13.0.3 (71839), прежний сертификат, подпись v1/v2,
локализация en/ru 295/295 и uk 155/155. 23 JVM-проверки кастомизации — PASS.
Лог: `output/build-customization-contrast-full-debug-2026-10-23.log`.
Итоговый APK отправлен через telegram-cli в «Избранное» 23.10.2026 в 22:56 МСК;
Telegram подтвердил сообщение `200556` и размер документа 122635249 байт.
Для установки использовать именно файл с пометкой FINAL; он заменяет две предыдущие сборки этой задачи.


23.10.2026: дополнительная сборка кастомизации со служебными якорями Spy,
защитой от устаревшего ответа фона и обновлением смещения поиска после настроек.
Full Debug `-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` —
BUILD SUCCESSFUL за 11m 10s (306 tasks: 14 executed, 292 up-to-date).
APK: `output/BYgram-customization-checked-full-debug.apk`, 137338647 байт;
SHA-256 `10e74dec461297b2163a0c15df68ef34b3956efebeafce397090a7c1a21f9c5a`.
Лог: `output/build-customization-final-full-debug-2026-10-23.log`.
APK отправлен через telegram-cli в «Избранное» 23.10.2026 в 22:51 МСК;
Telegram подтвердил сообщение `200551` и размер 137338647 байт.
Следующая сборка дополнительно исправляет подложку под белым именем цветного профиля.


23.10.2026: фон профиля (изображения/GIF) с общим сервером Android/iOS,
событие удаления чата с привязкой к последнему сохранённому сообщению,
постоянно свёрнутый поиск, предпросмотр заголовка/эмодзи, разделы кастомизации
и форма фона аватара обычного чата. Full Debug
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 8m 4s (306 tasks: 21 executed, 285 up-to-date).
APK: `output/BYgram-customization-full-debug.apk`, 132321217 байт;
SHA-256 `cad673add6111d56e54c56001876df1898e197622b6adebdd42cb4159f365c85`.
Подтверждены `com.bygram.mobile`, 13.0.3 (71839), прежний сертификат и подпись
v1/v2, локализация en/ru 295/295 и uk 155/155.
Сериализация Spy/кошелька/нового времени удаления, 79 ссылок, 16 retention-сценариев,
55 settings contracts, 23 JVM-граничных проверки кастомизации, заголовки/MD3 — PASS.
PHP self-test и реальный подписанный HTTP API на отдельной тестовой БД — PASS.
Production API и миграция обновлены, проверены 200/401/400 и байты файлов по FTP.
ADB devices пуст; iOS без Xcode на Windows не собирался и на устройстве не проверен.
Лог: `output/build-customization-full-debug-2026-10-23.log`.
APK этой сборки отправлен через telegram-cli в «Избранное» 23.10.2026 в 22:37 МСК;
Telegram подтвердил сообщение `200539` и размер 132321217 байт.
Подробности: [фон и история](bygram-settings/profile-backgrounds-and-history-2026-10-23.md).


23.10.2026: отдельный подпункт «Вибрация при звонке» и сверка каталога переводов.
Full Debug `-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` —
BUILD SUCCESSFUL за 7m 28s (306 tasks: 32 executed, 274 up-to-date).
APK: `output/BYgram-call-vibration-full-debug.apk`, 127302235 байт;
SHA-256 `fb18baa18e40266cf58834a55cf4dc9ab29c9220e3bb950a23d51cacf7c2983d`.
Подтверждены `com.bygram.mobile`, 13.0.3 (71839), подпись v1/v2 прежним
сертификатом, бинарная локализация en/ru 281/281 и uk 141/141.
Проверены 158 ссылок на BYgram-ресурсы и соответствие каталогу PR #1,
55 settings contracts, четыре сочетания переключателей вибрации и 13
Java-граничных сценариев кастомизации. Тексты нового переключателя и Ghost Mode
дополнительно прочитаны из бинарных ресурсов APK.
ADB devices пуст: на устройстве не проверено.
Лог: `output/build-call-vibration-translations-2026-10-23.log`.
APK отправлен через `telegram-cli` в «Избранное» 23.10.2026 в 16:37 МСК;
Telegram подтвердил сообщение `200436` и размер документа 127302235 байт.

23.10.2026: оформление верхней панели всех страниц настроек BYgram:
непрозрачная подложка с лёгким затемнением сверху, заголовки без стеклянной
плашки, на главной только кнопка возврата. Full Debug
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 6m 40s (306 tasks: 16 executed, 290 up-to-date).
APK: `output/BYgram-settings-header-full-debug.apk`, 127301467 байт;
SHA-256 `b92e430d98e1f21c5c4a8916c8aa341773c09693919336bbdbeb20fc2c84536d`.
Подтверждены `com.bygram.mobile`, 13.0.3 (71839), подпись v1/v2,
локализация en/ru 279/279 и uk 140/140. Проверки заголовков, MD3 и
55 settings contracts — PASS. ADB devices пуст: на устройстве не проверено.
Лог: `output/build-settings-header-2026-10-23.log`.
APK отправлен через `telegram-cli` в «Избранное» 23.10.2026 в 15:42 МСК;
Telegram подтвердил сообщение `200407` и размер 127301467 байт.

23.10.2026: обложка музыки профиля и четыре настройки кастомизации (свой номер,
относительный онлайн, свайп из архива, общий запрет вибрации).
Full Debug `-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` —
BUILD SUCCESSFUL, 7m 23s. APK: `output/BYgram-profile-customization-full-debug.apk`,
127301467 байт, SHA-256
`464c9f04ad2ca39eb692383ab61cefab63ebb0e0dd99a6bb8e6ea49ff297ab24`.
Пакет `com.bygram.mobile`, 13.0.3 (71839), подпись v1/v2 PASS,
локализация en/ru 279/279 и uk 140/140 PASS. Проверены 55 settings contracts,
профиль, MD3, 75 ссылок и 13 Java-граничных сценариев новой кастомизации.
Android-устройств через adb нет; визуальная/аппаратная проверка не выполнена.
Лог: `output/build-profile-customization-full-debug-2026-10-23.log`.
APK отправлен через `telegram-cli` в «Избранное» 23.10.2026 в 12:39 МСК;
Telegram подтвердил сообщение `200378` и размер документа 127301467 байт.
Отдельная предварительная проверка компиляции пересеклась с запуском assemble
и завершилась ошибкой упаковки промежуточных классов; итоговый assemble после
завершения первого процесса прошёл полностью.

23.10.2026: исправление пустых плашек скрытого заголовка и зазоров вокруг сторис.
Full Debug `-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` —
BUILD SUCCESSFUL за 6m 32s (306 tasks: 14 executed, 292 up-to-date).
`output/BYgram-full-debug.apk`: 130687950 байт,
SHA-256 `ac80930c09e32579134a00ee6f071915c5dbcd73d8244a257e5f93fd237f14b0`.
Подтверждены `com.bygram.mobile`, 13.0.3 (71839), подпись v1/v2 с прежним
сертификатом; бинарная локализация en/ru 274/274, uk 129/129 — PASS.
JVM-проверки заголовков, MD3, 55 contracts настроек и diff check — PASS.
На устройстве не запускался (ADB devices пуст).
Лог: `output/build-full-debug-header-gaps-2026-10-23.log`.
APK отправлен в Telegram «Избранное», сообщение `200377`.

23.10.2026: Full Debug с общим оформлением заголовков страниц отдельными
скруглёнными блоками Liquid Glass. Финальная сборка
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 5m 32s, 306 tasks (16 executed, 290 up-to-date).
`output/BYgram-full-debug.apk`: 130687961 байт,
SHA-256 `7b97181b243a12f537ed6233989b4b58d774bef682d8ddbc1e520289b4982c17`.
Подтверждены `com.bygram.mobile`, 13.0.3 (71839), Full/debuggable,
min 23 / target 36, четыре ABI, подпись v1/v2 (сертификат
`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`).
Бинарная локализация: en/ru 274/274, uk 129/129 — PASS.
Пройдены JVM-проверки фона и геометрии заголовков, MD3 и 55 contracts настроек.
На устройстве не запускался. Лог:
`output/build-full-debug-page-headers-final-2026-10-23.log`.
Описание: [заголовки](bygram-settings/customization.md#общий-стиль-заголовков-23102026).

22.10.2026, актуальная Full Debug: Telegram 13.0.3, upstream `0aae959806`,
вся история 14 коммитов после `f32ffee` включена через merge. Сборка
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 8m 45s. Готовый `output/BYgram-full-debug.apk`: 122614017 байт,
SHA-256 `bfe10fabcaa3a354173eec8fb9553b10b9ef3529f435d0d445c21432a710b017`.
Package `com.bygram.mobile`, 13.0.3 (71839), Full/debuggable, четыре ABI,
подпись v1/v2 и прежний сертификат подтверждены. Локализация en/ru/uk PASS.
Пройдены 55 contracts, 16 Spy-сценариев и JVM-проверки текстов, локальных
параметров и истории медиа. На устройстве не запускался.
Лог: `output/build-full-debug-telegram-13.0.3-2026-10-22.log`.
Подробности — [отчёт обновления](bygram-settings/telegram-13.0.3-upgrade.md).

18.10.2026, актуальная Full Debug: обводка выбранного аккаунта под форму аватаров, новые логотипы/Telegram icon и исправление сохранения своих сообщений при удалении всего чата. `:TMessagesProj_App:assembleAfatDebug -PBYGRAM_EDITION=full` — BUILD SUCCESSFUL; финальная упаковка после удаления конфликтующего Telegram vector — 23 s. `output/BYgram-full-debug.apk`: 128991783 байт, SHA-256 `aa0d2d88cbd625a57d6b242976c12e0897436b4851ad745d4a5b34624b7438d9`. Пакет `com.bygram.mobile`, 13.0.2 (71759), debuggable, min 23 / target 36, четыре ABI, `BYGRAM_FULL=true`. Подпись v1/v2 и en/ru/uk localization PASS; все три изображения внутри APK единственные и совпадают по пикселям с исходными ресурсами. Лог: `output/build-full-debug-branding-final-2026-10-18.log`. Проверки: MD3/ресурсы, 48 settings contracts, 16 Spy retention scenarios; визуальная проверка на устройстве не выполнялась.

Очистка 18.10.2026: удалены старые Lite APK, прежняя release-сборка с baseline profiles, устаревшие логи/дампы и JSON-метаданные сборки от 15.10 (22 файла, 445861865 байт). Сохранены актуальный `output/BYgram-full-debug.apk` с SHA-256 `a3ed80f8a219a02ae3139a02c049ffc9a730e45bfe6b0bcc468218fb505b7143`, его лог `output/build-full-debug-spy-whole-chat-2026-10-18.log` и кеш текущей сборки. Упомянутые ниже старые логи после очистки недоступны; исторические результаты проверок сохранены в документации.

Инструкция обновлена 23 сентября 2026 года; проверена компиляция `:TMessagesProj_App:compileAfatDebugJavaWithJavac` в PowerShell.

## Требования

- PowerShell 7.6.6.
- JDK 21 (`java -version`).
- Android SDK с Platform 36 и Build Tools 36.0.0.
- Android NDK `27.2.12479018`.
- CMake `3.22.1`.
- Gradle Wrapper `8.13` скачивает Gradle автоматически.
- Python 3 для проверки локализации и импорта из публичного каталога переводов.

Проверьте окружение:

```powershell
java -version
android info
android sdk list --all
```

`local.properties` должен содержать действующий `sdk.dir`. Release/debug signing этой конфигурации читает `TMessagesProj/config/release.keystore` и переменные `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` из `gradle.properties`. Не публикуйте их значения в документации и логах.

## Важные ограничения

- Не меняйте `APP_VERSION_CODE` и `APP_VERSION_NAME`, если задача отдельно этого не требует.
- `APP_PACKAGE` всегда должен быть `com.bygram.mobile`.
- Не уменьшайте KotlinPoet `2.3.0`, credentials `1.6.0`, dynamicanimation `1.1`.
- Сборка использует XML-переводы, зафиксированные в этом репозитории. Перед релизом импортируйте проверенный коммит из публичного [BYgram-translations](https://github.com/BYxarek/BYgram-translations).
- Генератор бинарной локализации должен включать `bygram_strings.xml` и `bygram_preferences.xml` для английского, русского и украинского языков. Иначе `LocaleController.getString(R.string.Bygram...)` показывает `LOC_ERR:null`, даже если текст есть в Android XML.

## Gradle Wrapper в PowerShell

В корне нет `gradlew.bat`, а текущий `gradlew` имеет CRLF и не запускается через Bash. На Windows используйте проверенный прямой запуск wrapper JAR:

```powershell
$gradleMain = 'org.gradle.wrapper.GradleWrapperMain'
$gradleJar = '.\gradle\wrapper\gradle-wrapper.jar'
java -classpath $gradleJar $gradleMain --version
```

PATH менять не требуется.

## Обновление переводов

После проверки и слияния pull request в публичный репозиторий получите его свежий `main` и из корня `C:\project\BYgram-mobile` выполните:

```powershell
gh repo clone BYxarek/BYgram-translations C:\project\BYgram-translations
python Tools/sync_bygram_translations.py C:\project\BYgram-translations
python Tools/sync_bygram_translations.py C:\project\BYgram-translations --check
```

Если каталог уже клонирован, перед импортом выполните `git -C C:\project\BYgram-translations pull --ff-only`. Скрипт читает только XML, проверяет имена ресурсов, форматные аргументы и длины массивов, затем копирует переводы в приложение. Он требует совпадения английских Android-ресурсов по именам, тексту и структуре; форматирование XML и строки `BygramDesktop*` не участвуют в сравнении. Строки Windows исключаются при импорте, остальные тексты и разметка сохраняются. После импорта проверьте diff и зафиксируйте его в закрытом репозитории. Обычная сборка не обращается к внешнему сервису переводов.

Полная проверка Android-ссылок и каталога: `python Tools/check_bygram_translation_catalog.py C:\project\BYgram-translations`.
23.10.2026 исправления отсутствующих текстов настроек и новый пункт вибрации звонков
подготовлены в [BYgram-translations PR #1](https://github.com/BYxarek/BYgram-translations/pull/1),
ветка `c-r/android-settings-call-vibration-2026-10-23`, коммит `cab944e6832002492b81e3952a097bf5e8f83758`.
Android-ресурсы синхронизированы с этой веткой; PR ещё не слит в `main`.
Украинский каталог настроек остаётся частичным с английским fallback, как указано в README каталога.

## Две редакции APK

Редакция задаётся обязательным для релизной сборки свойством `BYGRAM_EDITION`: `lite` или `full`. Если свойство не указано, безопасное значение по умолчанию — `lite`. Обе редакции сохраняют package `com.bygram.mobile`, версию и ключ подписи; установить их одновременно нельзя.

Debug Lite:

```powershell
$gradleMain = 'org.gradle.wrapper.GradleWrapperMain'
$gradleJar = '.\gradle\wrapper\gradle-wrapper.jar'
java -classpath $gradleJar $gradleMain -PBYGRAM_EDITION=lite :TMessagesProj_App:assembleAfatDebug
```

Результат:

```text
TMessagesProj_App/build/outputs/apk/afat/debug/BYgram-lite-afatDebug.apk
```

Debug Full:

```powershell
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug
Copy-Item .\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-full-afatDebug.apk .\output\BYgram-full-debug.apk
```

Перед передачей APK проверьте подпись, package/version и бинарную локализацию командами из раздела «Проверка APK». В сгенерированном `TMessagesProj/build/generated/source/buildConfig/debug/org/telegram/messenger/BuildConfig.java` после сборки должно быть `BYGRAM_FULL = true`.

15.10.2026 успешно собран Debug Full из `a2ab53df4`: `output/BYgram-full-debug.apk`, package `com.bygram.mobile`, версия `13.0.2` (`71759`), 120841071 байт. Подтверждены `BYGRAM_FULL = true`, debuggable, четыре ABI, подпись v1/v2 и доступность всех строк BYgram (en/ru/uk). SHA-256: `70fcb7041ef766600a8b1bc61baa41dedd077344d9e99b45b404c08983c67f3c`. На устройстве APK не запускался. Лог: `output/build-full-debug.log`.

17.10.2026 собран Debug Full из рабочего дерева с поддержкой медиа/InstantPage в истории правок и исправлением значка «Избранного»: `output/BYgram-full-debug.apk`, package `com.bygram.mobile`, версия `13.0.2` (`71759`), 126670277 байт. Подтверждены `BYGRAM_FULL = true`, debuggable, четыре ABI, подпись v1/v2 и бинарная локализация BYgram (en 269/269, ru 269/269, uk 124/124). SHA-256: `d2f6148e36eab9457e3cd45e199db8e81d55213d432da20e8f461d2643d4ac3e`. На устройстве APK не запускался. Лог: `output/build-full-debug-2026-10-17.log`.

## Release APK Lite и Full

18.10.2026 пересобран Full Debug с сохранением собственных сообщений при удалении всего чата независимо от отдельного переключателя: `output/BYgram-full-debug.apk`, package `com.bygram.mobile`, версия `13.0.2` (`71759`), 125932891 байт. Подтверждены Full, debuggable, четыре ABI, подпись v1/v2 и бинарная локализация (en 270/270, ru 270/270, uk 125/125). SHA-256: `a3ed80f8a219a02ae3139a02c049ffc9a730e45bfe6b0bcc468218fb505b7143`. Проверены 48 contracts и 16 JVM-сценариев хранения; на устройстве не запускался. Лог: `output/build-full-debug-spy-whole-chat-2026-10-18.log`.

18.10.2026 собран Full Debug из текущего рабочего дерева с исправлениями заголовка и Spy: `output/BYgram-full-debug.apk`, package `com.bygram.mobile`, версия `13.0.2` (`71759`), 125932643 байт. Подтверждены `BYGRAM_FULL = true`, debuggable, четыре ABI, подпись v1/v2 и бинарная локализация BYgram (en 270/270, ru 270/270, uk 125/125). SHA-256: `dd9ef660e00d5a96094f9f39036d84d34eb3f4eeb98030fdae259d2e1e0b9e24`. На устройстве APK не запускался. Лог: `output/build-full-debug-2026-10-18.log`.

```powershell
$gradleMain = 'org.gradle.wrapper.GradleWrapperMain'
$gradleJar = '.\gradle\wrapper\gradle-wrapper.jar'
java -classpath $gradleJar $gradleMain -PBYGRAM_EDITION=lite :TMessagesProj_App:assembleAfatRelease
Copy-Item .\TMessagesProj_App\build\outputs\apk\afat\release\BYgram-lite-afatRelease.apk .\output\BYgram-lite.apk

java -classpath $gradleJar $gradleMain -PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatRelease
Copy-Item .\TMessagesProj_App\build\outputs\apk\afat\release\BYgram-full-afatRelease.apk .\output\BYgram-full.apk
```

Результат:

```text
output/BYgram-lite.apk
output/BYgram-full.apk
```

Для standalone-варианта используйте только если он нужен задаче:

```powershell
java -classpath $gradleJar $gradleMain -PBYGRAM_EDITION=lite :TMessagesProj_AppStandalone:assembleAfatStandalone
```

Результат:

```text
TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app.apk
```

## Проверка APK

```powershell
$sdk = (Get-Content .\local.properties | Where-Object { $_ -like 'sdk.dir=*' }) -replace '^sdk.dir=', ''
$sdk = $sdk.Replace('\:', ':').Replace('\\', '\')
$apk = '.\output\BYgram-full.apk'
& (Join-Path $sdk 'build-tools\36.0.0\aapt.exe') dump badging $apk | Select-Object -First 3
& (Join-Path $sdk 'build-tools\36.0.0\apksigner.bat') verify --verbose --print-certs $apk
Get-FileHash -Algorithm SHA256 $apk
python Tools/check_bygram_localization_apk.py $apk
```

Проверьте:

- package ровно `com.bygram.mobile`;
- versionName/versionCode совпадают с разрешёнными значениями задачи;
- `sdkVersion` не ниже 23;
- `apksigner` завершился успешно;
- размер и SHA-256 записаны в release metadata.
- все строки BYgram доступны через бинарную локализацию Telegram.

## Установка на тестовое устройство

```powershell
adb devices -l
android install --device=SERIAL --apks='C:\project\BYgram-mobile\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-lite-afatDebug.apk'
android run --device=SERIAL --apks='C:\project\BYgram-mobile\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-lite-afatDebug.apk'
```

Не удаляйте установленное приложение ради downgrade: uninstall уничтожает локальные данные. Соберите версию с разрешённым versionCode или используйте отдельное чистое тестовое устройство.

## Перед публикацией

Сборка не равна публикации. Перед выкладкой релиза отдельно:

1. выполните smoke/integration checks;
2. переименуйте копию APK по принятой release-схеме;
3. обновите `server/activity-api/release.json` и страницу скачивания APK;
4. проверьте package/version/size/SHA-256 и ссылку скачивания;
5. только после этого загружайте файлы в согласованную server directory.

Не регистрируйте Windows Scheduled Task для сборки, публикации или имитации cron.

## Известные предупреждения

Текущая сборка успешна, но выводит предупреждения о Java 8 source/target под JDK 21, частично мигрированных AndroidX-зависимостях и отдельных локализованных ресурсах без default value. Они не блокируют APK, но должны отслеживаться отдельно; не скрывайте warnings без исправления причины.

17.10.2026 собран Debug Full с отключённой рекламой, новым логотипом и исправлениями профиля: `output/BYgram-full-debug.apk`, package `com.bygram.mobile`, версия `13.0.2` (`71759`), 128990237 байт. Подтверждены `BYGRAM_FULL = true`, подпись v1/v2 и бинарная локализация BYgram (en 269/269, ru 269/269, uk 124/124). SHA-256: `b971823b9e73ff17779cd7137be191a07e173a1272f4162d34e54d6f90b5f6a5`. На устройстве APK не запускался. Лог: `output/build-full-debug-profile-2026-10-17.log`.
