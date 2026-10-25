# Сборка BYgram на Windows

## 25.10.2026 — очистка верхней панели, v2

APK отправлен через `telegram-cli` в Telegram «Избранное»: сообщение `200679`,
25.10.2026 17:45:44 Europe/Moscow. Подтверждены `ok=true`, `out=true`,
имя `BYgram-settings-header-clear-v2-2026-10-25.apk` и размер 135824373 байта.
Журнал: `output/telegram-settings-header-clear-v2-2026-10-25.json`.

Первый APK ниже не устранил дефект на телефоне. Повторная правка отключает
пропуск `ActionBar.draw()` Android-контейнером без фона (`setWillNotDraw(false)`).
Дефект старого APK воспроизведён на эмуляторе API 36; новый APK на том же
маршруте убирает остаток аватара, меню и следы стрелок/заголовков после возврата
из «Кастомизации» и «Шпиона». Сохранены скриншоты `output/header-v1-*.png`
и `output/header-v2-*.png`. Crash buffer пуст.

Один итоговый Full Debug APK этой повторной задачи:
`output/BYgram-settings-header-clear-v2-2026-10-25.apk`, 135824373 байта.
SHA-256: `ce7c483d6a83f40fb01e78c2a90dfa5d85055959005c3e0da951b9710c3b4987`.
BUILD SUCCESSFUL за 7m 9s, 306 задач (14 выполнено), лог:
`output/build-settings-header-clear-v2-2026-10-25.log`.
Пакет `com.bygram.mobile`, 13.0.3 (71839), minSdk 23.
Подпись v1/v2 и прежний сертификат проверены (`output/header-v2-signature.txt`).
Бинарная локализация en/ru 303/303 и uk 163/163 — PASS.
Обновлённый JVM-тест проходит с правкой и падает на предыдущем коде;
проверки заголовков и 55 контрактов настроек — PASS.
APK установлен через `adb install -r` с сохранением данных.
Настоящий телефон не подключён. Существующие изменения рабочего дерева включены.

Повторные переходы проверены в светлой и тёмной теме; в обеих темах нет
остатков аватара/меню и наложения навигации. Тёмная тема проверена после
перезапуска приложения, скриншоты `output/header-v2-dark-*.png`.

## 25.10.2026 — очистка верхней панели настроек

Один итоговый Full Debug APK: `output/BYgram-settings-header-clear-2026-10-25.apk`.
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 6m 56s, 306 задач (16 выполнено). Лог:
`output/build-settings-header-clear-2026-10-25.log`.

Размер 135824373 байта; SHA-256
`7ea578a66c8cb41fb759b10ced864a97c589b9b97f1cff716feccc7069f283df`.
Пакет `com.bygram.mobile`, 13.0.3 (71839), minSdk 23, `BYGRAM_FULL=true`.
Подпись v1/v2 проверена, прежний сертификат SHA-256:
`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`.
Бинарная локализация en/ru 303/303 и uk 163/163 — PASS.
JVM-проверка очистки панели, проверки заголовков и 55 контрактов настроек — PASS.
ADB не обнаружил устройств: переходы на телефоне не проверены.
APK включает существующие изменения Android-кода рабочего дерева.
Описание изменения системного интерфейса:
[очистка верхней панели](bygram-settings/header-navigation-clear-2026-10-25.md).

APK отправлен через `telegram-cli` в Telegram «Избранное»: сообщение `200675`,
25.10.2026 17:25:19 Europe/Moscow. Подтверждены `ok=true`, `out=true`,
имя файла и размер 135824373 байта. Журнал:
`output/telegram-settings-header-clear-2026-10-25.json`.

## 25.10.2026 — стекло секции профиля и подложки заголовков

