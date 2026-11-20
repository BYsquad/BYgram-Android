# Скиллы BYgram для ИИ

## Установка без репозитория

Скачай [`install_bygram_skills.py`](https://byxarek.site/TEST-PROJECT/BYgram/docs/install_bygram_skills.py) и запусти его с Python 3.10 или новее:

```sh
python install_bygram_skills.py
```

Если команда `python` недоступна, используй `py` на Windows или `python3` на Linux и macOS. Установка копирует файлы из самого скрипта и скачивать дополнительно ничего не нужно. Для установки в один клиент добавь его имя, например `python install_bygram_skills.py claude`. Варианты: `codex`, `claude`, `gemini`, `cursor`, `copilot`, `deepseek`. Codex, Gemini CLI и Cursor используют общий каталог.

Скрипт печатает пути установки. Открой новую сессию агента. Для DeepSeek скрипт подготовит локальный Markdown с промптом и напечатает путь к нему. Скопируй его в чат или передай в API вместе с нужными документами из того же каталога.

При повторном запуске текущие файлы сохраняются. Если в каталоге уже есть изменённая или более старая копия, скрипт сообщит её путь. Чтобы поставить новую версию, перемести предыдущий каталог скилла и запусти установщик ещё раз.

Сопровождающие BYgram собирают скачиваемый файл из корня проекта:

```sh
python Tools/install_bygram_skills.py --build output/install_bygram_skills.py
python Tools/check_bygram_skill_installer.py
```

## Использование в репозитории

Открой корень репозитория BYgram Android в своём агенте. Точки входа используют один [рабочий процесс](agent-workflow.md) со ссылками на версионную документацию SDK, примеры и тесты.

| Клиент | Как использовать |
| --- | --- |
| Codex | Проектный [`SKILL.md`](../../.agents/skills/bygram-plugin/SKILL.md). В запросе можно указать `$bygram-plugin`. [Документация](https://developers.openai.com/plugins/concepts/skills) |
| Claude Code | Проектный [`SKILL.md`](../../.claude/skills/bygram-plugin/SKILL.md). Вызов `/bygram-plugin` с описанием задачи. [Документация](https://code.claude.com/docs/en/skills) |
| Gemini CLI | Общий [`SKILL.md`](../../.agents/skills/bygram-plugin/SKILL.md). Попроси применить `bygram-plugin` и подтверди активацию, если CLI её предложит. [Документация](https://geminicli.com/docs/cli/skills/) |
| Cursor | Общий [`SKILL.md`](../../.agents/skills/bygram-plugin/SKILL.md). Выбери `/bygram-plugin` в чате агента или опиши задачу. [Документация](https://cursor.com/docs/skills) |
| GitHub Copilot в VS Code | Проектный [`SKILL.md`](../../.github/skills/bygram-plugin/SKILL.md). Укажи `/bygram-plugin` в чате. [Документация](https://code.visualstudio.com/docs/agent-customization/agent-skills) |
| DeepSeek Chat, API и другие чаты | Вставь [готовый промпт](deepseek-prompt.md) и приложи руководство и нужные документы SDK. Поддержка файлов, терминала и автоматического подключения скиллов зависит от клиента. |

Пример запроса: «Используй bygram-plugin. Сделай пункт в меню чата, который показывает настраиваемую подсказку. Собери .byplugin и перечисли пройденные тесты».

При использовании вне репозитория предоставь агенту [руководство](agent-workflow.md), SDK, схему манифеста и подходящий пример из нужной версии BYgram. Для локальной сборки скачай упаковщик по инструкции [SDK](sdk-v1.md#сборка-плагина-без-клонирования).
