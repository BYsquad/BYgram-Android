# BYgram Plugin SDK v1 — проект документации автора

> Итог этапа 31.10.2026: исправленный `Aliuhook 1.1.4-api37.1-bygram.1`
> закреплён; Full Debug r4 проходит hooks fixture API 28 и core fixture
> API 35/36/37 с незагруженным backend. Gate: только 64-bit primary x86_64
> API 28. Recovery API 36 — PASS; public v1 и аппаратная матрица не приняты.
> r3 остался диагностическим failed кандидатом; [подробный отчёт](development-report.md).

> Обновление 31.10.2026: реализация уже находится в рабочем дереве. Текущий контракт — [SDK прототипа](sdk-v1.md), фактические PASS и оставшиеся проверки — [отчёт](development-report.md). Исторические формулировки «будущий/ещё нет» ниже описывают исходный план, а не текущий статус. Полная приёмка v1 ими не подтверждается.


30.10.2026. **Ниже проект требований SDK.** С 31.10.2026 часть API реализована;
фактические сигнатуры и ограничения описаны в [SDK прототипа](sdk-v1.md).
Не считать весь этот проект гарантией доступности API в конкретном APK.

Принятая платформа: Full для Android 7.0+ / API 24, Chaquopy с Python 3.11. Для Java hooks выбран BYxarek/hook `1.1.4-api37.1`; совместимость конкретной ОС/ABI подтверждается интеграционной матрицей BYgram.

## 1. Как будет устроен первый плагин

```text
hello-bygram/
  manifest.json
  main.py
  assets/icon.png          # необязательно
  locales/ru.json          # необязательно
  locales/en.json
```

Упаковать содержимое каталога в ZIP без дополнительной внешней папки, дать расширение `.byplugin`, установить на странице «Плагины». Установка сохраняет пакет выключенным. Включать только после проверки исходника и доверия автору.

Не включать в пакет Telegram credentials, backend tokens, абсолютные пути и локальные секреты. `.py` — исходный UTF-8 текст; `.pyc`, native extensions, Dex/JAR v1 не принимает. Пакет может содержать несколько Python-модулей и assets.

## 2. Manifest

30.10.2026: проверка метаданных уже реализована в `PluginManifest` (Full-only),
добавлена [JSON Schema](manifest-v1.schema.json). Это ещё не установщик и не runtime.
Числовые поля manifest ограничены положительным signed int32; версии пакета
сравниваются отдельно и не преобразуются в build. Имя entrypoint-класса v1 —
ASCII identifier `[A-Za-z_][A-Za-z0-9_]*`, кроме ключевых слов Python 3.11.
Строки не принимают C0/C1 controls и непарные UTF-16 surrogates.
Поле `version` ограничено 80 символами.
`hook_targets`: непустой массив объектов `{class, method, args}` либо
`{class, constructor: true, args}`; method/constructor взаимоисключающие.
Java-типы записываются именами (`int`, `java.lang.String`, `int[]`), без `void`,
максимум 512 ASCII символов на тип аргумента.
Проверка существования файлов/сигнатур и доступности backend остаётся следующим
слоям. Полный статус и команды: [отчёт](development-report.md).

Проект минимального manifest:

```json
{
  "manifest_version": 1,
  "id": "dev.bygram.hello",
  "name": "Hello BYgram",
  "description": "Кнопка приветствия в списке чатов",
  "version": "1.0.0",
  "author": "Your name",
  "entrypoint": "main.py:HelloPlugin",
  "sdk": {"min": 1, "max": 1},
  "python": "3.11",
  "capabilities": ["ui"],
  "account_scope": "global",
  "settings_schema": 1,
  "icon": "assets/icon.png"
}
```

| Поле | Правило v1 |
|---|---|
| `manifest_version` | Ровно 1; неизвестную версию отклонить до исполнения |
| `id` | 3–120 ASCII символов, lowercase сегменты через точку; каждый сегмент начинается с буквы, затем буквы/цифры/underscore; минимум два сегмента; неизменяемый storage key |
| `name`, `description`, `author` | Обязательные строки, максимум 80/1000/120 символов; непустое имя, без control characters; не HTML |
| `version` | Три неотрицательных целых `major.minor.patch`, без ведущих нулей кроме `0`; v1 без ranges/prerelease синтаксиса |
| `entrypoint` | Существующий относительный `.py` файл внутри пакета и имя класса через `:`; никаких expressions; class name — Python identifier |
| `sdk.min/max` | Целые положительные значения; inclusive range; min ≤ max; SDK major текущего host входит в диапазон |
| `python` | Для v1 ровно `3.11`; несовпадение — incompatible, не попытка установки второго runtime |
| `capabilities` | Только известные host значения, без duplicates; неизвестное обязательное значение — incompatible |
| `account_scope` | `global` для оформления или `selected_accounts` для account-sensitive сценариев |
| `settings_schema` | Целое ≥1, версия данных; отсутствующие данные считаются пустыми для этой версии |
| `icon` | Optional относительный PNG/WebP; host decode с лимитом размеров, например 256×256 и 256 KiB |
| `bygram` | Optional `{ "min_build": N, "max_build": M }`, числовые сравнения build, не строк versionName |
| `telegram` | Optional диапазон целых базовых build; обязательный для raw hooks вместе с `bygram` |
| `requires_hook_backend` | Optional boolean, default false; true требует проверенной пары OS/ABI backend |

