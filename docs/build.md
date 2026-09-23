# Сборка BYgram на Windows

Инструкция обновлена 23 сентября 2026 года; проверена компиляция `:TMessagesProj_App:compileAfatDebugJavaWithJavac` в PowerShell.

## Требования

- PowerShell 7.6.6.
- JDK 21 (`java -version`).
- Android SDK с Platform 36 и Build Tools 36.0.0.
- Android NDK `27.2.12479018`.
- CMake `3.22.1`.
- Gradle Wrapper `8.13` скачивает Gradle автоматически.
- Crowdin CLI с доступом к проекту.

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
- Перед каждой сборкой обязательно загрузите одобренные переводы.

## Gradle Wrapper в PowerShell

В корне нет `gradlew.bat`, а текущий `gradlew` имеет CRLF и не запускается через Bash. На Windows используйте проверенный прямой запуск wrapper JAR:

```powershell
$gradleMain = 'org.gradle.wrapper.GradleWrapperMain'
$gradleJar = '.\gradle\wrapper\gradle-wrapper.jar'
java -classpath $gradleJar $gradleMain --version
```

PATH менять не требуется.

## Обязательная загрузка переводов

Из корня `C:\project\BYgram-mobile`:

```powershell
crowdin download translations --export-only-approved --skip-untranslated-strings
```

Если Crowdin завершился с ошибкой, сборку не продолжайте: исправьте авторизацию/конфигурацию и повторите download.

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
$sdk = (Get-Content .\local.properties | Where-Object { $_ -like 'sdk.dir=*' }) -replace '^sdk.dir=', ''
$sdk = $sdk.Replace('\:', ':').Replace('\\', '\')
$apk = '.\output\BYgram-full.apk'
& (Join-Path $sdk 'build-tools\36.0.0\aapt.exe') dump badging $apk | Select-Object -First 3
& (Join-Path $sdk 'build-tools\36.0.0\apksigner.bat') verify --verbose --print-certs $apk
Get-FileHash -Algorithm SHA256 $apk
```

Проверьте:

- package ровно `com.bygram.mobile`;
- versionName/versionCode совпадают с разрешёнными значениями задачи;
- `sdkVersion` не ниже 23;
- `apksigner` завершился успешно;
- размер и SHA-256 записаны в release metadata.

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
