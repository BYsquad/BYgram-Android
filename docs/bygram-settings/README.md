# Настройки BYgram: документация разработчика

Раздел открывается из `SettingsActivity` и реализован в `BygramSettingsActivity`. Настройки/Spy history сосредоточены в `BygramConfig`; платная активность — в `BygramActivityController` и `server/activity-api`. Кастомные статусы печати работают только в Full через `BygramTypingController`, значки — через `BygramBadgeController`.

## Карта экранов

```text
Настройки → Настройки BYgram
├─ Кастомизация
│  ├─ Профиль
│  ├─ Общие
│  └─ Кастомные статусы печати (Full)
├─ Режим призрака
├─ Шпион
├─ Система активности (Full)
├─ Скрытые чаты
├─ Запароленные чаты
├─ Другое
└─ Канал / Чат / Переводы (GitHub)
```

## Документы

- [Новый аудит iOS: дизайн настроек и текущие различия с Android, 16.10.2026](../../../BYgram-ios/docs/bygram-ios-design-and-feature-audit-2026-10-16.md)
- [Независимая повторная проверка переноса настроек Android → iOS, 16.10.2026](ios-settings-audit-2026-10-16.md)
- [Конфигурация, ключи, экспорт и сброс](configuration.md)
- [Кастомизация интерфейса и профиля](customization.md)
- [Ghost Mode и Spy Mode](privacy-modes.md)
- [«Кто с кем общается?» и badges](activity-sharing.md)
- [Примечания для разработчиков: флаги, ID и решения при обновлении](../development-notes.md)
- [Проверка изменений](testing.md)
- [Обновление Telegram до 13.0.2 и совместимость BYgram](telegram-13.0.2-upgrade.md)
- [Обновление Telegram до 13.0.3 с сохранением upstream-истории](telegram-13.0.3-upgrade.md)
- [Статус переноса в Windows-клиент](desktop-port-status.md)
- [Девять Windows-функций: реализация и непройденные тесты](desktop-nine-features.md)
- [Актуальный аудит](../BYGRAM_SETTINGS_AUDIT_2026-09-16.md)
- [Сборка проекта](../build.md)

## Главные исходники

| Назначение | Файл |
|---|---|
| Экран и вложенные screens | `TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java` |
| Ключи, defaults, Ghost/Spy helpers | `TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java` |
| Локальные скрытые и запароленные чаты | `TMessagesProj/src/main/java/org/telegram/messenger/BygramChatPrivacy.java` |
| Лицензия, activity, badges | `TMessagesProj/src/main/java/org/telegram/messenger/BygramActivityController.java` |
| Точка входа | `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` |
| Строки | `TMessagesProj/src/main/res/values/bygram_*.xml`, `values-ru/...` |
| Activity backend | `server/activity-api/index.php`, `bootstrap.php`, `schema.sql` |

## Контракт новой настройки

У каждой видимой настройки должны быть:

1. ключ/тип/default и правильный global/account scope;
2. UI и локализованная подпись;
3. реальный consumer через общий predicate/helper;
4. понятное поведение после restart;
5. решение для export/reset/migration;
6. минимальная regression check.

Privacy-функции должны быть opt-in, не отправлять данные до действия пользователя и не логировать message text, viewer list или tokens. Основной package всегда `com.bygram.mobile`; instrumentation package `com.bygram.mobile.test` является только тестовым package.

При изменениях актуализируйте эту директорию и `docs/readme.md`.