Unknown поля manifest v1 отклонять с именем поля, чтобы опечатка не игнорировалась. Для будущего расширения менять schema version или добавлять заранее документированный optional field; не исполнять неизвестные поля. Декларации dependencies между плагинами в v1 нет: каждый пакет самодостаточен.

Для `hooks.raw` обязательны `requires_hook_backend=true`, диапазоны host builds и декларация `hook_targets` с `class`, `method`/`constructor`, `args` Java type names. Пример проверки API — после уточнения exact signatures конкретной версии клиента. Не использовать dynamic discovery в качестве обещания совместимости.

Capabilities первой версии:

| Capability | Назначение в SDK |
|---|---|
| `ui` | Собственные settings/menu/pages и bulletins |
| `theme` | Scoped overrides поддерживаемых цветов/элементов |
| `messages.observe` | События сообщений выбранных аккаунтов |
| `messages.send` | Явная отправка через account client |
| `network` | Host network helpers |
| `hooks.raw` | Экспериментальные Java hooks/reflection helpers |

Обычные настройки/логи/storage своей области доступны базовому плагину. Каждая capability проверяется host на вызовах SDK. **Эти проверки не ограничивают весь Python/Java-код в процессе.** Все Python-плагины требуют доверия, включая те, что декларируют только `ui`. Выбранные аккаунты также не являются sandbox.

## 3. Базовый класс и lifecycle

Проект сигнатур:

```python
from bygram import Plugin

class MyPlugin(Plugin):
    def on_load(self):
        pass

    def on_enable(self):
        pass

    def on_disable(self):
        pass

    def on_unload(self):
        pass

    def create_settings(self):
        return []

    def migrate_settings(self, old_version, new_version, values):
        return values
```

Host создаёт объект, затем подключает `self.context` до `on_load`. Конструктор автора без аргументов, без побочных эффектов. `on_load/on_enable/on_disable/on_unload` идут последовательно через management queue; работа с View — только `context.ui.run`.

`on_enable` регистрирует действия, подписки и overrides через host API. Все возвращённые handles привязываются к PluginScope автоматически. Повторное enable уже ACTIVE — no-op; повторный disable выключенного — no-op. Частичный enable с исключением очищает все уже созданные handles.

`on_disable` завершает авторскую работу; `on_unload` освобождает ссылки. Cleanup host выполняется независимо от ошибок автора. Отсутствие callback означает стандартный no-op; возвращать boolean «успешно» не требуется, ошибки — исключениями.

`create_settings` вызывается только у разрешённого к исполнению загруженного плагина, на management queue; возвращает данные, не View. Для выключенного/не загруженного пакета настройки можно прочитать из store, но пользовательский Python ради страницы настроек автоматически не запускать; UI предлагает сначала включение. Системные детали, возможности и удаление доступны всегда без исполнения.

`migrate_settings` получает копию JSON settings одного scope, возвращает новую. Host валидирует результат и атомарно применяет его после backup. Исключение сохраняет старые данные и останавливает обновление. Миграция вызывается отдельно для global и каждого account scope; scope указан в context, активные account clients на этом шаге недоступны. Крайние переходы schema версий автор обязан поддержать или явно отклонить.

## 4. Context и публичные модули

