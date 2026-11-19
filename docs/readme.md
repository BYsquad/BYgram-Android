# Документация BYgram Android

- [Сборка BYgram, проверка APK и переводы](build.md)
- [Карта настроек и описание кастомных функций](bygram-settings/README.md)
- [Пламя в личных чатах](bygram-settings/chat-flame.md)
- [Панель управления BYgram](admin-panel.md)
- [Проверки перед выпуском](bygram-settings/testing.md)
- [Совместимость с базой Telegram](development-notes.md)
- [Обмен активностью и сессии](bygram-settings/activity-sharing.md)
- [Плагины Android, SDK и каталог](plugins/README.md)
- [История изменений](../changelog.md)

Клиент находится в `TMessagesProj`, основной APK собирается из `TMessagesProj_App`. Сервер активности и каталога находится в `server/activity-api`, публичные страницы — в `server/site`. Для опубликованного APK используйте метаданные `server/activity-api/release.json`. Страница покупки получает имя менеджера через `manager.php` из `app_settings.manager_username`.
