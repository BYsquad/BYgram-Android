# Обновление Android-базы Telegram до 13.0.3

Дата: 22.10.2026. Ветка: `c-r/telegram-latest-2026-10-22`.

Исходная база: `f32ffeec763f0bdbf4bec25b52d654f77bff733d`.
Целевая база: [`0aae959806c57be8ad457980f2a5c6277ff18635`](https://github.com/DrKLO/Telegram/commit/0aae959806c57be8ad457980f2a5c6277ff18635),
последний `master`, полученный непосредственно из официального репозитория
`https://github.com/DrKLO/Telegram.git` на момент обновления.

## История и сохранение локальных изменений

Существующие незакоммиченные изменения сохранены в `f375bcad5` до слияния.
Предыдущая интеграция `f32ffee` была перенесена итоговым diff и не связывала
истории. Отдельное слияние со стратегией `ours` фиксирует эту уже включённую
базу, сохраняя дерево BYgram. Затем выполнено обычное трёхстороннее слияние
полного `telegram/master`. История не переписана, промежуточные коммиты
и их родители сохранены, squash и выборочный cherry-pick не использовались.

Полный диапазон `f32ffee..0aae959806` — 14 коммитов, включая боковые ветки:

```text
422c316f61 fix gift reordering offset in profile gifts
b7c18acf82 Fix "Set as Main Tab" option showing for supergroups
b0047bfaaf Respect in-app browser settings when opening links from Instant View
14d8316c99 Fix system Back being ignored when a bot keyboard is persistent
ee39dcbfc3 Fix 'Send as file' option and sending via external share
c185e264ac Show EmailNotAllowed for EMAIL_NOT_ALLOWED on email code and 2FA screens
68c6775ac3 Merge pull request #2177
5dfd67c5d2 Merge pull request #2181
130d49fe93 Merge pull request #2182
8de8133da1 Merge pull request #2183
977c1d0189 Merge pull request #2185
e9808c1b3b Merge pull request #2191
1c84083351 update submodules
0aae959806 update to 13.0.3 (7183)
```

47 изменённых upstream-путей, 14 пересечений с BYgram. Единственный текстовый
конфликт — `gradle.properties`: приняты версия `13.0.3` и базовый versionCode
`7183`, сохранены package `com.bygram.mobile` и параметры подписи BYgram.
Разделение Lite/Full и ранее обновлённые зависимости сохранены.

Все подмодули сверены с gitlinks целевого дерева. Изменились:

- `TMessagesProj/jni/tgcalls`: `03d7b8662512af7528f476c02094862c1a37177d`;
- `TMessagesProj_Modules/media`: `09b6c97c0d691c50cd4541e1573995d4066d6f5c`.

## Совместимость настроек BYgram

Новый `MediaController.stopRecordingV2()` вызывал системный виброотклик
напрямую, обходя режим BYgram. Он подключён к существующему
`BygramConfig.hapticFeedback()` так же, как начало записи и legacy-рекордер.
Добавлен regression contract для всех трёх вызовов виброотклика записи.
Проверка ожидаемой версии в `check_bygram_md3_geometry.py` обновлена до 13.0.3.

Проверено сохранение каждой прежней строки интеграции `Bygram`/`bygram`
в Java-файлах, затронутых upstream. Обновлённые пути без кастомизаций
побайтно совпадают с upstream после нормализации CRLF. Сохранены новая
настройка аудиорекордера Telegram (ID 26) и вход BYgram (ID 1000).
Сериализация Spy/кошелька, Ghost, защита чатов, кастомные статусы,
геометрия профиля, иконки и отключение рекламы проверяются существующими
контрактами. Настройки и локальные базы пользователей не сбрасывались.

## Проверки

- `Tools/check_bygram_settings_fixes.py`: 55 contracts, PASS;
- `Tools/check_bygram_spy_retention.py`: 16 JVM-сценариев, PASS;
- `Tools/check_bygram_md3_geometry.py`: PASS;
- `Tools/check_bygram_profile_activity.py`: PASS;
- `Tools/check_bygram_no_ads.py`: PASS;
- `Tools/check_bygram_editions.py`: PASS;
- `Tools/BygramTextCheck.java`: PASS.
- `Tools/BygramMessageParamsCheck.java`: чтение прежнего Spy-флага,
  независимость состояния кошелька, reload/copy — PASS;
- `Tools/BygramEditMediaCheck.java`: фото, документы, InstantPage и повторное
  чтение snapshots — PASS. Как и до обновления, фоновая Android-очередь
  в JVM выводит `Looper.prepare: Stub!`; проверки TL завершаются с кодом 0.

## Сборка и APK

```powershell
java -classpath .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug
```

`BUILD SUCCESSFUL in 8m 45s`, 306 tasks (143 executed, 163 up-to-date).
Лог: `output/build-full-debug-telegram-13.0.3-2026-10-22.log`.
Готовый файл: `output/BYgram-full-debug.apk`, 122614017 байт.
SHA-256: `bfe10fabcaa3a354173eec8fb9553b10b9ef3529f435d0d445c21432a710b017`.

Подтверждены package `com.bygram.mobile`, versionName `13.0.3`,
versionCode `71839`, min SDK 23 / target SDK 36, debuggable и
`BYGRAM_FULL=true`. В APK присутствуют arm64-v8a, armeabi-v7a, x86, x86_64;
новые JNI-символы аудиорекордера экспортируются во всех четырёх библиотеках.
Подпись v1/v2 проверена, сертификат совпадает с предыдущим APK.
Бинарная локализация BYgram: en 273/273, ru 273/273, uk 129/129, PASS.

Остаются предупреждения upstream об устаревших Java 8/deprecated API,
локализованных ресурсах без default value и Gradle API. `apksigner`
предупреждает о служебных META-INF entries вне JAR-подписи; полная
проверка подписи APK v2 проходит. Независимая Release/R8 и Lite-сборка
в этом обновлении не запускались.

## Ограничения

ADB не обнаруживает подключённых Android-устройств. UI smoke tests,
сохранение переключателей после перезапуска, Ghost/Spy на двух аккаунтах,
серверные функции, звонки и реальная запись аудио требуют проверки
на устройстве по [testing.md](testing.md). Статические контракты и успешная
сборка не подтверждают эти пользовательские сценарии. APK не публикуется
на production-сервер в рамках этого обновления.
