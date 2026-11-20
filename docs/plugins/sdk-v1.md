# SDK v1 плагинов BYgram для Android

Доступные API и ограничения BYgram. См. [менеджер плагинов](README.md) и [проверки](testing-and-release.md).

## Установка и доверие

Настройки BYgram → Плагины → установить из файла. ZIP `.byplugin`
проходит ограниченную распаковку и проверку manifest без импорта Python.
Host показывает автора, capabilities, SHA-256 и fingerprint проверенной подписи содержимого, если она есть. Перед локальной установкой вне каталога требуется отдельное предупреждение и подтверждение полного доступа к клиенту; отмена удаляет staging.
Установка оставляет пакет выключенным. Движок и каждый hash включаются отдельно.
Автор в manifest не удостоверяет личность; Python/Java внутри UID клиента не
является sandbox.

Обновление становится pending и применяется при холодном старте выключенным;
требуется новое согласие. Предыдущий package и снимок global/account settings
доступны через «Откатить». Внешние действия, storage автора и отправленные
сообщения не откатываются. Удаление отдельно предлагает сохранить настройки.

## Сборка плагина без клонирования

Установите Python 3.10 или новее. Скачайте [`package_plugin.py`](https://raw.githubusercontent.com/BYsquad/BYgram-Android/9f4695b9616b7dcfef1449dd5f697c10311b8027/Tools/package_plugin.py)
и сохраните его рядом с папкой `my-plugin`. Поместите `manifest.json` в корень
папки плагина, а Python-файл расположите по пути из `entrypoint`, например `main.py`.

```powershell
python ./package_plugin.py ./my-plugin ./my-plugin.byplugin
```

Готовый пакет появится рядом с папкой плагина. Для подписи добавьте
`--signing-key ./author.pem`. Подпись требует OpenSSL и ключа вне папки плагина.

Если у вас уже есть копия репозитория BYgram, из её корня можно собрать
примеры SDK и запустить проверки репозитория.

```powershell
python Tools/package_plugin.py docs/plugins/examples/hello output/hello.byplugin
python Tools/package_plugin.py docs/plugins/examples/theme output/theme.byplugin
python Tools/package_plugin.py docs/plugins/examples/events output/events.byplugin
python Tools/package_plugin.py docs/plugins/examples/accounts output/accounts.byplugin
python Tools/package_plugin.py docs/plugins/examples/hooks output/hooks.byplugin
python Tools/check_plugins.py
```

Упаковщик не импортирует код. Он не заменяет host validation. Формат manifest:
[правила manifest](manifest-v1.schema.json).
Лимиты: ZIP 10 MiB, распакованное 30 MiB, 512 entries, файл 5 MiB,
manifest 64 KiB, путь 12 компонентов; PNG/WebP до 256 KiB и 256×256.
Не допускаются traversal, symlink, case collisions, duplicates, encryption,
повреждённый CRC и исполняемые бинарные файлы. `.py` проверяется как UTF-8.

## Контракт API

```python
from bygram import Plugin
from bygram.settings import Header, Text, Switch, TextInput, Select, Slider, Action
```

`Plugin` содержит no-op `on_load`, `on_enable`, `on_disable`, `on_unload`,
`create_settings`, `migrate_settings`. `context` подключён до `on_load`.
Management queue последовательно запускает lifecycle в отдельном daemon-потоке на вызов, ожидая не более 5 s. После timeout поколение блокируется до restart, поэтому зависшие вызовы разных плагинов не занимают общий пул. Timeout инвалидирует scope и требует restart этого плагина; очередь управления освобождается. Принудительного безопасного уничтожения авторского Python/Java thread нет. Зависший поток остаётся до завершения авторского кода или restart; повторное включение этого поколения запрещено. Constructor и top-level
модуля исполняются только после согласия. Namespace уникален для ID/generation;
relative imports приватны, общий `sys.path` не расширяется. Unload удаляет
namespace, но не отменяет произвольные изменения Java/threads автора.

`context`:

| API | Реализованный контракт |
|---|---|
| `plugin_id`, `version`, `generation` | Идентичность загруженного экземпляра |
| `log.info/warning/error(text)` | Только локальный журнал; до 4000 символов на запись |
| `settings.get(key, default)` / `set(key, value)` | Global JSON, атомарная запись, лимиты; сериализуемые JSON значения |
| `account_settings(slot)` | Отдельный store по slot/user ID; нужен selected_accounts и явный выбор владельцем |
| `storage.read_text/read_asset/write_text` | Проверка canonical containment; чтение до 5 MiB, запись до 1 MiB |
| `ui.run(callback)` | Асинхронная очередь callbacks плагина, deadline 5 s, отмена при disable; UI-операции выполняет host |
| `ui.bulletin(text)` | Текущая native страница, до 1000 символов |
| `ui.alert(options, on_click=None, view=None)` | Нативный диалог, список и кнопки, опциональный Android View. Возвращает handle с `close` и `setProgress` |
| `ui.add_menu(location, id, title, on_click)` | Owned handle, уникальный `(plugin_id, id)`; callback получает `event.account` |
| `ui.open_page({title, items})` | Native страница, закрывается host при disable |
| `theme.override(key, color)` | Owned cached Java override; не перезаписывает тему пользователя |
| `events.subscribe(name, callback, account=None)` | Owned handle; DTO, без mutable TLRPC |
| `work.submit(callback)` | До двух worker threads на экземпляр плагина, отдельная очередь 32; handle отменяет queued работу |
| `client(slot).send_text(peer, text)` | Explicit identity, capability messages.send, 1–4096 символов, штатный SendMessagesHelper, без собственного retry |
| `client(slot).send_request(request, callback)` | TLObject и `callback(response, error)`, capability telegram.requests. Возвращает токен запроса. Host проверяет identity на очереди отправки и при доставке ответа |
| `client(slot).send_media(peer, kind, path, caption="")` | `photo`, `document`, `video` или `audio` из каталога пакета или данных. Capability messages.media. Перед отправкой повторно проверяются аккаунт и диалог |
| `client(slot).edit_text(message, text)` | Редактирование текста сообщения через штатный редактор. Нужны `MessageObject` выбранного аккаунта и capability messages.media |
| `client(slot).draft_text(peer)` | Host читает main thread, worker ждёт до 1 s; ui + messages.observe, выбранный аккаунт; plain text только текущего обычного чата |
| `client(slot).preview_text(peer, text)` | ui + messages.send; до 4096 символов, native-предпросмотр/отмена, повторная проверка identity/чата/черновика; заменяет текст только по нажатию владельца |
| `network.get_json(https_url)` | Capability network; запрещён main thread; timeout 10 s, ответ до 1 MiB, без credentials в URL |
| `hooks.method/constructor/replace(...)` | Experimental dispatcher; точные manifest targets/build ranges, availability gate и native probe |

`Handle.close()` идемпотентен для SDK handles; host также освобождает их в
обратном порядке при disable/failed enable. После invalidation старое поколение
не может использовать SDK. Host ждёт in-flight callbacks до 2 s на management
queue; зависание требует restart и блокирует повторное включение до restart.
Произвольный Python/Java thread безопасно принудительно остановить нельзя.
Три runtime callback ошибки за 60 s выключают плагин. Ошибка enable выключает
сразу. Длинные callbacks журналируются; VM/native падения не маскируются.

Синхронные settings/storage операции могут выполнять файловый I/O. Вызывать их
на management/background queue; `ui.run` ставит callback в отдельную последовательную очередь; произвольный Python больше не исполняется host на UI-потоке. Смена этого контракта требует переноса прямых View/Java UI действий автором; SDK UI helpers сами переходят на main thread.
Generation проверяется перед SDK действиями; Python/Java в обход SDK доверенный.

## Native страницы и настройки

`create_settings()` возвращает список descriptors. Он вызывается только у
ACTIVE плагина. Для выключенного плагина код ради настроек не загружается.
Поддержаны Header, Text, Switch, TextInput, Select (`options=[{title,value}]`),
Slider (числовой диалог с min/max/step), Action (callback). До 100 элементов;
проверки типов/defaults/limits применяются host и при `settings.set` после
регистрации схемы. Renderer редактирует global store и поля `scope="account"`: выбор аккаунта
ограничен выданными владельцем актуальными identity. Logout лишает поле доступа.

Миграция получает копию JSON и возвращает dict. `context.migration_scope`
содержит имя scope; client API и другие SDK действия во время миграции запрещены.
Сначала рассчитываются все новые значения, затем пишется durable backup journal.
При ошибке/незавершённой записи следующий старт восстанавливает исходные JSON.
Миграция не выполняется при установке или без согласия на новый код.

Доступные точки меню включают `dialogs.toolbar` в списке чатов, `profile.actions`, `chat.actions` и `message.context`. Последняя добавляет пункт «Плагины» в контекстное меню сообщения для выбранного аккаунта и требует `ui` и `messages.observe` или `messages.send`. Нативный SDK передаёт `account`, `dialog_id`, `message_id` и `topic_id`. Скрытые, защищённые и секретные диалоги исключены. Адаптер exteraGram передаёт также объект сообщения и данные альбома. Host обрабатывает клик на main thread, авторский callback исполняется в отдельной последовательной очереди. [API совместимости](exteragram-port.md)

Theme keys — имена из `ThemeColors` Telegram, например `windowBackgroundWhite`,
`windowBackgroundGray`, `actionBarDefault`, `actionBarDefaultTitle`. Неизвестное имя
отклоняется. Цвет: signed/unsigned 32-bit ARGB int, `#aarrggbb` или непрозрачный `#rrggbb`.
В getter нет Python: только lookup готового Java map. При изменении overrides host обновляет общие Telegram paints, ThemeDescription всех известных открытых fragment, инвалидирует их View и очищает recycled pools; при resume применяется пропущенная ревизия. Черновики и navigation stack не пересоздаются. Занятый другим плагином key возвращает
ContributionConflict; владелец может отключить конфликтующий плагин.
Авторские custom Views должны объявлять ThemeDescription или обновлять свои цвета по `theme.changed`; host не может определить назначение произвольного цвета, закодированного автором вручную.

Изменение `context.settings.set` обновляет открытую native-страницу, включая Action callbacks.
Account-поля повторно проверяют grants при возобновлении страницы и account-событиях.

## События и аккаунты

`context.accounts` — read-only tuple номеров разрешённых и ещё авторизованных
слотов; для global-плагина пуст. Он не раскрывает аккаунты без grant. Logout
может доставить старую identity, новый вход автоматически доступа не получает.

- `ui.page_opened/closed`: `{page, account}`, main thread, native resume/pause.
  Стабильные значения `page`: `ChatActivity`, `DialogsActivity`, `ProfileActivity`,
  `SettingsActivity`, `PluginPage`, `BygramPluginsActivity`, `Other`; одинаковы после R8.
- `theme.changed`: main thread, изменение plugin override.
- `account.added/logged_out`: `{account,user_id,identity}`, main thread, только
  `selected_accounts` и точно выданная identity; глобальный плагин не получает список входов.
  Переходы UserConfig передаются сразу на main thread; resume дополнительно сверяет список.
- `messages.received`: нужен `account=slot`, messages.observe и выбранный account;
  `{account,user_id,dialog_id,message_ids,scheduled,dropped}`, main thread,
  максимум 50 ID на host batch. Текст/контент не передаются.
- `network.completed`: capability network, `{plugin_id,bytes}`, поток network caller;
  только SDK helper и подписчики того же плагина/поколения, без URL/headers/payload и без перехвата Telegram network.

Каждый account вызов, включая чтение из cache settings, заново сверяет user ID
и UUID поколения авторизации из UserConfig. UUID меняется при каждом auth reset,
включая повторный вход тем же пользователем. Logout/new user в старом slot
не наследует доступ. Новый аккаунт сам не добавляется к разрешённым.
Отложенное событие от прежней авторизации отбрасывается без ошибки callback
и без увеличения счётчика карантина. Событие logout доставляется только
подписке соответствующей identity.
Скрытые/защищённые диалоги исключены из SDK observe/send независимо от PIN;
обход через raw Java не становится безопасным из-за этой проверки.

Действие `chat.actions`/`profile.actions` получает `dialog_id`, только если
владелец выдал актуальный account grant, плагин имеет messages.observe/send
и диалог не скрыт/защищён. В остальных случаях event содержит только account.
Пример `examples/composer` преобразует черновик в верхний регистр через меню
чата. Предпросмотр не отправляет сообщение и не меняет получателя. Редактирование
отправленного сообщения, caption, особые режимы чата и подготовленное медиа
исключены. Применяемый результат — plain text; spans/entities исходного
черновика не переносятся автоматически. Смена чата, logout/relogin, отзыв
grant, disable или изменение текста до подтверждения отменяют применение.

## Hooks и восстановление

### Перехваты операций Telegram

`context.telegram.intercept(kind, callback, name="", match_substring=False, priority=0)` возвращает handle. Нужны `hooks.telegram`, `account_scope: "selected_accounts"` и выбор аккаунтов владельцем. Перехваты работают после успешной активации пакета и используют штатные точки клиента без зависимости от native ART backend.

| `kind` | Операция и объект |
| --- | --- |
| `request` | TL-запрос перед сериализацией |
| `response` | Ответ на Java-запрос перед обработчиком. `detail` содержит Telegram-ошибку при наличии |
| `updates` | Пакет обновлений при первом входе в обработчик, до постановки в очередь |
| `update` | Отдельное обновление на входе в `processUpdateArray` |
| `send` | `SendMessageParams` перед обработкой штатной отправки |

`callback(name, account, value, detail)` получает короткое Java-имя операции. Для ответа используется имя запроса. Пустой фильтр охватывает все имена данного типа. Обработчики идут по убыванию приоритета, затем по ID плагина и порядку регистрации. `None` и `HookResult()` продолжают цепочку. `CANCEL` прерывает операцию, `MODIFY` передаёт новый объект дальше, `MODIFY_FINAL` завершает цепочку с новым объектом. Для отмены запроса существующий обработчик получает ошибку `PLUGIN_CANCELLED`. Отмена отдельного update убирает его из массива обработки.

```python
from bygram import Plugin
from bygram.telegram import HookResult, HookStrategy

class Uppercase(Plugin):
    def on_enable(self):
        self.context.telegram.intercept("send", self.uppercase)

    def uppercase(self, name, account, params, detail):
        message = str(params.message) if params.message is not None else ""
        if message.startswith("/upper "):
            params.message = message[7:].upper()
            return HookResult(HookStrategy.MODIFY_FINAL, params)
```

Обработчик выполняется синхронно в потоке операции. Рекурсивная операция на том же потоке пропускает цепочку. Java-объекты изменяемы и могут содержать данные выбранного аккаунта. Ошибка, закрытие handle или отзыв доступа не отменяют уже сделанную мутацию объекта. Плагин должен возвращать объект, совместимый с вызывающей стороной, и завершать обработку быстро. Для ответа действителен срок жизни Java-объекта, ограниченный callback.

Прямой `client(slot).send_request` также работает с изменяемыми TL-объектами. Доступ к нему относится ко всему выбранному аккаунту. Вызывающая сторона передаёт владение объектом запроса клиенту и читает ответ внутри callback. Отключение плагина или отзыв аккаунта блокируют новые отправки и доставку ответа. Уже переданный серверу запрос может завершиться.

### Хуки Java и ART

`HookCall`: `args`, `result`, `exception`, `member`, `this_object`; `set_arg`,
`set_result`, `set_exception`, `call_original`. Для primitive conversion применять
`set_arg`/`set_result`, а не присваивание Python int в Java Object[].
Before: priority descending → plugin ID → sequence; after: reverse visited.
Один replacement на Member, repeated original возвращает cached result/throwable.
`call_original` разрешён только внутри replacement; before/after не могут
неявно повторить уже выполненное действие.
При exception после original host не исполняет его второй раз. Изменения args
проверяются до commit; shallow snapshot не отменяет мутацию объектов/side effects.

Backend gate текущих исходников: 64-bit процесс, primary x86_64 или arm64-v8a, API 24–37. Перед первым включением требующего hooks плагина отдельный `:plugin_probe` процесс запускает только встроенный Python/native тест с GC и watchdog. Отказ/crash/timeout не загружает native в основной клиент и блокирует попытки до restart. После успешной пробы основной процесс выполняет self-probe. Остальные OS/ABI отклоняются до native init. Закреплённый AAR обновлён до `1.1.4-api37.1-bygram.3` (fork `43b0d8d`).
На API 35/36/37 новый dispatcher прошёл полный SDK fixture через отдельный DEX
в настоящем Debug-процессе: два владельца, constructor/private/overload,
original throwable, reentrancy, 100 циклов, quarantine и GC. Это ещё не
приёмка финального APK; результаты упаковки и Release будут внесены отдельно.
Физический POCO X7 Pro / Android 16 API 36 / arm64 / 4 KiB также прошёл
source-DЕX fixture: 300 циклов, два владельца, original throwable,
constructor/private/overload, reentrancy, карантин и GC. SHA native-библиотеки
совпал с pinned AAR, процесс сохранился, исходный реестр восстановлен.
Наличие этого телефона не подтверждает остальные ARM/OEM/API.

`Handle.close()` удаляет регистрацию и Python callbacks. После закрытия последнего
владельца и завершения активных вызовов native dispatcher физически снимается,
а запись Member удаляется. Закрытие из самого callback откладывает снятие до
возврата из него. XposedBridge дополнительно дожидается своих активных dispatch.
Обновлённые LSPlant/JNI-ID и ART-flags исправления устранили причину прежнего
retention workaround. Лимит — 1024 одновременно удерживаемых Member;
освобождённые цели больше не требуют restart для повторного использования лимита.
Глобальные оптимизации ART SDK не отключает.
Все ссылки на context/callbacks удаляются при закрытии registrations,
после окончательного cleanup количество registrations равно нулю.

Незавершённый activation/startup marker включает безопасный режим автоматически
в фоновой очереди до загрузки плагинов. Таймаут lifecycle при автозапуске и ошибка
восстановления также переводят клиент в безопасный режим. Уведомление открывает
экран в отдельном процессе без запуска Python. При отключённых уведомлениях
состояние видно в настройках плагинов. Выход выключает движок и плагины. Данные
Telegram сохраняются.

Логи локальны и могут содержать данные, которые автор вывел сам. Копирование
только по действию владельца. Подпись содержимого и закрепление ключа автора доступны;
это не удостоверяет личность издателя. Hot reload/pip отсутствуют; curated каталог
доступен в менеджере плагинов.

Отключение движка сохраняет желание владельца включить отдельные плагины;
карточка показывает это отдельно от текущего ACTIVE. Можно выключить такой
плагин и при остановленном движке. Pending update содержит версию/hash/capabilities
и отменяется до холодного старта отдельным действием, не отключающим старый пакет.
Обновление должно иметь более новую версию. На холодном старте проверяется
совместимость с текущим BYgram. При отказе рабочая версия и её настройки сохраняются,
а причина показывается в карточке. Для возврата на прежнюю версию есть явный откат.

Клиент проверяет список отозванных SHA-256 при запуске и возвращении в приложение.
При отсутствии сети применяются ранее полученные отзывы. Впервые опубликованный
отзыв вступит в силу на устройстве после получения списка.
Журнал показывает текущий и предыдущий сегмент; очистка удаляет оба.

Ошибка восстановления данных оставляет менеджер доступным с engine off/safe mode.
После удаления повреждённого пакета или сброса его данных требуется restart.
Если оба реестра повреждены, явный сброс менеджера забывает установленные пакеты
и grants, сохраняя plugin settings и данные Telegram; автоматического восстановления
разрешения исполнения нет. После прерванной первой активации обновления host
возвращает предыдущий пакет/settings выключенным, сверив activation ID/hash.

## Обмен настройками

Экспорт/импорт из карточки плагина содержит только JSON scopes и версию схемы;
код и разрешения исполнения/аккаунтов не переносятся. Лимит 8 MiB, на scope
1 MiB. Дубликаты ключей, неверный UTF-8, чрезмерная вложенность и чужой ID
отклоняются до остановки плагина. Успешный импорт оставляет плагин выключенным.
Экспорт записывает снимок вне очереди менеджера. Запись можно отменить, таймаут
срабатывает через 30 секунд. При прерывании провайдер может оставить неполный файл.

## Примеры с аккаунтом и hooks

`examples/accounts`: владелец сначала выбирает разрешённые аккаунты в карточке.
В меню открытого чата «Observe this account» создаёт одну подписку на
`ui.page_opened` именно этого аккаунта. Поле настроек `observe` имеет
`scope="account"`. Пример пишет только имя страницы, без сообщений и user ID.
Меню аккаунта без выданного доступа возвращает `AccountUnavailable`.
Повторный вход инвалидирует старую подписку даже при том же user ID.

`examples/hooks`: экспериментальный before/after только для встроенного
`HookBridge$Probe.value(int)`; пользовательский Telegram не изменяется.
Нужен build 72379 / base build 7237 и доступный backend. Для другого build
сначала проверить сигнатуру и обновить manifest, не расширять диапазон вслепую.
Встроенная Android diagnostic fixture отдельно проверяет настоящий native
backend; Python mock test примера не считается native PASS.

## Границы доступа SDK

Изоляция не добавляется по решению владельца: весь авторский код остаётся доверенным, включая Java/raw hooks. Отдельное предупреждение перед установкой из файла не делает выполнение безопасным. Подпись доказывает владение ключом и целостность пакета, а не личность автора и не отсутствие вредоносного поведения.

`signature.json` содержит `format:1`, RSA SubjectPublicKeyInfo DER в Base64 (`public_key`) и PKCS#1 v1.5 SHA-256 подпись в Base64 (`signature`). RSA 2048–4096 бит. Подписывается UTF-8: строка `BYGRAM-PLUGIN-SIGNATURE-V1\n`, затем для каждого файла, кроме signature.json, `sha256 + " " + byte_length + " " + relative_path + "\n"`; сортировка по UTF-8 bytes имён, директории не включаются. Изменение/удаление/добавление файла нарушает подпись. Fingerprint — SHA-256 DER ключа. Обновление подписанного установленного пакета с другим ключом или без подписи отклоняется; локальная установка неподписанного пакета остаётся доступной с предупреждением.

Упаковщик: `python Tools/package_plugin.py SOURCE OUTPUT.byplugin --signing-key PRIVATE.pem`. Приватный RSA PEM хранить вне исходников плагина и не публиковать; OpenSSL используется только на машине автора. Новые публикации модерации требуют подписи, ключ закрепляется за опубликованным plugin ID/репозиторием. Существующие неподписанные записи не становятся подписанными автоматически.

Callbacks событий, меню и Action, доставляемые host с main thread, а также ui.run выполняются асинхронно в порядке очереди одного плагина. По истечении 5 s scope инвалидируется и запрашивается restart; thread, игнорирующий interruption, безопасно уничтожить нельзя. Синхронные before/after/replacement hooks сохраняют поток вызываемого Java-метода. Прямой Java Handler/raw-hook код автора может блокировать UI или VM: без отказа от полного API это ограничение устранить нельзя.

Чтение URI для локального пакета и импорта настроек: отдельный bounded pool, 30 s deadline, Cancel, лимиты bytes; management queue не ожидает провайдера. Отмена проверяется перед применением результата.
