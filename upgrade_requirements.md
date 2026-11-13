# Требования к обновлению зависимостей BYgram

Таблицы — ориентиры для оценки миграций. Фактические версии проверяйте в Gradle-файлах перед обновлением.

## Уровни работ

| Уровень | Оценка | Состав работ |
|---|---:|---|
| L1 | 1–4 часа | Обновление версии, сборка и короткий функциональный тест |
| L2 | 1–3 дня | Небольшие изменения API и проверка затронутой подсистемы |
| L3 | 3–7 дней | Миграция устаревшего API или нескольких пользовательских сценариев |
| L4 | 1–4 недели | Сквозная миграция сборки, платежей или крупной подсистемы |

## L1 — низкая трудоёмкость

| Зависимость | Текущая | Целевая stable | Проверка |
|---|---:|---:|---|
| `androidx.core:core` | 1.16.0 | 1.19.0 | Уведомления, permissions, общие Android-утилиты |
| `androidx.exifinterface:exifinterface` | 1.3.6 | 1.4.2 | Ориентация и metadata JPEG/HEIF/AVIF |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 | 1.1.0 | Анимации интерфейса |
| `androidx.mediarouter:mediarouter` | 1.7.0 | 1.8.1 | Cast-кнопка и выбор маршрута |
| `play-services-location` | 21.0.1 | 21.4.0 | Текущая позиция и отмена запросов |
| `androidx.credentials:credentials` | 1.6.0-alpha04 | 1.6.0 | Создание и получение passkey |
| `credentials-play-services-auth` | 1.6.0-alpha04 | 1.6.0 | Fallback до Android 14 |
| `com.google.android.recaptcha:recaptcha` | 18.7.1 | 18.9.3 | Инициализация, токен, ошибки |
| `androidx.fragment:fragment` | 1.8.9 | 1.9.0 | Навигация и восстановление состояния |
| `androidx.annotation:annotation` | 1.7.0 | 1.10.0 | Компиляция `jlatexmath` |
| `checker-qual` | 2.5.2 | 4.2.3 | Компиляция встроенного ExoPlayer |
| `checker-compat-qual` | 2.5.0 | 2.5.6 | Компиляция встроенного ExoPlayer |
| `gson` | 2.11.0 | 2.14.0 | Сериализация логов и runtime-адаптеры |
| `guava` | 31.1-android | 33.7.1-android | Встроенный ExoPlayer; использовать Android-вариант |
| `kotlinpoet` | 1.15.0 | 2.3.0 | Компиляция и результат генерации исходников |

## L2 — средняя трудоёмкость

| Зависимость | Текущая | Целевая stable | Проверка и внедрение |
|---|---:|---:|---|
| `play-services-cast-framework` | 21.4.0 | 22.3.1 | Cast-сессии, очередь и восстановление воспроизведения |
| `androidx.car.app:app` | 1.4.0 | 1.7.0 | Автомобильные экраны, Car API level, исправление CVE-2024-10382 |
| `androidx.car.app:app-projected` | 1.4.0 | 1.7.0 | Android Auto; оценить `MediaPlaybackTemplate` и conversation items |
| `play-services-maps` | 18.1.0 | 20.0.0 | Новый renderer, dark/light scheme, Transit layer, legacy HTTP fallback |
| `play-services-wearable` | 18.0.0 | 20.0.1 | Авторизация и обмен данными на Wear OS |
| `play-services-wallet` | 19.1.0 | 20.0.0 | Google Pay readiness и результат платежа |
| `com.google.android.play:integrity` | 1.3.0 | 1.6.0 | Standard/classic token flow и повтор запросов |
| `isoparser` | 1.0.6 | 1.1.22 | MP4 muxing, HEVC metadata и экспорт видео |
| Huawei Push | 6.5.0.300 | 6.13.0.301* | Токен, background push, реальное Huawei-устройство |
| Huawei Maps | 6.6.0.300 | 6.15.1.324* | Карта, маркеры, lifecycle и obfuscation |

