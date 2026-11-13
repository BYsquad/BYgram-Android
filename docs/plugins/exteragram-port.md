# Перенос плагинов exteraGram

Конвертер для Full читает метаданные из AST и упаковывает исходник в `.byplugin`. Он не запускает исходник. После установки для исполнения действуют обычные подтверждения BYgram. Для одного и того же исходного ID конвертер сохраняет постоянный ID BYgram. Опциональный `--signing-key` использует обычный механизм подписи пакета BYgram.

```powershell
python Tools/port_extera_plugin.py example.plugin example.byplugin --telegram-hooks --message-menu
```

| Флаг | Заявленная возможность |
| --- | --- |
| `--telegram-hooks` | Перехваты запросов, ответов, обновлений и отправки через `BasePlugin` |
| `--telegram-requests` | `client_utils.send_request` и `self.client(account).send_request` |
| `--send-text` | `client_utils.send_text` и `self.client(account).send_text` для простого текста |
| `--media` | Отправка файлов плагина и редактирование текста сообщений |
| `--ui` | Меню чата и профиля |
| `--message-menu` | Контекстное меню сообщения, включая `ui` и `messages.observe` |

Для хуков, запросов, отправки и меню сообщений включите доступ к выбранным аккаунтам в карточке плагина. `BasePlugin` поддерживает `on_app_event`, `logger`, импорт и экспорт глобальных настроек, Telegram hooks и пять типов меню. В BYgram `DRAWER_MENU` появляется в меню аккаунтов, `MAIN_MENU` — в меню списка чатов. Callback действия над сообщением получает идентификаторы, `message`, фрагмент и `groupedMessages` для альбома. Условия принимают выражение MVEL или Python callable. Методы `client_utils` используют аккаунт события, если вызов связан с ним, и выбранный аккаунт в UI в остальных случаях.

`ui.settings` поддерживает `Header`, `Text`, `Switch`, `Selector`, `Input`, `EditText`, `Divider` и `Custom`. Обычная фабрика строки получает Android Context и возвращает View или UItem. `SimpleSettingFactory` добавляет раздельное создание и привязку View, callbacks подключения, сравнения и взаимодействия. `reload_settings=True` заново строит открытую корневую страницу или подстраницу с цепочкой `link_alias`. `elyx.settings` поддерживает `get_settings()` и `clear_settings()`.

`client_utils` поддерживает отправку с HTML или Markdown, расширенный `send_message`, редактирование текста и замену медиа. Числовой ID ответа разрешается среди загруженных сообщений открытого диалога. Для другого диалога передайте `MessageObject`. Файлы для отправки выбираются из пакета или данных плагина. `android_utils`, `file_utils`, `hook_utils`, `log_utils` и `intents` доступны напрямую. Обработчики Intent получают копии ссылок `ACTION_VIEW` после разблокировки для разрешённого аккаунта. `before` может вернуть `True` и остановить стандартную обработку. Фильтры флагов принимают маску или список, а callback с `**kwargs` получает полный контекст.

Xposed адаптер поддерживает `MethodHook`, `MethodReplacement`, `HookFilter`, before/after и регистрацию перегрузок. Для переноса с ART подготовьте JSON с диапазонами `bygram` и `telegram` и точными `hook_targets`, затем передайте его через `--hook-manifest`. Для набора перегрузок укажите каждую сигнатуру. Запуск использует пробу ART BYgram.

Elyx `requires` проверяет наличие включённого плагина нужной версии и порядок запуска. При отсутствующей или устаревшей зависимости менеджер предлагает открыть HTTPS-ссылку из `requires`. Установка проходит через отдельное подтверждение. При отключении обязательного плагина зависимые плагины выгружаются. В режиме разработки `watch` отправляет изменённые и удалённые файлы после первоначального развёртывания и возвращается к отправке полного пакета при смене базового хеша.

## Elyx и зависимости

В Full файлы `.plugin`, `.elyx`, `.eaf` и их Elyx-варианты с `.zip` можно открыть из чата или выбрать через «Установить из файла». Клиент преобразует исходник в `.byplugin` и показывает обычный просмотр пакета. Преобразование не запускает авторский Python. Клиент скачивает объявленные pure-Python wheels с PyPI, сверяет размеры и SHA-256 и включает их в итоговый пакет. YAML-метаданные читает встроенный PyYAML. Клиентский конвертер объявляет поддерживаемые возможности совместимости, включая медиа. Аккаунты пользователь выбирает отдельно перед работой с их данными.

```powershell
python Tools/port_extera_plugin.py weather.elyx weather.byplugin --ui --resolve-pypi
```

