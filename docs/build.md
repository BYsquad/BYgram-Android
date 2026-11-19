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

Укажите SDK через `ANDROID_HOME` или `sdk.dir` в `local.properties`. Для подписи официальных сборок положите ключ в `TMessagesProj/config/release.keystore` и задайте `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` и `RELEASE_KEY_PASSWORD` в локальном `bygram-signing.properties` в корне. Оба файла игнорируются Git. Если локальные данные подписи отсутствуют, Debug использует стандартный отладочный ключ Android SDK.

Для входа в Telegram укажите собственные `TELEGRAM_API_ID` и `TELEGRAM_API_HASH` в `local.properties`. Для Huawei-варианта положите свой `agconnect-services.json` в `TMessagesProj_AppHuawei/`.

## Важные ограничения

- Для Telegram 13.2.2 и новее выполните `git submodule update --init --recursive` после обновления исходников. Вложенный QuickJS нужен для сборки `libtwidget.so`: он находится внутри подмодуля widget-sandbox.

- Не меняйте `APP_VERSION_CODE` и `APP_VERSION_NAME`, если задача отдельно этого не требует.
- `APP_PACKAGE` остаётся `com.bygram.mobile`.
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
## Сборка APK

Android-клиент BYgram использует пакет `com.bygram.mobile`, minSdk 24 и прежний ключ подписи. Плагины, Python и Firebase входят в основную сборку.

```powershell
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :TMessagesProj_App:assembleAfatDebug
```

Результат — `TMessagesProj_App/build/outputs/apk/afat/debug/BYgram-afatDebug.apk`. Если Python 3.11 отсутствует в PATH, укажите его исполняемый файл с помощью `-PBYGRAM_BUILD_PYTHON`.

Для релиза используйте задачу `:TMessagesProj_App:assembleAfatRelease`. Она создаёт `TMessagesProj_App/build/outputs/apk/afat/release/BYgram-afatRelease.apk`.

## Публикация бета-версии

После проверки Afat Debug загрузите один APK через «Бета-версия» в админке. При публикации по FTP загрузите APK с префиксом `BYgram-beta-` в корень сайта, затем обновите `api/beta.json`. Проверьте загрузку с публичной страницы и сравните SHA-256 скачанного файла с локальным.

18 ноября 2026 опубликована бета **13.2.2 (73049)**, пакет `com.bygram.mobile`, Android 7.0+, 183 703 722 байта. SHA-256 `8de8834746966e39a2e8288b0dc05a4b4ab0b5a750374e348cc5a4a2b21196c5`. Проверены подпись v2, выравнивание 16 КиБ, четыре ABI, локализации EN/RU/UK, установка поверх предыдущего APK на эмуляторе и публичное скачивание. Тот же файл доставлен в «Избранное» Telegram.

Бета **13.2.2 (73049)** от 19 ноября 2026 опубликована с переключателем Dev Server и входом через Mini App. SHA-256 `57fef8ba767617eb4afe36c9d7d003896b9dfee6684b641fcc6d868c742c8054`. Проверены подпись v2, выравнивание 16 КиБ, нативные хуки на x86 (API 28) и x86_64 (API 37), запуск Dev Server и отклонение неверного токена. Этот же APK доставлен в «Избранное».

## Проверка APK

```powershell
$sdk = $env:ANDROID_HOME
$apk = '.\output\BYgram-afatRelease.apk'
& (Join-Path $sdk 'build-tools\36.0.0\aapt.exe') dump badging $apk | Select-Object -First 3
& (Join-Path $sdk 'build-tools\36.0.0\apksigner.bat') verify --verbose --print-certs $apk
Get-FileHash -Algorithm SHA256 $apk
python Tools/check_bygram_localization_apk.py $apk
```

Проверьте:

- package — `com.bygram.mobile`;
- versionName/versionCode совпадают с разрешёнными значениями задачи;
- `sdkVersion` — 24;
- `apksigner` завершился успешно;
- размер и SHA-256 записаны в release metadata.
- все строки BYgram доступны через бинарную локализацию Telegram.

## Установка на тестовое устройство

```powershell
adb devices -l
android install --device=SERIAL --apks='C:\project\BYgram\BYgram-mobile\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-afatDebug.apk'
android run --device=SERIAL --apks='C:\project\BYgram\BYgram-mobile\TMessagesProj_App\build\outputs\apk\afat\debug\BYgram-afatDebug.apk'
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
