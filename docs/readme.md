# Документация BYgram Android

- [Сборка BYgram, проверка APK и переводы](build.md)
- [Карта настроек и описание кастомных функций](bygram-settings/README.md)
- [Пламя в личных чатах](bygram-settings/chat-flame.md)
- [Панель управления BYgram](admin-panel.md)
- [Проверки перед выпуском](bygram-settings/testing.md)
- [Совместимость с базой Telegram](development-notes.md)
- [Исправления базы Telegram Android от 24.11.2026](telegram-base-fixes-2026-11-24.md)
- [Обмен активностью и сессии](bygram-settings/activity-sharing.md)
- [Плагины Android, SDK и каталог](plugins/README.md)
- [История изменений](../changelog.md)

Клиент находится в `TMessagesProj`, основной APK собирается из `TMessagesProj_App`. Сервер активности и каталога находится в `server/activity-api`, публичные страницы — в `server/site`. Страница загрузки использует метаданные Debug APK из `server/activity-api/beta.json`. Страница покупки получает имя менеджера через `manager.php` из `app_settings.manager_username`.