Конвертер принимает исходные архивы `.elyx`, `.eaf` и варианты с суффиксом `.zip`. Сохраняется структура проекта. Читаются `refmap`, JSON-метаданные и локализация, YAML при наличии PyYAML на компьютере и Python-файлы с литеральными декларациями. Исходники при упаковке не исполняются. Поддерживается байткод Python 3.11. Для точки входа `.pyc` поместите метаданные в отдельный файл. JSON, YAML и TXT можно импортировать как модули Elyx. Нативные библиотеки внутри сторонних архивов отклоняются.

Модуль `elyx` предоставляет `assets`, `strings`, `settings`, `metainfo`, `refmap`, `get_environment()` и `import_module()`. Доступны текстовые, бинарные и JSON-ресурсы, а для YAML в рантайме нужен доступный плагину PyYAML. Растровые ресурсы можно преобразовать в `BitmapDrawable` или передать по пути через Telegram `ImageLocation`. Переводы выбираются по системному языку с запасным английским. Метаданные карточки разрешаются при упаковке. Исходники проекта и распакованные pure-Python wheels импортируются в пространстве имён каждого экземпляра без изменения общего `sys.path`. Для локального динамического импорта используйте `elyx.import_module()`. Java-обёртки `gen`, `gen2`, `Runnable`, `OnClickListener` и `Callback*` работают с интерфейсами через Chaquopy.

Флаг `--resolve-pypi` разрешает сборщику скачать совместимые pure-Python wheels с PyPI. Он поддерживает `requirements` у Elyx и `__requirements__` у однофайлового плагина. Прямые URL и явные environment markers в объявлении отклоняются. Для зависимостей с условиями в их метаданных проверьте состав архива и запуск под Android Python 3.11. Wheels из каталога `wheels` Elyx включаются без скачивания. Зависимости входят в итоговый `.byplugin`, включая его SHA-256 и подпись при подписании. Установка на телефоне использует готовый пакет.

## Репозитории

В каталоге Full можно указать прямой HTTPS-URL `extera-plugins.json` schema 1 или ссылку на публичный Telegram-канал, включая форму `https://t.me/exteraPluginRepo?c=channel_name`. Для HTTPS клиент сверяет размер, SHA-256, ID и версию исходного файла. Для GitHub можно использовать адрес репозитория, ветки или каталога, включая `exteraPluginRepo?git=…`. Telegram-канал ищет среди 2000 документов и сортирует по времени публикации или правки. Для доступных метаданных оставляется один пакет на ID плагина. Автоматическая загрузка ограничена 32 МиБ и 12 секундами ожидания. Остальные записи доступны по имени файла и проверяются при открытии.

```powershell
python Tools/port_extera_repository.py https://example.org/extera-plugins.json output/plugins --ui
```

Конвертер принимает HTTPS-индекс exteraGram schema 1. Он проверяет размер, SHA-256, ID и версию исходного архива, а затем создаёт `index.json` и пакеты BYgram. Для загрузки в приложении разместите весь результат на HTTPS и добавьте адрес нового `index.json` через «Каталог». Параметры возможностей и подписи применяются ко всем пакетам этой сборки. Существующий индекс заменяется после успешного преобразования всех записей.

## Интерфейс, Java и медиа

В Full предустановлены `beautifulsoup4`, `requests`, `lxml`, Pillow, `packaging`, `PyYAML` и `certifi`. Нативные зависимости `lxml` и Pillow для arm64 и x86_64 пересобраны с выравниванием 16 КиБ. IDE-адаптеры ставятся по инструкции [Dev Server](dev-server.md). `elyx.mvel_execute` выполняет MVEL с переданными переменными и опциональным Java-типом результата.

`AlertDialogBuilder` поддерживает `create()` перед показом, доступ к Java-диалогу, верхнее изображение или анимацию, настройки размытия и выделение кнопок. Для немедленного доступа к Java-диалогу вызывайте методы на UI-потоке. `BulletinHelper` поддерживает выбранный фрагмент, Lottie-иконки, кнопки, отмену действия и уведомления об операциях с файлами.

Java-прокси поддерживают `jpreconstructor`, `jconstructor`, `on_post_init`, `init_args`, `new_java_instance` и `from_java`. Для переопределений используйте `self.call_super()`. У `AccountClient` есть привязанные к аккаунту контроллеры и расширенные параметры отправки. В `edit_message` принимаются `spoiler=` и `with_spoiler=`.

