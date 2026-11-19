# Конфигурация, экспорт и сброс

`BygramConfig` хранит видимые параметры в `MessagesController.getGlobalMainSettings()`. Настройки активности имеют account suffix; access/refresh tokens и license cache находятся отдельно в SharedPreferences `bygram_licenses`.

## Основные ключи

Полный набор и значения по умолчанию определены в `BygramConfig`.

| Ключ | Тип | Default | Область |
|---|---|---:|---|
| `bygram_ghost_enabled` | boolean | false | global |
| `bygram_ghost_no_read` | boolean | true | global |
| `bygram_ghost_no_story_read` | boolean | true | global |
| `bygram_ghost_no_online` | boolean | true | global |
| `bygram_ghost_no_typing` | boolean | true | global |
| `bygram_ghost_read_on_actions` | boolean | false | global |
| `bygram_spy_save_deleted` | boolean | false | global |
| `bygram_spy_save_own_deleted` | boolean | false | global |
| `bygram_spy_notify_deleted` | boolean | false | global |
| `bygram_qr_glass_style` | boolean | false | global |
| `bygram_spy_save_edits` | boolean | false | global |
| `bygram_spy_save_read_date` | boolean | false | global |
| `bygram_spy_save_last_online` | boolean | false | global |
| `bygram_activity_enabled_<account>` | boolean | false | account |
| `bygram_activity_viewers_<account>` | JSON object | `{}` | account |
| `bygram_custom_profile_music` | boolean | false | global |
| `bygram_filter_zalgo` | boolean | false | global |
| `bygram_profile_id` | int 0…2 | 0 | global |
| `bygram_md3_loading` | boolean | false | global |
| `bygram_md3_sliders` | boolean | false | global |
| `bygram_md3_switches` | boolean | false | global |
| `bygram_avatar_corners` | int 0…28 | 28 | global |
| `bygram_firebase_crashlytics` | boolean | true | global |
| `bygram_firebase_analytics` | boolean | false | global |

`bygram_activity_enabled` — legacy-ключ. `clearRemovedCustomizationSettings()` один раз переносит его значение в account-specific ключи и удаляет исходный.

## Служебные данные

- `bygram_edit_<account>_<dialogId>_<messageId>` — JSON истории правок.
- `bygram_read_ranges_<account>_<dialogId>` и `bygram_read_baseline_*` — диапазоны дат прочтения.
- `bygram_online_<account>_<userId>` — замеченное время online.
- `bygram_licenses`: challenge, tokens, сроки и license cache по Telegram user ID.
- Признак сохранённого удалённого сообщения сериализуется в SQLite message data.

## Экспорт

Экран использует `ACTION_CREATE_DOCUMENT`, MIME `application/json`, имя `BYgram-settings.json`.

```json
{
  "app": "BYgram",
  "version": 1,
  "settings": {
    "bygram_filter_zalgo": true
  }
}
```

В экспорт попадает только allowlist из `isActiveSetting()`. История «Шпиона», разрешения на просмотр активности, пароли чатов, токены, значки и данные плагинов не экспортируются. Импорта нет.

## Сброс

`resetSettings()`:

1. удаляет активные UI-ключи и legacy Microsoft Translator key;
2. удаляет account-specific activity master/viewers;
3. запрашивает очистку текущей server activity и server viewers;
4. сбрасывает локальную защиту чатов, восстанавливает форматтеры времени и применяет настройки телеметрии по умолчанию;
5. запрашивает очистку опубликованных статусов набора и отключение просмотров профиля; UI возвращает иконку приложения к `DEFAULT`.

Telegram-аккаунты, access/refresh tokens и Spy message history сохраняются. Server cleanup является best effort: при отсутствии сети текущая activity исчезнет по TTL, а viewers будут перезаписаны при следующем включении функции.

## Добавление параметра

Одновременно обновляйте:

- константу и `isActiveSetting()`;
- default в `getBoolean()` или fallback/clamp для int;
- UI и единственный общий consumer guard;
- export/reset/migration scope;
- английские строки приложения и публичного каталога переводов, затем русский и другие переводы через pull request;
- один минимальный runnable regression check.
