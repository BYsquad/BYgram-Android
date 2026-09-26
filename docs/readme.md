# Документация разработчика BYgram

## Android-клиент

Основа исходников синхронизирована с официальным Telegram commit `dc780e81ed1261c369c27870e8e0999a1eb0b600`. После `9552e5541e1274b9557c9832b204dbfcaf44b3dc` последовательно перенесены `2ad0f47d` (submodules), `c8480176` (исходники Telegram 12.10.4) и `dc780e81` (исходники Telegram 12.10.5). Версия Android-клиента BYgram: `APP_VERSION_NAME=12.10.5`, `APP_VERSION_CODE=7105`; основной package — `com.bygram.mobile`.

В `SettingsActivity` ID `24` сохранён за пунктом Telegram для настроек круглых видео. Разделу BYgram назначен постоянный `BYGRAM_SETTINGS_ID = 1000`.
Для flavor `afat` Gradle формирует итоговый versionCode `71059` из базового `7105` и ABI-кода `9`; это сохраняет возможность обновления поверх прежнего APK с кодом `70899`.

- [Инструкция по сборке](build.md)
- [Редакции Lite и Full и привязка устройств](full-edition-device-licensing.md)
- [Функции раздела «Настройки BYgram»](bygram-settings/README.md)
- [Кастомные статусы печати и значки](bygram-settings/customization.md)
- [Аудит неиспользуемого кастомного кода от 25.09.2026](UNUSED_CUSTOM_CODE_AUDIT_2026-09-25.md)
- [Обзор функций BYgram](BYGRAM_FEATURE_REVIEW.md)
- [Проверка ADB-исправлений](adb-fix-verification-2026-09-07.md)

Переключатели Material 3 рисуются в пределах выделенного им места; детали и проверка — в [документе по кастомизации](bygram-settings/customization.md).
Пункты «Жидкого стекла» используют выбранный в приложении язык. Устаревшая интеграция значка ExteraGram удалена из серверного кода и схемы новых установок.
Список чатов перестраивает имя после получения BYgram-значка из API. Профиль пересчитывает высоту области аватара, когда музыкальная карточка появляется при открытии или после загрузки данных пользователя. Регрессионная проверка: `python Tools/check_bygram_settings_fixes.py`.

## Сайт и релиз Lite от 25.09.2026

Публичные страницы `server/site`: главная, `features.html` с реальными скриншотами из эмулятора, `editions.html` со сравнением Lite/Full и `download.html`. Ссылка покупки ведёт через `server/activity-api/manager.php`, который при каждом переходе читает `app_settings.manager_username` и перенаправляет в Telegram. Не закрепляйте username менеджера в HTML.

Release Lite собран командой из [инструкции](build.md) без изменения `APP_VERSION_*`: APK `output/BYgram-lite-12.10.3.apk`, package `com.bygram.mobile`, versionName `12.10.3`, итоговый variant versionCode `70899`, размер `115657162` байт, SHA-256 `4EC5391C7FC5EBDDB74118DEAC8EB45A506465F7E9B1DA8E725F851A3B09118A`. Публичные данные релиза находятся в `server/activity-api/release.json`; APK публикуется рядом со страницей сайта. Скриншоты должны соответствовать версии сайта; страницы не описывают функцию BYxarek badge.

## Правила сопровождения

- Основной Android package: `com.bygram.mobile`; не добавляйте `.debug`, `.test` или `.beta` к `applicationId` приложения.
- `com.bygram.mobile.test` — отдельный тестовый package instrumentation APK.
- Не меняйте версию клиента без прямого запроса.
- Переводы BYgram ведутся в публичном репозитории [BYgram-translations](https://github.com/BYxarek/BYgram-translations). После проверки pull request импортируйте их в закрытый проект командой из [инструкции по сборке](build.md); сборка использует зафиксированные XML-файлы и не зависит от Crowdin.
- Не уменьшайте версии KotlinPoet `2.3.0`, credentials `1.6.0`, dynamicanimation `1.1`.
- После изменения функций обновите соответствующий документ и минимальную runnable-проверку.
- При публикации релиза обновите metadata и страницу скачивания APK.
