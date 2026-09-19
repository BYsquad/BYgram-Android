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

### Liquid Glass

`LIQUID_GLASS_PLACEMENTS` — mask из 6 битов. UI и examples используют массивы одинаковой длины; consumer guards находятся в viewer, context panels, FAB, search, selection и popup components.

Новая область требует нового бита, строки/примера, consumer guard и обновления `GLASS_ALL`. Не переиспользуйте старый бит.

## Профиль

### Badges

Preview использует `StatusBadgeComponent.profileBadges()`. BYgram badges приходят batch-запросом к своему API; Extera badges — из публичного profiles endpoint. Локального toggle нет: состояние server-managed.

### Музыка профиля

`CUSTOM_PROFILE_MUSIC` управляет custom view в `ProfileActivity`/`ProfileMusicView`. Выключение скрывает представление и не удаляет media пользователя.

### ID и DC

`PROFILE_ID_MODE`:

- 0 — скрыто;
- 1 — Telegram API ID;
- 2 — Bot API ID (`-` для group, `-100` для supergroup/channel).

Mode применяется и при отображении, и при копировании. UI ограничивает индекс по размеру `BygramIdOptions`; новый mode требует обновить обе ветки профиля и массив строк.