| Интерфейс | Контракт |
|---|---|
| `context.plugin_id`, `version`, `generation` | Read-only identity текущей установленной версии |
| `context.log.info/warning/error(text)` | Host ring log; не передавать секреты/сообщения |
| `context.settings.get(key, default)` / `set(key, value)` | Global JSON store; атомарность, типы/лимиты; account store — отдельным методом |
| `context.account_settings(account)` | Store только разрешённого account identity, проверка generation |
| `context.storage` | Пути/чтение assets и private plugin data с проверкой выхода за root |
| `context.ui.run(callback)` | Планирование main-thread работы; owned task, cancel при disable |
| `context.ui.bulletin(text)` | Локальное сообщение, ограниченная длина, no HTML |
| `context.ui.add_menu(location, id, title, on_click)` | Именованный location, уникальный local ID; возвращает owned handle |
| `context.ui.open_page(page_spec)` | Native page из поддержанных элементов; handle закрывается при disable |
| `context.theme.override(key, value)` | Только опубликованные ключи и типы; обратимый handle |
| `context.events.subscribe(name, callback, account=...)` | Документированное событие, account/generation и поток в reference |
| `context.work.submit(callback)` | Ограниченная background queue; результаты инвалидируются при disable |
| `context.client(account)` | Клиент конкретного разрешённого аккаунта; никакого неявного selected account |
| `context.hooks` | Экспериментальная регистрация Java hooks, проверка backend/capability |

Клик UI получает `event.account` и слабый native page context; не сохранять event/View дольше callback. Даже глобальное меню может открываться в контексте конкретного аккаунта. Account-sensitive SDK метод требует account явно; вызов без него — `AccountRequired`, вне выбранного scope — `AccountUnavailable`. Не молча переключать аккаунт.

`context.client(account).send_text(peer, text)` предлагается вводить лишь на этапе P6 после полноценного account lifecycle. В v1 host не должен обещать все TL методы стабильным API; raw Java — отдельно и без гарантий совместимости. Для отправки сохранять штатные проверки, очереди и обработку Telegram errors; автоматического повтора с возможным дублированием нет.

Первые стабильные события: `ui.page_opened`, `ui.page_closed`, `account.added`, `account.logged_out`, `theme.changed`. Добавление message/request/update событий сопровождается схемой DTO, потоком, nullable полями, гарантиями порядка и privacy-поведением. Не выдавать внутренний мутируемый TLRPC объект там, где достаточно read-only DTO.

## 5. Первый пример: меню и настройки

Проект `main.py` для manifest из раздела 2:

```python
from bygram import Plugin
from bygram.settings import Switch, TextInput


class HelloPlugin(Plugin):
    def on_enable(self):
        self.context.ui.add_menu(
            location="dialogs.toolbar",
            id="hello",
            title="Приветствие",
            on_click=self.show_hello,
        )

    def create_settings(self):
        return [
            Switch(key="enabled", title="Показывать приветствие", default=True),
            TextInput(key="text", title="Текст", default="Привет из BYgram!",
                      max_length=120),
        ]

    def show_hello(self, event):
        if not self.context.settings.get("enabled", True):
            return
        text = self.context.settings.get("text", "Привет из BYgram!")
        self.context.ui.bulletin(text)
```

`dialogs.toolbar` — планируемая стабильная точка P4, не существующий сейчас extension point. Клик вызывается на main thread; `bulletin` безопасен на этом потоке. `add_menu` хранит callback только до cleanup PluginScope; ручной `unhook` для этого примера не нужен. Settings setters дополнительно проверяют `max_length`, а не только EditText.

Проверка примера при реализации: установить выключенным → включить → открыть список чатов → нажать → изменить text → повторить → выключить → убедиться, что кнопка исчезла → перезапустить → проверить сохранность настроек. Повторить смену темы и аккаунта.

## 6. Схема страницы настроек

Первые компоненты: `Header`, `Text`, `Switch`, `TextInput`, `Select`, `Slider`, `Action`. Все элементы — данные; host создаёт обычные Telegram/BYgram cells. Для каждого поля: стабильный key, title, default, scope, validator. `Select` хранит value, а не позицию переведённого пункта; `Slider` имеет min/max/step и проверку диапазона на записи.

Глобальные/аккаунтные значения разделены явно. Удалённый key сохранять до миграции или сброса; не терять неизвестные пользовательские настройки при простом открытии страницы. Динамическая видимость применяется host после изменения, не polling Python каждый frame. Actions идут в background, если работа долгая; UI updates через `ui.run`.

UI text локализуется ключом словаря плагина с fallback en, затем исходной строкой. `password` field не объявляет store зашифрованным: хранение секретов требует отдельного проверенного Keystore API и не входит в первый пример.

## 7. Проект интерфейса хуков

```python
handle = self.context.hooks.method(
    class_name="org.telegram.messenger.plugins.test.HookProbe",
    method="label",
    args=["java.lang.String"],
    before=self.before_label,
    after=self.after_label,
    priority=0,
)

def before_label(self, call):
    call.args[0] = "BYgram"

def after_label(self, call):
    if call.exception is None:
        call.set_result(str(call.result) + "!")
```

