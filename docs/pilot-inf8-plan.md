# Пилот «Информатика 8 класс» — план запуска и анализ

## 1) Пилотный курс
- Курс подготовлен в `examples/herzendoc/inf-8-pilot.herzendoc`.
- Содержит:
  - 5 терминов с определениями
  - 4 главы с prerequisite-цепочкой
  - 5 вопросов входной диагностики

## 2) Ограниченная группа и сбор telemetry + feedback
- Группа: 20–30 учеников, 2 подгруппы по 10–15.
- Варианты рекомендателя:
  - `baseline` (только граф)
  - `hybrid` (граф + персонализация)
- Сбор telemetry:
  - стандартные события: `term_click`, `chapter_open`, `chapter_complete`, `answer_submit`, `recommendation_accept`
  - эндпоинт: `POST /api/analytics/events`
- Сбор feedback:
  - после главы: понятность объяснения, релевантность рекомендации, сложность вопросов (шкала 1–5)
  - формат: `payload` в событиях + внешний опрос

## 3) Сравнение baseline vs hybrid
1. Вызывать рекомендации с `recommenderVersion=baseline|hybrid`:
   - `GET /api/recommendations/next?...&recommenderVersion=baseline`
   - `GET /api/recommendations/next?...&recommenderVersion=hybrid`
2. Выполнить пересчёт агрегатов:
   - `POST /api/analytics/recompute`
3. Получить сравнение:
   - `GET /api/analytics/compare?courseId=inf-8-pilot`

Ключевые метрики:
- Learning Gain
- Time-to-Mastery
- Recommendation Acceptance
- Drop-off
- Prerequisite Violation

## 4) Выявление узких мест
Использовать:
- `GET /api/analytics/bottlenecks?courseId=inf-8-pilot`
- `GET /api/analytics/overview?courseId=inf-8-pilot`

Правила интерпретации:
- Высокий `term_click` по одному термину → слабая формулировка определения / связи
- Высокий `drop_off` в главе → плохие вопросы или завышенная сложность
- Высокий `prerequisite_violation` → ошибки в траектории/связях prerequisite
- Низкий `recommendation_acceptance` при хорошем `learning_gain` → проблема explainability

## 5) Backlog итерации 2
1. Адаптивный тест:
   - ветвление по confidence/mastery
   - ранняя остановка при устойчивой оценке
2. Улучшенный similarity:
   - взвешивание по prerequisite-терминам
   - time-decay для старых наблюдений
3. A/B инфраструктура:
   - assignment-сервис
   - sticky-экспозиция по student_id
   - отчётность по статистической значимости
4. Explainability v2:
   - структурированный reason JSON
   - пользовательские «почему эта глава?» подсказки
5. Качество контента:
   - ревизия вопросов с высоким drop-off
   - ревизия терминов с частыми повторными кликами
