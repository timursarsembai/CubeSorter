# CubeSorter

(Прежнее название: DanaBala Numbers (RUS))

Образовательное Android‑приложение: тренировки и мини‑игры для изучения цифр, счёта и логики детьми.

## Возможности
- Наглядные тренировки: распознавание и сравнение чисел, счёт предметов, последовательности, рисование цифр и др.
- Мини‑игры на внимание, скорость и логику (Шульте, Сортер, Цифровой ряд и др.).
- Простые жесты и крупные элементы UI, дружелюбные визуалы и анимации.
- Озвучка на русском через Text‑to‑Speech (при наличии данных на устройстве).

## Содержимое
Тренировки (основные): NumberIntroduction, NumberRecognition, Counting, ObjectCounting, Matching, AudioMatching, NumberDrawing, Ascending/Descending Sequence, NumberComparison, KidsComparison, RowError. Игры (основные): BubbleCatch, BlockMatch, SchulteNumbers, SnakeMath, Sorter, Simple Sudoku.

## Требования
- Android Studio Koala+ (или Giraffe+)
- JDK 11
- Android SDK: minSdk 24, target/compileSdk 36

## Установка и запуск
1) Клонировать репозиторий (обновите URL на фактический, если он изменён после переименования):

```bash
git clone https://github.com/timursarsembai/CubeSorter.git
```

2) Открыть проект в Android Studio и дождаться синхронизации Gradle.
3) Запустить конфигурацию «app» на устройстве/эмуляторе (Android 7.0+).

Сборка через Gradle:

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/`.

## Технологии и стек
- Kotlin, Coroutines
- AndroidX: AppCompat, Material, ConstraintLayout
- Jetpack: Lifecycle (ViewModel/Livedata/Runtime), Fragment, Navigation
- Room (подключён для потенциального хранения), RecyclerView, CardView
- ViewBinding, TTS (android.speech.tts)

## Структура проекта
```
app/
  src/main/
    java/com/timursarsembayev/danabalanumbers/  ← активности и пользовательские вью
    res/                                        ← макеты, стили, строки, drawable
  build.gradle.kts
```

Ключевые точки входа:
- `MainActivity` → `MathExercisesActivity` (главный экран с вкладками Тренировки/Игры)
- Активности упражнений и игр (…Activity) имеют собственные макеты в `res/layout/` и при необходимости экраны результатов (…ResultsActivity).

## Качество и стиль кода
- Kotlin style guide, null‑safety, иммутабельность, extension‑ы по месту.
- UI — через XML‑макеты и кастомные View при сложной отрисовке (например, игровые поля).
- Разделение логики от рисования в кастомных вью; минимизация аллокаций в onDraw.
- Проверка на конфликты/ошибки состояния — внутри игровых представлений.

## Локализация и озвучка
- Базовая локаль — ru-RU.
- Для активностей с озвучкой требуется установленный русский TTS‑язык на устройстве.

## Доступность
- Крупные элементы управления, контрастные цвета.
- По возможности использовать contentDescription для значимых иконок.

## Контрибуция
PR приветствуются: багфиксы, улучшения UX/UI, новые мини‑игры/тренировки. Рекомендации:
- Соберите приложение на реальном устройстве (Android 7.0+).
- Следуйте принятым зависимостям и архитектуре; не добавляйте тяжёлые библиотеки без необходимости.
- При добавлении активностей: создайте Activity + layout, карточку на главном экране и (при необходимости) экран результатов.

—

Вопросы и предложения — через Issues репозитория.
