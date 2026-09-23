# Настройки BYgram: документация разработчика

Раздел открывается из `SettingsActivity` и реализован в `BygramSettingsActivity`. Настройки/Spy history сосредоточены в `BygramConfig`; платная активность — в `BygramActivityController` и `server/activity-api`. Значки BYgram и ExteraGram из Android-клиента удалены.

## Карта экранов

```text
Настройки → Настройки BYgram
├─ Кастомизация
│  ├─ Профиль
│  └─ Общие
├─ Другое
├─ Режим призрака
├─ Шпион
├─ Кто с кем общается?
└─ Канал / Чат / Crowdin
```

## Документы

- [Конфигурация, ключи, экспорт и сброс](configuration.md)
- [Кастомизация интерфейса и профиля](customization.md)
- [Ghost Mode и Spy Mode](privacy-modes.md)
- [«Кто с кем общается?» и badges](activity-sharing.md)
- [Проверка изменений](testing.md)
- [Актуальный аудит](../BYGRAM_SETTINGS_AUDIT_2026-09-16.md)
- [Сборка проекта](../build.md)

## Главные исходники

| Назначение | Файл |
|---|---|
| Экран и вложенные screens | `TMessagesProj/src/main/java/org/telegram/ui/BygramSettingsActivity.java` |
| Ключи, defaults, Ghost/Spy helpers | `TMessagesProj/src/main/java/org/telegram/messenger/BygramConfig.java` |
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