Один итоговый Full Debug APK: `output/BYgram-profile-glass-headers-2026-10-25.apk`.
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` — BUILD SUCCESSFUL
за 5m 34s, 306 задач (16 выполнено). Лог:
`output/build-profile-glass-headers-2026-10-25.log`.

Размер 135824373 байта; SHA-256
`0bb3ed3cb6daa8295e35d9aaa468f13667660bfbcc6860012ec9e6ae769e5d0b`.
Пакет `com.bygram.mobile`, версия 13.0.3 (71839), minSdk 23,
`BYGRAM_FULL=true`. Подпись v1/v2 проверена, сертификат прежний:
`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`.
Бинарная локализация en 303/303, ru 303/303, uk 163/163 — PASS.
Проверки profile_section_glass, page_headers, profile_scroll, settings_layout
и settings_fixes (55 контрактов) — PASS. ADB не обнаружил устройств,
визуальная проверка на телефоне не выполнена. Сборка включает текущие
незавершённые изменения рабочей папки.

APK отправлен через `telegram-cli` в Telegram «Избранное»: сообщение `200660`,
25.10.2026 01:18:32 Europe/Moscow. Подтверждены `ok=true`, `out=true`, имя файла
и размер 135824373 байта. Журнал: `output/telegram-profile-glass-headers-2026-10-25.json`.

## 24.10.2026 — release APK и публикация на сайте

Собран один итоговый release APK со всеми функциями BYgram:
`-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatRelease`.
BUILD SUCCESSFUL за 41m 52s, 341 задача (337 выполнено, 4 up-to-date).
Полностью пересобраны нативные release-зависимости четырёх ABI.
Лог: `output/build-release-site-2026-10-24.log`.

Файл: `output/BYgram-13.0.3.apk`, 76632613 байт (73,1 МиБ).
SHA-256: `53eaca16c2faecc64b707ce299fafbb5ce33bfe38050293eb27db38946fb2bdc`.
Пакет `com.bygram.mobile`, 13.0.3 (71839), Android 6.0+;
четыре ABI, `BYGRAM_FULL=true`, `DEBUG=false`, без `application-debuggable`.
Проверены подпись v2 и zipalign с параметром 16 КиБ.
Сертификат SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`
совпадает с прежним публичным APK 12.10.3, загруженным с сайта для сравнения.
Бинарная локализация: en 303/303, ru 303/303, uk 163/163 — PASS.
Полный отчёт: `output/release-site-2026-10-24/verification.txt`.

Пройдены runnable-проверки local_activity, settings_fixes (55 контрактов),
spy_deletions, profile_scroll, settings_layout, spy_retention (18 сценариев).
Release установлен через `adb install -r` поверх прежнего приложения на
`medium_phone`; открыты главная настроек BYgram и «Шпион», строки отображаются.
Журнал crash пуст. Скриншоты: `output/release-site-2026-10-24/*-smoke.png`.
Полный прогон функций на настоящем телефоне не выполнялся.

Опубликован: https://byxarek.site/TEST-PROJECT/BYgram/BYgram-13.0.3.apk
Обновлены страницы и `api/release.json`, включая размер и SHA-256.
На сайте удалены сведения о происхождении скриншотов, внутренний versionCode,
Package и предупреждение о прежнем APK. Fallback данных релиза использует тот же
`server/activity-api/release.json`, что и публикация. Новых APK после этого не собирали.
Загрузка через временное имя, сверка SHA-256 и переименование; затем страницы
и metadata. Все 9 файлов, включая APK целиком, повторно сверены через HTTPS.
Резервные копии: `output/release-site-2026-10-24/backup`;
манифест выкладки: `output/release-site-2026-10-24/deployed.json`.
Предыдущий публичный APK сохранён. API приложения и админка не изменялись.

APK отправлен через `telegram-cli` в Telegram «Избранное»: сообщение `200658`,
24.10.2026 21:24:59 Europe/Moscow. Подтверждены `ok=true`, `out=true`,
имя `BYgram-13.0.3.apk` и размер 76632613 байт. Журнал доставки:
`output/release-site-2026-10-24/telegram-delivery.json`.


## 24.10.2026 — локальная активность общих групп и видеообои

Финальный APK: `output/BYgram-local-activity-video-FINAL-debug.apk`,
130391617 байт, SHA-256
`a43bfb64095197203f6d69235601fb1067c134f6401e5e145cce706b9f73aeba`.
Full Debug — BUILD SUCCESSFUL за 8m 29s, 306 задач (28 выполнено).
Лог: `output/build-local-activity-video-final-2026-10-24.log`.
Подпись проверена, сертификат SHA-256 совпадает с предыдущими сборками:
`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`.
Бинарная локализация: en 303/303, ru 303/303, uk 163/163 — PASS.

Финальный APK доставлен в Telegram «Избранное»: message `200651`,
24.10.2026 20:12:00 Europe/Moscow. CLI подтвердил `ok=true`, `out=true`,
точное имя файла и размер 130391617 байт.
Журнал: `output/telegram-local-activity-video-final-delivery.jsonl`.