\* Версии Huawei дополнительно подтверждать разрешением артефакта и сборкой Huawei-варианта: metadata Huawei неполные и нестабильные.

## L3 — высокая трудоёмкость

| Зависимость | Текущая | Целевая | Требуемая миграция |
|---|---:|---:|---|
| Play Billing | 8.0.0 | 9.1.0 | Stars/Premium, acknowledge/consume, восстановление покупок, новые response codes; Billing Choice только при продуктовой необходимости |
| `play-services-auth` | 20.4.0 | 22.0.0 | Перенести Google Sign-In на Credential Manager и проверить серверные ID-токены |
| `play-services-safetynet` | 18.0.1 | удалить | Удалить отключённый `SafetyNet.attest()` после полного перехода на Play Integrity/reCAPTCHA |
| App Center Distribute | 3.3.1 | удалить | Перенести распространение сборок на поддерживаемый канал |
| App Center Crashes | 3.3.1 | удалить | Перенести Hockey-сборку на Firebase Crashlytics |
| App Center Analytics | 3.3.1 | удалить | Перенести события на Firebase Analytics с пользовательскими переключателями |

## L4 — очень высокая трудоёмкость

| Компонент | Текущая | Целевая stable | Требуемая миграция |
|---|---:|---:|---|
| Android Gradle Plugin | 8.10.1 | 9.4.0 | Built-in Kotlin, новый DSL/Variant API, `buildSrc`, все APK-варианты и CI |
| Gradle Wrapper | 8.11.1 | 9.7.1 | Выполнять вместе с AGP; AGP 9.4 требует минимум Gradle 9.6 |
| Kotlin Gradle Plugin | 2.1.0 | 2.4.10 | Выполнять вместе с AGP и проверить Java/Kotlin interop |
| Stripe Android | 2.0.2 | 23.17.1 | Новая токенизация, 3DS2/PaymentSheet, backend API; выполнять после toolchain |
| Mobile Vision | 20.1.3 | заменить ML Kit | Перенести QR/barcode и face detection в восьми файлах |

## Не обновлять без отдельной задачи

- Preview: Palette 1.1.0-alpha01, Biometric 1.4.0-alpha07, MediaRouter 1.9.0-alpha01, Car App 1.9.0-alpha01, Credentials 1.7.0-alpha03, Annotation 1.11.0-alpha02, AGP 9.5.0-alpha04 и Kotlin 2.4.20-RC3.
- Уже актуальны: Google Services 4.5.0, Crashlytics Gradle 3.0.8, Firebase BoM 34.18.0, Firebase DataTransport 20.0.1, Firebase App Indexing 20.0.0, ML Kit Language ID 17.0.6, ML Kit Image Labeling 16.0.8, ZXing 3.5.4, NanoHTTPD 2.3.1, Markwon 4.6.2, Moshi 1.15.2, JavaParser 3.28.2 и `desugar_jdk_libs` 2.1.5.

## Порядок внедрения

1. L1 одним пакетом.
2. Car App 1.7 отдельно из-за исправления безопасности.
3. Cast/MediaRouter, Maps, Wearable и Huawei отдельными тематическими пакетами.
4. Billing 9 и удаление SafetyNet.
5. Замена App Center на Firebase.
6. AGP, Gradle и Kotlin.
7. Stripe после toolchain.
8. Mobile Vision → ML Kit отдельной миграцией.

## Обязательное правило отчётности

После обновления и тестирования **каждой зависимости или связанного набора зависимостей** добавьте запись в [changelog.md](changelog.md) до объединения изменений. Обновление не считается завершённым без успешной сборки затронутых вариантов и записи о проверке.

### Шаблон отчёта

```markdown
### YYYY-MM-DD — group:artifact — old → new

- Уровень: L1/L2/L3/L4
- Причина обновления и использованные новые возможности:
- Изменённый старый код:
- Собранные варианты и команды:
- Автоматические тесты:
- Ручные сценарии и устройства/API:
- Предупреждения до/после:
- Известные ограничения или откат:
- Результат: успешно / откат / требуется доработка
```