Это **синтетический тестовый target**, его ещё предстоит добавить в instrumentation; он не часть Telegram и не пример для установки на текущий APK. Для реального метода брать сигнатуру из своего build, проверить nullable/return type и диапазон версий. В manifest такого плагина нужны `hooks.raw`, backend requirement и target/build constraints.

`HookCall`: `member`, `this_object` (None для static), `args`, `result`, `exception`; методы `set_result(value)`, `set_exception(error)`. Before `set_result` завершает оригинал ранним результатом; after может явно изменить его. Invalid return/argument type отклоняется до Java call. Для constructor замена результата недоступна; поддерживаемые действия описать отдельно по результатам прототипа.

`handle.close()` идемпотентен. Отключение плагина закрывает все handles автоматически. Порядок priority/reverse-after и конфликт replacement определены [в плане](implementation-plan.md). Не вызывать hooked method из callback без понимания reentrancy. Не перехватывать `onDraw` ради обычной смены цвета: для этого есть theme API.

Для replacement предусмотреть отдельный `hooks.replace(..., callback=...)`, а не смешивать с before/after keyword combinations. Один replacement owner на method. `call.call_original()` обходит hooks; если он уже вызван и callback затем упал, host не повторяет оригинал. Такая ошибка выводится с корректным исключением, а не маскируется потенциальным повтором отправки сообщения.

API hooks помечается experimental. Нет гарантий для native/abstract methods, boot image, произвольных Android internals и обновлений Telegram. Глобальные отключения оптимизаций ART не являются частью обычного SDK.

## 8. Ошибки SDK

| Код | Что делать автору/пользователю |
|---|---|
| `InvalidPackage`, `InvalidManifest`, `PackageLimitExceeded` | Исправить структуру, поле или размер; код не загружен |
| `IncompatibleSdk`, `IncompatibleBuild`, `IncompatiblePython` | Использовать подходящую версию пакета/host |
| `HookBackendUnavailable`, `HookTargetMissing` | Отключить raw hooks или выбрать проверенный build/device |
| `CapabilityDenied`, `AccountRequired`, `AccountUnavailable` | Проверить декларацию, выбор аккаунта и logout |
| `HookConflict`, `ContributionConflict` | Изменить target/local ID либо отключить конфликтующее расширение |
| `PluginDisabled` | Callback/generation устарел; не повторять работу |
| `SettingsMigrationFailed` | Исправить миграцию; предыдущие данные сохранены |
| `PluginCallbackFailed`, `PluginQuarantined` | Открыть локальный журнал, исправить код, затем явно включить |

UI содержит понятный перевод и стабильный код для поиска в документации. Полный traceback доступен локально с предупреждением о содержимом, без auto-upload. Авторский logger не должен печатать сообщения/телефоны/auth keys.

## 9. Версии и обновление

SDK major описывает совместимость публичных контрактов, не versionName Telegram. В пределах major новые API additive; доступность конкретной функции можно проверять через host feature list. Для breaking API — новый major, таблица миграции и новый диапазон manifest. Депрецированные API сохранять минимум один объявленный переходный релиз host, сроки фиксировать в changelog SDK.

Обновление плагина не активирует его само по себе, если прежняя версия была OFF. Новые capabilities, другой publisher key или другой непроверенный файл требуют повторного согласия. В v1 обновление применяется после restart; hot reload отсутствует. Откат восстанавливает предыдущий package и settings snapshot, но не отменяет действия во внешнем мире.

Совместимость exteraGram не предполагается. Одинаковые Python и hooking backend не означают одинаковые lifecycle, utils и UI API. Переносить плагин вручную по таблице возможностей; слой совместимости появится только с отдельной спецификацией и проверками.

## 10. Что добавить к документации при реализации

1. Валидатор и JSON Schema manifest с fixtures положительных/отрицательных случаев.
2. `.pyi` stubs всех публичных типов, сведения о потоках и nullable аргументах.
3. Реальные примеры: hello/menu, настройки, theme override, account-scoped event, Java hook.
4. Способ упаковки без исполнения импортируемого кода; проверяемый SHA-256 пакета.
5. Список стабильных UI locations/theme keys/events по каждой версии SDK.
6. Руководство по debugging, safe mode, performance и upgrade troubleshooting.
7. Cookbook с тестами примеров и changelog SDK.

До появления этого набора статус страницы остаётся «проект SDK». Ссылка «Документация» в выпускаемом UI должна вести на документацию именно поставленной версии.