Промежуточный `output/BYgram-local-activity-video-intermediate-debug.apk`,
127328940 байт, SHA-256
`18fb5e3c4d916a44409ca14b26b57b4d0ee79d1e5cfbf4be49424a2a616819b2`.
Первая сборка — BUILD SUCCESSFUL за 8m 34s, 306 задач (32 выполнено).
Доставлен в Telegram «Избранное» по AGENTS.md: message `200650`,
24.10.2026 20:01:40 Europe/Moscow; CLI подтвердил `ok=true`, `out=true`,
имя файла и размер. Для установки использовать FINAL.

Поведение, проверки Java/iOS и ограничения:
[локальная активность и видеообои](bygram-settings/local-group-activity-and-video-wallpapers-2026-10-24.md).
ADB не видит устройств; на телефоне не запускалось. iOS не собран без Xcode.


## 24.10.2026 — переносы настроек и стекло профиля

`output/BYgram-profile-glass-FINAL-debug.apk`, 132279904 байт, SHA-256
`8b211e42206e835b35cdcca3b2610675e7fa0f2fa4a418a059257bca32def056`.
Full Debug — BUILD SUCCESSFUL за 5m 18s, 306 задач (16 выполнено).
Java-компиляция принудительно повторена после последней правки музыкальной
карточки; метод drawCardBackground присутствует в скомпилированном классе.
Лог: `output/build-profile-glass-settings-final-2026-10-24.log`.
Пакет `com.bygram.mobile`, 13.0.3 (71839), minSdk 23 / target 36;
`BYGRAM_FULL = true`, подпись v1/v2 и прежний сертификат — PASS.
Бинарная локализация en 300/300, ru 300/300, uk 160/160 — PASS.
Дополнительно 15 новых формулировок en/ru/uk найдены непосредственно
в бинарных языковых assets итогового APK.
Итоговый APK доставлен в Telegram «Избранное»: message `200645`,
24.10.2026 15:53:54 Europe/Moscow. CLI подтвердил `ok=true`, `out=true`,
имя файла и размер 132279904 байт.

Промежуточный `output/BYgram-profile-glass-intermediate-debug.apk`
(127326584 байт, SHA-256
`9580544ebeb63697a1959010c1489109658d33dd85c3c9789c96a8b2d5f7ea0f`)
также отправлен в Telegram «Избранное» по AGENTS.md: message `200644`,
24.10.2026 15:47:43 Europe/Moscow. Для установки использовать FINAL.
Первый проход: BUILD SUCCESSFUL за 5m 40s, 306 задач (28 выполнено),
`output/build-profile-glass-settings-2026-10-24.log`.

Проверки, каталог переводов и ограничения:
[отчёт](bygram-settings/settings-wrap-and-profile-glass-2026-10-24.md).
ADB не видит устройств; внешний вид на телефоне не проверен.

## 24.10.2026 — отключаемые проверки доступа

`output/BYgram-access-policy-full-debug.apk`, 135819516 байт, SHA-256
`ce1a86ee9e4287fc659590cf3b4abfb6488f2e2801bd3497e93b03071d1fff9e`.
Full Debug — BUILD SUCCESSFUL за 3m 52s, 306 задач (14 выполнено).
Лог: `output/build-access-policy-2026-10-24.log`. Пакет `com.bygram.mobile`,
13.0.3 (71839), minSdk 23 / target 36; подпись v1/v2 и прежний сертификат — PASS.
Бинарная локализация en 300/300, ru 300/300, uk 160/160 — PASS.

Доставлен в Telegram «Избранное»: message `200642`, 24.10.2026 15:13:44
Europe/Moscow; CLI подтвердил `ok=true`, `out=true`, имя и размер файла.
Android и iOS повторяют публикацию после регистрации без бота. Серверные флаги,
миграция, проверки и риск подмены ID:
[отчёт](bygram-settings/access-policy-flags-2026-10-24.md).
На устройстве не запускалось. iOS не собран без Xcode/Apple SDK.

## 24.10.2026 — стекло профиля, фон всей страницы и прокрутка

