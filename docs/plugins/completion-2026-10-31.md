# Завершение текущего BYgram SDK P0–P8

> **Исторический снимок 31.10.2026.** Текущий статус: [надёжность и модерация 01.11](reliability-and-moderation-2026-11-01.md). Исключение каталога, прежний gate и состояние упаковки ниже относятся только к этому проходу.

31.10.2026. Android. Согласованный объём — текущий BYgram SDK; совместимость с чужими `.plugin`/exteraGram и онлайн-каталог исключены. Работа ведётся поверх существующих незакоммиченных изменений.

## Реализация

| Этап | Выполнено в текущем SDK |
|---|---|
| P0 | Full API 24 / Lite API 23, четыре ABI, pinned Chaquopy/AliuHook, gate до native init, ELF/ZIP tooling |
| P1 | Native менеджер, строгие manifest/ZIP, установка без исполнения, AtomicFile registry/backup, удаление |
| P2 | Lazy Python, private imports, lifecycle, поколения, owned handles, очереди, settings/storage/logging |
| P3 | Startup/activation markers, отдельный recovery process без Python, safe mode, карантин, явный reset повреждённых реестров |
| P4 | Native settings/pages/menu/theme/events, отзыв account grants, обновление страницы после Python settings.set |
| P5 | Typed hooks, ordering/conflicts, two-owner cleanup, original exception, reentry, bounded persistent native dispatchers |
| P6 | Диалоги/профиль/чат/menu metadata, чтение черновика и подтверждаемый preview, message observation и owner-only network completion |
| P7 | Pending/cancel, atomic migration journal, предыдущий пакет/settings, rollback прерванной активации, auth generation |
| P8 | SDK stubs, шесть примеров, JVM/Python/crash/device/R8 fixtures; упаковка и приёмка итогового Release выполняются ниже |

Подробный исполняемый контракт: [SDK](sdk-v1.md). `Plugin.on_load/on_enable/on_disable/on_unload` по умолчанию пусты намеренно: это необязательные переопределения автора, а не нереализованный runtime.

## Исправленные дефекты

- Перед изменением пакета/данных/grants автозапуск durable выключается. Отказ операции не включает код при следующем запуске. `on_disable` вызывается только после входа в `on_enable`.
- Activation recovery сопоставляет ID/hash и отсутствие успешного activated. Старые markers не откатывают принятые версии. Migration journal проверяется полностью до записи; orphan snapshots очищаются.
- Ошибка recovery оставляет доступным менеджер, выключает engine и показывает причину. Сброс двух повреждённых registry сохраняет settings и Telegram auth.
- Отклонённая страница освобождает callbacks. Network completion виден только инициировавшему context. Account events требуют selected_accounts и точного grant; stale queued auth events отбрасываются.
- `context.accounts` — read-only tuple текущих разрешённых слотов. Account-поля повторно проверяют доступ; Select отображает подпись, журнал читает оба сегмента и не открывается после закрытия страницы.
- Theme принимает реестр Telegram, RGB/ARGB, signed/unsigned int. Неизвестные имена/цвета отклоняются. Page events используют стабильные ID независимо от R8.
- Composer preview сверяет слот, identity, dialog/topic, состояние composer и прежний draft при подтверждении. Запись медиа/readonly/edit исключены; preview ничего не отправляет.
- Raw hook replacement разворачивает reflection throwable и не считает ошибку оригинального Java-метода ошибкой плагина. Нет `ThreadLocal.withInitial`, отсутствующего на API 24.

## Native lifecycle

Исправленный Dobby в pinned AAR сохранён без изменений: `1.1.4-api37.1-bygram.1`, AAR SHA-256 `06051b75f3251a1f70e986d160e77b3e0c3614ef8e5bc9913c814ee94b6628c8`.

Дополнительная A/B-проба локализовала порчу ART/JNI metadata при физическом unhook и последующем GC в Python-процессе. Java bridge теперь удерживает один native dispatcher на уникальный Member до завершения процесса, а close удаляет plugin callbacks и context references. Повторные регистрации переиспользуют dispatcher. Лимит — 1024 разных целей на процесс; полное снятие native-прослойки требует холодного restart. Это видимо в карточке и SDK-документации. Bridge не добавляет глобального отключения JIT/оптимизаций. Pinned LSPlant сохраняет собственные ART init hooks; в Debug его штатный workaround переводит runtime в NonJavaDebuggable, поэтому Debug и Release проверяются отдельно.

Gate допускает только primary x86_64, 64-bit, API 28/35/36/37 и arm64-v8a, 64-bit, API 36. Остальные OS/ABI получают HookBackendUnavailable до загрузки native; core SDK продолжает работать. Это проверенная матрица, а не обещание всех OEM/версий Android.

## Подтверждённые проверки до упаковки

- `python Tools/check_plugins.py`: PASS; 79 отрицательных manifest, 14 settings exchange, 21 page schema, 33 ZIP fixtures, loader/examples/account generations/network/composer/theme; 36 process-death границ до/после AtomicFile commit для install/pending/cancel/rollback/import/remove. Лог `output/plugins-finish-core-final.log`.
- Gate компилируется из production source: positive/negative OS/ABI, lazy availability и кеширование LinkageError. Fixtures не подменяют native dispatch.
- Full Debug Java compile: PASS (`output/plugins-finish-compile-final.log`). Full Release/R8 предыдущего прохода: PASS (`output/plugins-finish-r8-final.log`); новый проход фиксируется отдельно.
- Текущие Java/Python через отдельный DEX в установленном r4, без нового APK: API 24 x86 core PASS (raw backend не загружается), API 28 x86_64 и API 37 x86_64/16 KiB core/hooks PASS. Логи `output/plugins-finish-overlay-final/`. app_process не проверяет Android window/theme renderer; это отдельно проверяется в настоящем приложении.
- Полный source hook fixture в настоящем Debug-процессе: API 35/36/37 x86_64 PASS; POCO X7 Pro / Android 16 API 36 / arm64 / 4 KiB PASS. На телефоне: два владельца, original throwable, private/constructor/overload, reentry, 300 циклов, quarantine, GC, 5 целей и 0 registrations, PID жив; native SHA совпал с pinned AAR. Машинный отчёт `output/plugins-finish-arm-source/report.json`.
- `python Tools/check_bygram_editions.py`: PASS. Дополнительные edition/configuration и финальный артефакт проверяются перед доставкой.

## Изменённые системные границы

Новый проход меняет Full `PluginEntry` на границе UserConfig/account events: перед доставкой сверяется актуальная auth identity, события фильтруются grants. Общие системные страницы Telegram в этом проходе не переписывались; меню/page lifecycle/theme extension points из предыдущего этапа сохранены. Собственные страницы менеджера и SDK изменены, включая native composer preview. FTP/production/MySQL для локального SDK не использовались.

## Финальная приёмка

Итоговый APK этого прохода ещё не упакован. Здесь будут записаны Release runtime, UI/accessibility, измерения, hash и подтверждение доставки. Реальные account/message операции без тестовых аккаунтов не объявляются проверенными; отсутствующие ARM/API комбинации не открываются в gate.
