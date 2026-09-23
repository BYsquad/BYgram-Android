# Кастомизация

## Общие

### Иконка приложения

`AppIconsSelectorCell` переключает manifest alias через `LauncherIconController`. Доступны `DEFAULT` (New), `VINTAGE` (Current), `AQUA` (Telegram). Это component state, а не ключ `BygramConfig`; reset явно вызывает `LauncherIconController.setIcon(DEFAULT)`.

### Фильтр Zalgo

`FILTER_ZALGO` включает `BygramTextUtils.filterZalgo()` из `Emoji.replaceEmoji()`. Алгоритм ограничивает combining marks и пересчитывает offsets/lengths `MessageEntity`.

Проверка после изменения:

```powershell
$checkDir = Join-Path $env:TEMP 'bygram-text-check'
New-Item -ItemType Directory -Force -Path $checkDir | Out-Null
javac -d $checkDir TMessagesProj/src/main/java/org/telegram/messenger/BygramTextUtils.java Tools/BygramTextCheck.java
java -cp $checkDir BygramTextCheck
```

### Material 3

| Ключ | Потребители | Граница |
|---|---|---|
| `MD3_LOADING` | `CircularProgressDrawable`, `RadialProgressView` | progress/loading indicators |
| `MD3_SLIDERS` | `SeekBarView` | только простой one-way slider без buffer/timestamps/min-value |
| `MD3_SWITCHES` | общий `Switch` | BYgram/Telegram switch component |

UI-группа раскрывается без отдельного persisted expanded state; счётчик показывает число включённых опций.

### Углы аватаров

`AVATAR_CORNERS`, 0…28 dp: 0 — square, 28 — circle. Значение ограничивается UI и применяется общим `ImageReceiver`, включая форумные avatars. Новый special avatar shape не должен обходить этот контракт без явного исключения в UI.

Обводка реакций под сообщениями также использует `BygramConfig.avatarRadius(height)`: при 0 она квадратная, при увеличении настройки её углы скругляются вместе с аватарами.

### Liquid Glass

`LIQUID_GLASS_PLACEMENTS` — маска из 8 битов. Для каждого флага нужен пункт и пример в `BygramLiquidGlassPlaces` и `BygramLiquidGlassExamples` во всех локализациях. Экран ограничивает обход длиной обоих массивов, чтобы неполный перевод не вызывал вылет. Consumer guards находятся в viewer, context panels, FAB, search, selection и popup components.

Новая область требует нового бита, строки/примера, consumer guard и обновления `GLASS_ALL`. Не переиспользуйте старый бит.

## Профиль

### Кастомный статус печати

В Lite и Full раздел «Кастомизация» содержит экран фраз для действий в личном чате: набор текста, запись голоса, отправка файла/фото/видео/аудио, запись и отправка видеосообщения и игра. Пустая фраза оставляет статус Telegram. Длина фразы ограничена 64 символами. Настройки хранятся отдельно для каждого Telegram account под ключами `bygram_custom_typing_<account>_<action>`, входят в экспорт и сброс.

Клиент публикует набор фраз через `typing_put` и читает фразы автора через `typing_get` только при наличии обычного Telegram typing action в личном диалоге. Поэтому обычный Telegram-клиент продолжает показывать стандартный статус, а BYgram подменяет его фразой. Сервер не получает список собеседников или текст сообщений. Результаты `typing_get` кэшируются на 7 секунд.

В Lite первую публикацию владелец подтверждает через ссылку бота (`typing_register` и `/start typing_...`). Бот проверяет Telegram ID, полученный от Telegram, и привязывает публичный ключ Android Keystore. После подтверждения пользователь повторно нажимает «Опубликовать статусы».

В Full уже привязанное устройство с активной лицензией публикует статусы сразу, используя тот же ключ Android Keystore: отдельное подтверждение статусов через бота не требуется. Сервер принимает ключ только пока устройство не отозвано и лицензия активна. Если Full ещё не активирован, остаётся обычная независимая привязка статусов через бота, доступная и в Lite. `typing_put` в обоих случаях проверяет подпись, timestamp и одноразовый nonce.

Перед развёртыванием PHP нужно применить `server/activity-api/migrations/20260923_typing_statuses.php` в контексте с настроенным `$pdo`; те же таблицы добавлены в `schema.sql` для новых установок.

### Значки рядом с именем

Админ панель выдаёт значок по Telegram ID и типу `user` или `chat` (канал, группа, супергруппа). Для чата принимаются положительный внутренний ID и Bot API ID с префиксом `-100` или `-`. Для пользователя используется `logo-new.png`, для ресурсов — `bygram-logo.png`. Публичный API `badges` возвращает назначенные ID, клиент кэширует результат 5 минут. Нажатие на значок открывает короткое уведомление внизу экрана. Старые записи пользовательских значков в `badges.json` читаются как BYgram-значок пользователя; выдача ExteraGram прекращена.

### Музыка профиля

`CUSTOM_PROFILE_MUSIC` управляет custom view в `ProfileActivity`/`ProfileMusicView`. Выключение скрывает представление и не удаляет media пользователя.

`getContentHeight()` уже возвращает пиксели. При расчёте высоты заголовка и положения панели действий повторно применять `dp()` к этому значению нельзя.

### ID и DC

`PROFILE_ID_MODE`:

- 0 — скрыто;
- 1 — Telegram API ID;
- 2 — Bot API ID (`-` для group, `-100` для supergroup/channel).

Mode применяется и при отображении, и при копировании. UI ограничивает индекс по размеру `BygramIdOptions`; новый mode требует обновить обе ветки профиля и массив строк.
