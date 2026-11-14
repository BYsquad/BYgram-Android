# Сборка BYgram на Windows

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

Укажите SDK через `ANDROID_HOME` или `sdk.dir` в `local.properties`. Release/debug signing этой конфигурации читает `TMessagesProj/config/release.keystore` и переменные `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` из `gradle.properties`. Не публикуйте их значения в документации и логах.

## Важные ограничения

- Для Telegram 13.2.2 и новее выполните `git submodule update --init --recursive` после обновления исходников. Вложенный QuickJS нужен для сборки `libtwidget.so`: он находится внутри подмодуля widget-sandbox.

- Не меняйте `APP_VERSION_CODE` и `APP_VERSION_NAME`, если задача отдельно этого не требует.
- `APP_PACKAGE` для Full остаётся `com.bygram.mobile`; Lite основного модуля переопределяет applicationId на `org.telegram.messenger`.
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

После проверки и слияния pull request в публичный репозиторий получите его свежий `main` и из корня `C:\project\BYgram\BYgram-mobile` выполните:

```powershell
gh repo clone BYxarek/BYgram-translations C:\project\BYgram-translations
python Tools/sync_bygram_translations.py C:\project\BYgram-translations
python Tools/sync_bygram_translations.py C:\project\BYgram-translations --check
```

Если каталог уже клонирован, перед импортом выполните `git -C C:\project\BYgram-translations pull --ff-only`. Скрипт читает только XML, проверяет имена ресурсов, форматные аргументы и длины массивов, затем копирует переводы в приложение. Он требует совпадения английских Android-ресурсов по именам, тексту и структуре; форматирование XML и строки `BygramDesktop*` не участвуют в сравнении. Строки Windows исключаются при импорте, остальные тексты и разметка сохраняются. После импорта проверьте diff и зафиксируйте его в закрытом репозитории. Обычная сборка не обращается к внешнему сервису переводов.

Полная проверка Android-ссылок и каталога: `python Tools/check_bygram_translation_catalog.py C:\project\BYgram-translations`.
## Две редакции APK

Редакция задаётся свойством `BYGRAM_EDITION`: `lite` или `full`; без него используется `lite`. Основной модуль `TMessagesProj_App` собирает Lite с пакетом `org.telegram.messenger`, именем Telegram и штатной иконкой. Full остаётся `com.bygram.mobile`; редакции используют одну версию и один ключ подписи в данной конфигурации. Эти редакции устанавливаются отдельно, но Lite конфликтует с официальным Telegram с тем же пакетом и другой подписью. Вспомогательные AppHuawei/AppStandalone не переводились на новый пакет. Firebase в Lite основного модуля отключён.

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

### Проверка Full Debug от 14.11.2026

Использована команда `:TMessagesProj_App:assembleAfatDebug` с `-PBYGRAM_EDITION=full` и `-PBYGRAM_BUILD_PYTHON`, указывающим на Python 3.11.18. Gradle использовал JVM 21. APK `BYgram-full-afatDebug.apk` содержит `com.bygram.mobile` 13.2.1 (72919), minSdk 24 и четыре ABI. Размер 180 619 591 байт, SHA-256 `4631aef5410b30b7302cb0870154df5c5799148ac2d85e9c1069f104ece77147`.

Прошли `check_plugins.py`, проверка Elyx под Python 3.11, подпись, `zipalign -c -P 16 4` и проверка 152 ELF64-библиотек. На эмуляторе Android API 37 x86_64 с размером страницы 4 КиБ прошли диагностический сценарий Full с ART-хуками и запуск основного Activity. Реальный arm64 и запуск на устройстве с размером страницы 16 КиБ в этом прогоне не проверялись.

APK отправлен в «Избранное» Telegram, сообщение 202357.

### Full Debug с Pillow и lxml от 14.11.2026

Повторный выпуск 13.2.1 (72919) по той же команде после доработки Dev Server и ART. Размер 191 360 351 байт, SHA-256 `628452fe223f04547068f0440aa0d9ad4862dde4eb2896adad28c8310ec0cba3`. Проверены подпись, `zipalign -c -P 16 4` и выравнивание 184 ELF64-библиотек. Диагностика прошла на Android API 37 x86_64 с размером страниц 4096 и 16384 байта, включая JPEG, FreeType, lxml и ART. Для 16 КиБ использован образ `emu64xa16k:CANARY/ZP11.260918.008/16518831`. На 4-КиБ эмуляторе пройдены проверка токена, несколько запросов по одному сокету, выдача сигнатуры ART, загрузка Python и изменение Elyx с проверкой хеша.

Физический arm64 и подключения из VS Code или PyCharm в этом прогоне не проверялись. [Релиз нативных wheels](https://github.com/BYxarek/chaquopy/releases/tag/bygram-python311-16k-v1)

APK отправлен в «Избранное» Telegram, сообщение 202358.

## Release APK Lite и Full

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
$sdk = $env:ANDROID_HOME
$apk = '.\output\BYgram-full.apk'
& (Join-Path $sdk 'build-tools\36.0.0\aapt.exe') dump badging $apk | Select-Object -First 3
& (Join-Path $sdk 'build-tools\36.0.0\apksigner.bat') verify --verbose --print-certs $apk
Get-FileHash -Algorithm SHA256 $apk
python Tools/check_bygram_localization_apk.py $apk
```

Проверьте:

- package соответствует редакции: Full — `com.bygram.mobile`, Lite основного модуля — `org.telegram.messenger`;
- versionName/versionCode совпадают с разрешёнными значениями задачи;
- `sdkVersion`: 24 для Full, 23 для Lite;
- `apksigner` завершился успешно;
- размер и SHA-256 записаны в release metadata.
- все строки BYgram доступны через бинарную локализацию Telegram.

## Установка на тестовое устройство

```powershell
adb devices -l
android install --device=SERIAL --apks='C:\project\BYgram\BYgram-mobile\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-lite-afatDebug.apk'
android run --device=SERIAL --apks='C:\project\BYgram\BYgram-mobile\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-lite-afatDebug.apk'
```

Не удаляйте установленное приложение ради downgrade: uninstall уничтожает локальные данные. Соберите версию с разрешённым versionCode или используйте отдельное чистое тестовое устройство.

В рамках задачи собирайте один итоговый APK после проверок и передавайте его в «Избранное» Telegram.

## Перед публикацией

Сборка не равна публикации. Перед выкладкой релиза отдельно:

1. выполните smoke/integration checks;
2. переименуйте копию APK по принятой release-схеме;
3. обновите `server/activity-api/release.json` и страницу скачивания APK;
4. проверьте package/version/size/SHA-256 и ссылку скачивания;
5. только после этого загружайте файлы в согласованную server directory.

Не регистрируйте Windows Scheduled Task для сборки, публикации или имитации cron.
