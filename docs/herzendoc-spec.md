# Спецификация формата `.herzendoc`

- **Версия спецификации:** `1.0.0`
- **Статус:** Draft for MVP
- **Кодировка:** UTF-8
- **Расширение файлов:** `.herzendoc`

## 1. Назначение
Формат `.herzendoc` предназначен для хранения структурированных интерактивных учебных материалов:
- главы;
- термины и определения;
- вопросы и ключи ответов;
- связи между главами и терминами.

Документ должен быть машиночитаемым для парсера и удобным для ручного редактирования автором курса.

## 2. Версионирование формата
Каждый документ обязан содержать объявление версии:

```text
@meta version="1.0.0" course="informatics-8"
```

Правило сравнения версий: `MAJOR.MINOR.PATCH`.

## 3. Секции и обязательность полей

### 3.1 Метаданные курса
Маркер:

```text
@meta version="1.0.0" course="<course_id>" title="<course_title>"
```

Поля:
- `version` — **обязательно**
- `course` — **обязательно**, уникальный идентификатор курса
- `title` — опционально

### 3.2 Глава
Маркер:

```text
@chapter id="<chapter_id>" title="<chapter_title>" difficulty="<1..5>"
```

Тело главы начинается после маркера и заканчивается перед следующим блоком верхнего уровня.

Поля:
- `id` — **обязательно**, уникален в рамках курса
- `title` — **обязательно**
- `difficulty` — опционально (целое 1..5)

### 3.3 Термин
Маркер:

```text
@term key="<term_key>"
```

Поля:
- `key` — **обязательно**, уникален в рамках курса

### 3.4 Определение
Маркер:

```text
@definition term="<term_key>"
```

Поля:
- `term` — **обязательно**, должен ссылаться на существующий `@term`

### 3.5 Вопрос
Маркер:

```text
@question id="<question_id>" chapter="<chapter_id>" type="single|multi|text"
```

Поля:
- `id` — **обязательно**, уникален в рамках курса
- `chapter` — **обязательно**, ссылка на существующую главу
- `type` — **обязательно**: `single`, `multi`, `text`

### 3.6 Ключ ответа
Маркер:

```text
@key question="<question_id>"
```

Поля:
- `question` — **обязательно**, ссылка на существующий `@question`

## 4. Синтаксис маркеров и экранирование

### 4.1 Общий синтаксис
Любой маркер начинается с `@` в начале строки:

```text
@marker_name attr1="value1" attr2="value2"
```

Ограничения:
- имя маркера: `[a-z][a-z0-9_]*`
- имена атрибутов: `[a-z][a-z0-9_]*`
- значения атрибутов только в двойных кавычках

### 4.2 Спецсимволы внутри значений
Поддерживаются escape-последовательности:
- `\"` — двойная кавычка
- `\\` — обратный слэш
- `\n` — перевод строки
- `\t` — табуляция
- `\@` — символ `@` внутри текста

Пример:

```text
@term key="big_o"
@definition term="big_o"
Нотация \"O(...)\" показывает верхнюю оценку сложности.
```

### 4.3 Комментарии
Строки, начинающиеся с `#`, считаются комментариями и игнорируются парсером.

## 5. DTO-схема промежуточной модели
Парсер возвращает модель:

```text
CourseDoc
 ├── meta: CourseMetaDoc
 ├── chapters: List<ChapterDoc>
 ├── terms: List<TermDoc>
 ├── definitions: List<DefinitionDoc>
 └── questions: List<QuestionDoc>
```

### 5.1 `CourseDoc`
- `version: String`
- `courseId: String`
- `title: String?`
- `chapters: List<ChapterDoc>`
- `terms: List<TermDoc>`
- `definitions: List<DefinitionDoc>`
- `questions: List<QuestionDoc>`

### 5.2 `ChapterDoc`
- `id: String`
- `title: String`
- `difficulty: Integer?`
- `content: String`
- `usesTermKeys: List<String>`
- `prerequisiteChapterIds: List<String>`

### 5.3 `TermDoc`
- `key: String`
- `aliases: List<String>` (опционально, если поддерживается в версии)

### 5.4 `DefinitionDoc`
- `termKey: String`
- `text: String`

### 5.5 `QuestionDoc`
- `id: String`
- `chapterId: String`
- `type: QuestionType`
- `prompt: String`
- `options: List<String>` (для `single|multi`)
- `answerKey: AnswerKeyDoc`

## 6. Правила валидации
Минимальные ошибки валидации:
1. Отсутствует `@meta` или поле `version`.
2. Дубли `chapter.id`, `term.key`, `question.id`.
3. `@definition` ссылается на несуществующий `@term`.
4. `@question.chapter` ссылается на несуществующую главу.
5. `@key.question` ссылается на несуществующий вопрос.
6. Некорректный `type` у `@question`.
7. Некорректная escape-последовательность в атрибутах.

Формат ошибки:
- `code` (например, `TERM_NOT_FOUND`)
- `message`
- `line`
- `column`
- `context` (fragment/marker)

## 7. Примеры файлов
См. каталог `examples/herzendoc/`:
- `valid-course.herzendoc`
- `invalid-missing-meta.herzendoc`
- `invalid-broken-links.herzendoc`

## 8. Политика обратной совместимости

1. **Если MAJOR отличается** (например, документ `2.x`, парсер поддерживает `1.x`):
   - импорт отклоняется;
   - возвращается ошибка `UNSUPPORTED_MAJOR_VERSION`;
   - режим fallback запрещён.

2. **Если MINOR документа выше поддерживаемого** (документ `1.3`, парсер `1.1`):
   - допускается только если новые поля помечены как optional и неизвестные атрибуты игнорируются;
   - парсер записывает предупреждение `MINOR_VERSION_PARTIAL_SUPPORT`.

3. **Если PATCH отличается**:
   - всегда допускается;
   - поведение считается эквивалентным.

4. Для безопасной эволюции формата:
   - новые обязательные поля можно добавлять только при повышении `MAJOR`;
   - deprecated-маркеры сохраняются минимум 1 MAJOR-цикл;
   - для миграций ведётся таблица совместимости в changelog спецификации.