Итоговый Full Debug: `output/BYgram-profile-scroll-FINAL-full-debug.apk`,
130389081 байт. SHA-256:
`0ab132003ca82f8b08c6891a6aed94b2fb1de6c59b457d83f7afc57e22d35863`.
Пакет `com.bygram.mobile`, 13.0.3 (71839), minSdk 23 / target 36;
подпись v1/v2 — PASS, сертификат SHA-256
`a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`.
Бинарная локализация en 300/300, ru 300/300, uk 160/160 — PASS.
Финальная сборка — BUILD SUCCESSFUL за 5m 7s, 306 задач (14 выполнено).
Лог: `output/build-profile-scroll-delivery-2026-10-24.log`.
Окончательный APK доставлен в Telegram «Избранное»: message `200638`,
24.10.2026 14:44:07 Europe/Moscow; CLI подтвердил `ok=true`, `out=true`,
имя файла и размер 130389081 байт. Сертификат совпадает с предыдущим APK.

Проверки поведения, новых опций, сервера, ограничений окружения и обратного
включения лицензии: [подробный отчёт](bygram-settings/profile-scroll-and-free-full-2026-10-24.md).
На production обновлены только API bootstrap/index, с сохранением серверных
функций, отсутствующих в локальном HEAD. Платная лицензия временно не требуется.

Промежуточные APK отправлены в Telegram «Избранное» по AGENTS.md:
`BYgram-profile-scroll-intermediate-full-debug.apk` — message 200635;
`BYgram-profile-scroll-check-full-debug.apk` — message 200636;
`BYgram-profile-scroll-contrast-full-debug.apk` — message 200637.
Для второго CLI завершился ошибкой блокировки SQLite при обработке обновлений;
доставка подтверждена отдельным чтением по точному имени файла, повтор не выполнен.
Использовать окончательный файл с FINAL в имени.

ADB не видит устройств; визуальные сценарии и анимации не проверены.
iOS: исходники и XCTest добавлены, сборка и XCTest не запускались без Xcode.


## 24.10.2026 — фон профиля и уведомления Шпиона

Full Debug, `-PBYGRAM_EDITION=full :TMessagesProj_App:assembleAfatDebug` —
BUILD SUCCESSFUL, итоговый проход 5m 17s (306 задач: 14 выполнено, 292 up-to-date).
Файл: `output/BYgram-spy-background-FINAL-full-debug.apk`, 127325857 байт.
SHA-256: `5a112afa2ebe0e615025892b60ba8d29948f2cc013797bcea610a3b04f7826ec`.
Пакет `com.bygram.mobile`, версия `13.0.3` (`71839`), minSdk 23,
`BYGRAM_FULL = true`, подпись v1/v2 — PASS. Бинарная локализация:
en 297/297, ru 297/297, uk 157/157 — PASS.
Лог: `output/build-spy-background-complete-2026-10-24.log`.
Итоговый файл отправлен в Telegram «Избранное»: message `200605`,
24.10.2026 09:18:44 Europe/Moscow; CLI подтвердил `out=true`, имя файла
и размер 127325857 байт.

Предыдущие проверочные APK также отправлены в «Избранное» по AGENTS.md:

- `BYgram-spy-background-intermediate-full-debug.apk`, 128064820 байт,
  SHA-256 `5c026467d7f8af4a1aac4dd7b0713909324ccb618d16217f395bb717d2dcddaa`,
  Telegram message `200572`. Лог `output/build-spy-background-2026-10-24.log`,
  6m 57s, 32 выполненных задачи. Без последних правок push/возврата из background.
- `BYgram-spy-background-check-full-debug.apk`, 127325968 байт,
  SHA-256 `2e663c23d60595cf50c9a93a9ac73684c287f6857b8a548bc787350af8bb4a3a`,
  Telegram message `200587`. Лог `output/build-spy-background-final-2026-10-24.log`,
  6m 1s, 16 выполненных задач. Без последнего расширения уведомлений на исходящие
  сообщения, удалённые удалённо. Использовать итоговый файл с `FINAL` в имени.

Пройдены 18 JVM-сценариев хранения, тест реальных Java-решений обычного и
push-удаления, 55 контрактов настроек и проверка MD3/геометрии. Документация
поведения: [фон и уведомления](bygram-settings/spy-notifications-and-background-2026-10-24.md).
ADB не видит устройств; APK на устройстве не запускался. Для iOS добавлены
исходники и XCTest; сборка, Swift typecheck, XCTest и видео анимаций не выполнены
в Windows без Xcode/Apple SDK.


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
