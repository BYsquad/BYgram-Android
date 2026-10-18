# Сборка BYgram на Windows

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

Если каталог уже клонирован, перед импортом выполните `git -C C:\project\BYgram-translations pull --ff-only`. Скрипт читает только XML, проверяет имена ресурсов, форматные аргументы и длины массивов, затем копирует переводы в приложение. Он требует, чтобы английские исходные XML-файлы в обоих репозиториях совпадали. После импорта проверьте diff и зафиксируйте его в закрытом репозитории. Обычная сборка не обращается к внешнему сервису переводов.

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
