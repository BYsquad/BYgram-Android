---
name: bygram-plugin
description: "Создаёт, исправляет, проверяет и упаковывает Python-плагины BYgram Android (.byplugin), переносит exteraGram .plugin и Elyx, подбирает API SDK, capabilities и тесты. Use for BYgram plugin development, debugging, review, packaging, and compatibility ports."
---

# Плагины BYgram · GitHub Copilot

Используй запрос из чата Copilot. При вызове `/bygram-plugin` описание после команды задаёт ожидаемое поведение плагина.

Сверь API с целевой версией BYgram. Используй [SDK](https://byxarek.site/TEST-PROJECT/BYgram/docs/#sdk), [манифест и capabilities](https://byxarek.site/TEST-PROJECT/BYgram/docs/#manifest), [пример первого плагина](https://byxarek.site/TEST-PROJECT/BYgram/docs/#first-plugin) и [руководство по сборке без клонирования](https://byxarek.site/TEST-PROJECT/BYgram/docs/en/package/). Для переноса сверяй [совместимость с exteraGram](https://byxarek.site/TEST-PROJECT/BYgram/docs/#compatibility) и [руководство по Elyx](https://byxarek.site/TEST-PROJECT/BYgram/docs/en/elyx/project-structure/).

Доведи запрос до нужного результата: исходники и пакет для новой разработки или переноса, конкретные выводы для ревью, причина и проверенное исправление для отладки. Используй вложенные упаковщик и конвертер из `references/Tools/`, если они установлены вместе со скиллом. При доступных инструментах проверь синтаксис Python 3.11, манифест, архив и сценарий плагина. Укажи фактические результаты, включая проверку на Android. Если нужная страница недоступна или документация не подтверждает API, запроси только сведения, без которых нельзя продолжить.