`ui.alert.AlertDialogBuilder` поддерживает иконки элементов списка, высоту пользовательского View, кликабельное сообщение, настройку затемнения и ключ темы кнопок. Методы `set_on_back_button_listener`, `set_on_cancel_listener` и `set_on_dismiss_listener` регистрируют события нативного диалога. `set_cancelable` и `set_canceled_on_touch_outside` можно менять после показа. `ui.bulletin.BulletinHelper` показывает информацию, ошибку и успех. Отправка медиа и редактирование требуют `messages.media` и разрешённого аккаунта.

`extera_utils.classes` поддерживает `Base`, `java_subclass`, `joverride` и `joverload` для Java-подклассов через DexMaker. `new_instance(..., arg_types=...)` выбирает конструктор, `.java` возвращает объект для Java, а `self.call_super(...)` вызывает родительский метод во время переопределения. Создавайте объект во время callback плагина или из Elyx.

```python
from java.util import ArrayList
from extera_utils.classes import Base, java_subclass, jfield, jgetmethod, jmethod, jsetmethod

@java_subclass(ArrayList)
class CountingList(Base):
    counter = jfield("int", default=0, methods=[jgetmethod("getCounter"), jsetmethod("setCounter")])

    @jmethod("describe", "java.lang.String", ["int"])
    def describe_count(self, offset):
        return str(self.counter + offset)

proxy = CountingList.new_instance(8, arg_types=["int"])
proxy.java.setCounter(3)
assert proxy.counter == 3
assert proxy.java.describe(4) == "7"
```

Поля и методы экземпляра видны через обычную Java-рефлексию. Сгенерированные `jgetmethod` и `jsetmethod` читают и пишут поле в DEX. Конструктор Java выполняется до установки значений `jfield(default=…)`. Ограничения `__app_version__` и `__sdk_version__` относятся к exteraGram.

`MenuItemData.condition` принимает выражение MVEL или Python callable. `jMVELmethod` и `jMVELoverride` компилируют код MVEL и выполняют его в Java. В теле доступны аргументы, публичные поля, `java`, `python`, `py`, `self` и `SUPER_<имя>` для конкретного родительского метода. `@jclassbuilder()` вызывает функцию с `DexMaker`, `TypeId` нового класса и `TypeId` родителя до загрузки класса. [Class Proxy](https://plugins.exteragram.app/docs/class-proxy)

В Elyx `to_svg_drawable()` возвращает векторный drawable. Вариант с шириной и высотой возвращает `BitmapDrawable`. `to_svg_bitmap(width, height, white=False)` создаёт bitmap, а `to_svg_thumb(color_key, alpha)` применяет цвет темы. `to_lottie_drawable(width, height)` принимает Lottie JSON или TGS и переводит размеры из dp в пиксели. [Elyx Assets](https://plugins.exteragram.app/docs/elyx/assets)

Долгое нажатие на строку с `link_alias` показывает копирование и отправку ссылки. BYgram открывает настройки включённого плагина, прокручивает список и подсвечивает строку. Для вложенной страницы задайте `link_alias` также каждому родительскому пункту, который открывает эту страницу. `EditText(mask=…)` принимает регулярное выражение Java, разрешает ввод частичного значения и требует полного совпадения при сохранении. [Plugin Settings](https://plugins.exteragram.app/docs/plugin-settings)

Проверка конвертации и загрузки через подставной Java-мост входит в `python Tools/check_plugins.py`. [SDK BYgram](sdk-v1.md) · [BasePlugin exteraGram](https://plugins.exteragram.app/docs/plugin-class)

## Оставшиеся различия

- Нативные хуки BYgram требуют деклараций сигнатур, диапазонов сборки и успешной пробы ART на устройстве. Intent API ограничен ссылками `ACTION_VIEW` после разблокировки.
- Совместимые helpers сохраняют привязку к аккаунту события внутри callback. Вне него при отсутствии явного аккаунта используется выбранный аккаунт. Медиа передаётся из пакета или каталога данных плагина. Числовой ID ответа разрешается среди загруженных сообщений открытого диалога.
- `SimpleSettingFactory` использует RecyclerView BYgram. В callbacks создания UItem и обработки нажатий аргументы, специфичные для Java-движка exteraGram, передаются как `None`. `release()` отключает новые строки, а регистрация типа освобождается при выгрузке плагина.
- Dev Server поддерживает форматы запросов exteraGram и дополнительно требует токен. У BYgram собственные упаковщик и конвертер. Для создания исходного проекта Elyx можно использовать ElyxBuilder, а готовый Elyx преобразовать штатным конвертером BYgram.
