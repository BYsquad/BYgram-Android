# Настройки BYgram для Android

Full: **Настройки → Настройки BYgram**. Точка входа — `SettingsActivity`, общая панель кастомных страниц — `BygramSettingsPage`. В Lite основного модуля этот раздел скрыт; добровольная отправка активности доступна отдельно через меню отладки.

```text
Настройки → Настройки BYgram
├─ Кастомизация (профиль, статусы набора, оформление)
├─ Плагины · БЕТА
├─ Режим призрака
├─ Шпион
├─ Система активности
├─ Скрытые чаты
├─ Запароленные чаты
├─ Просмотры профиля
├─ Другое (телеметрия, экспорт и сброс)
└─ Канал / Чат / Переводы
```

- [Конфигурация, экспорт и сброс](configuration.md)
- [Кастомизация интерфейса и профиля](customization.md)
- [Призрак, Шпион и защита чатов](privacy-modes.md)
- [Активность, просмотры профиля и привязка аккаунта](activity-sharing.md)
- [Ссылки на пункты настроек](links.md)
- [Плагины Android](../plugins/README.md)
- [Сборка](../build.md) и [проверки](testing.md)

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

Privacy-функции должны быть opt-in, не отправлять данные до действия пользователя и не логировать message text, viewer list или tokens. Пакеты редакций и параметры сборки описаны в [инструкции](../build.md#две-редакции-apk).

При изменениях актуализируйте эту директорию и `docs/readme.md`.
