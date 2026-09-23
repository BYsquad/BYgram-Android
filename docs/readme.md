# Документация разработчика BYgram

## Android-клиент

Основа исходников синхронизирована с официальным Telegram commit `9552e5541e1274b9557c9832b204dbfcaf44b3dc`. Версия Android-клиента BYgram: `APP_VERSION_NAME=12.10.3`, `APP_VERSION_CODE=7089`; основной package — `com.bygram.mobile`.

- [Инструкция по сборке](build.md)
- [Редакции Lite и Full и привязка устройств](full-edition-device-licensing.md)
- [Функции раздела «Настройки BYgram»](bygram-settings/README.md)
- [Кастомные статусы печати и значки](bygram-settings/customization.md)
- [Аудит настроек от 16.09.2026](BYGRAM_SETTINGS_AUDIT_2026-09-16.md)
- [Обзор функций BYgram](BYGRAM_FEATURE_REVIEW.md)
- [Проверка ADB-исправлений](adb-fix-verification-2026-09-07.md)

## Правила сопровождения

- Основной Android package: `com.bygram.mobile`; не добавляйте `.debug`, `.test` или `.beta` к `applicationId` приложения.
- `com.bygram.mobile.test` — отдельный тестовый package instrumentation APK.
- Не меняйте версию клиента без прямого запроса.
- Переводы BYgram ведутся в публичном репозитории [BYgram-translations](https://github.com/BYxarek/BYgram-translations). После проверки pull request импортируйте их в закрытый проект командой из [инструкции по сборке](build.md); сборка использует зафиксированные XML-файлы и не зависит от Crowdin.
- Не уменьшайте версии KotlinPoet `2.3.0`, credentials `1.6.0`, dynamicanimation `1.1`.
- После изменения функций обновите соответствующий документ и минимальную runnable-проверку.
- При публикации релиза обновите metadata и страницу скачивания APK.
