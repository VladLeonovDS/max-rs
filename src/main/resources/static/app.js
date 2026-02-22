const state = {
  sessionId: null,
  studentId: null,
  courseId: null,
  questions: [],
  currentQuestionIdx: 0,
  attempts: [],
  coveredTerms: new Set(),
  termDefinitions: {
    алгоритм: 'Последовательность шагов для решения задачи.',
    граф: 'Модель данных из вершин и рёбер между ними.',
    сложность: 'Оценка затрат ресурсов алгоритма.'
  },
  readingAnchorY: 0,
  currentChapterId: 'chapter-1',
  recommenderVersion: 'hybrid',
  recommendedChapterId: null
};

const chapterTemplate = [
  'В этой главе мы рассматриваем ',
  { term: 'алгоритм' },
  ', а также как представить знания через ',
  { term: 'граф' },
  '. Для оценки подходов важна ',
  { term: 'сложность' },
  ' вычислений.'
];

const assessmentEls = {
  studentId: document.getElementById('student-id'),
  courseId: document.getElementById('course-id'),
  startBtn: document.getElementById('start-assessment'),
  progressWrap: document.getElementById('assessment-progress-wrap'),
  progressText: document.getElementById('assessment-progress-text'),
  progressPercent: document.getElementById('assessment-progress-percent'),
  progressBar: document.getElementById('assessment-progress-bar'),
  form: document.getElementById('question-form'),
  prompt: document.getElementById('question-prompt'),
  options: document.getElementById('question-options'),
  nextBtn: document.getElementById('next-question'),
  submitBtn: document.getElementById('submit-assessment'),
  status: document.getElementById('assessment-status')
};

const readingEls = {
  chapter: document.getElementById('chapter-content'),
  covered: document.getElementById('covered-terms'),
  remaining: document.getElementById('remaining-terms'),
  panel: document.getElementById('definition-panel'),
  term: document.getElementById('definition-term'),
  text: document.getElementById('definition-text'),
  backBtn: document.getElementById('back-to-reading')
};

const recommendationEls = {
  loadBtn: document.getElementById('load-recommendation'),
  acceptBtn: document.getElementById('accept-recommendation'),
  result: document.getElementById('recommendation-result')
};

renderChapter();
restoreReadingPosition();
renderMasteryIndicators();

assessmentEls.startBtn.addEventListener('click', startAssessment);
assessmentEls.form.addEventListener('submit', handleNextQuestion);
assessmentEls.submitBtn.addEventListener('click', submitAssessment);
readingEls.backBtn.addEventListener('click', closeDefinitionPanel);
recommendationEls.loadBtn.addEventListener('click', loadRecommendation);
recommendationEls.acceptBtn.addEventListener('click', acceptRecommendation);
window.addEventListener('scroll', persistReadingPosition);

async function startAssessment() {
  state.studentId = assessmentEls.studentId.value.trim();
  state.courseId = assessmentEls.courseId.value.trim();
  if (!state.studentId || !state.courseId) return;

  assessmentEls.status.textContent = 'Запрашиваем вопросы...';
  const response = await fetch('/api/assessment/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ studentId: state.studentId, courseId: state.courseId })
  });
  const payload = await response.json();
  state.sessionId = payload.sessionId;
  state.questions = payload.questions || [];
  state.currentQuestionIdx = 0;
  state.attempts = [];

  assessmentEls.progressWrap.hidden = false;
  assessmentEls.form.hidden = state.questions.length === 0;
  assessmentEls.status.textContent = state.questions.length
    ? 'Выберите вариант ответа и продолжите.'
    : 'Вопросы не получены.';

  renderQuestion();
  emitEvent('chapter_open', { chapterId: state.currentChapterId, payload: 'source=assessment-start' });
}


function handleNextQuestion(event) {
  event.preventDefault();
  const selected = assessmentEls.options.querySelector('input[name="option"]:checked');
  if (!selected) return;

  const question = state.questions[state.currentQuestionIdx];
  state.attempts.push({
    sessionId: state.sessionId,
    questionId: question.questionId,
    selectedOption: selected.value
  });

  state.currentQuestionIdx += 1;
  if (state.currentQuestionIdx >= state.questions.length) {
    assessmentEls.nextBtn.hidden = true;
    assessmentEls.submitBtn.hidden = false;
    assessmentEls.status.textContent = 'Все ответы собраны. Отправьте результаты.';
    updateAssessmentProgress();
    return;
  }

  renderQuestion();
}

async function submitAssessment() {
  const response = await fetch('/api/assessment/submit', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: state.studentId,
      courseId: state.courseId,
      sessionId: state.sessionId,
      attempts: state.attempts
    })
  });
  const payload = await response.json();

  assessmentEls.status.textContent = payload.needsRefinement
    ? 'Нужен уточняющий мини-тест.'
    : 'Результаты сохранены.';
  emitEvent('answer_submit', { chapterId: state.currentChapterId, payload: `attempts=${state.attempts.length}` });

  const termKeys = Object.keys(payload.profile?.terms || {});
  state.coveredTerms = new Set(termKeys.filter((key) => (payload.profile.terms[key]?.masteryScore ?? 0) >= 0.6));
  renderMasteryIndicators();
}

function renderQuestion() {
  const question = state.questions[state.currentQuestionIdx];
  if (!question) return;

  assessmentEls.prompt.textContent = question.prompt;
  assessmentEls.options.innerHTML = '';
  question.options.forEach((option, idx) => {
    const id = `q-${state.currentQuestionIdx}-${idx}`;
    assessmentEls.options.insertAdjacentHTML('beforeend', `
      <label for="${id}">
        <input id="${id}" type="radio" name="option" value="${option}" /> ${option}
      </label><br/>
    `);
  });

  assessmentEls.nextBtn.hidden = false;
  assessmentEls.submitBtn.hidden = true;
  updateAssessmentProgress();
}

function updateAssessmentProgress() {
  const total = state.questions.length || 1;
  const completed = Math.min(state.currentQuestionIdx, total);
  const percent = Math.round((completed / total) * 100);
  assessmentEls.progressText.textContent = `${completed} / ${state.questions.length}`;
  assessmentEls.progressPercent.textContent = `${percent}%`;
  assessmentEls.progressBar.style.width = `${percent}%`;
}

function renderChapter() {
  readingEls.chapter.innerHTML = '';
  chapterTemplate.forEach((part) => {
    if (typeof part === 'string') {
      readingEls.chapter.insertAdjacentText('beforeend', part);
      return;
    }

    const termButton = document.createElement('button');
    termButton.className = 'term-chip';
    termButton.type = 'button';
    termButton.textContent = part.term;
    termButton.addEventListener('click', () => openDefinitionPanel(part.term));
    readingEls.chapter.appendChild(termButton);
  });
}

function openDefinitionPanel(term) {
  emitEvent('term_click', { chapterId: state.currentChapterId, payload: `term=${term}` });
  state.readingAnchorY = window.scrollY;
  readingEls.term.textContent = term;
  readingEls.text.textContent = state.termDefinitions[term] || 'Определение временно отсутствует.';
  readingEls.panel.hidden = false;
}

function closeDefinitionPanel() {
  readingEls.panel.hidden = true;
  emitEvent('chapter_complete', { chapterId: state.currentChapterId, payload: `scroll=${state.readingAnchorY}` });
  window.scrollTo({ top: state.readingAnchorY, behavior: 'smooth' });
}

function renderMasteryIndicators() {
  const terms = Object.keys(state.termDefinitions);
  const covered = terms.filter((term) => state.coveredTerms.has(term));
  const remaining = terms.filter((term) => !state.coveredTerms.has(term));

  readingEls.covered.innerHTML = covered.map((term) => `<li class="covered">${term}</li>`).join('') || '<li class="covered">Пока нет</li>';
  readingEls.remaining.innerHTML = remaining.map((term) => `<li class="remaining">${term}</li>`).join('') || '<li class="remaining">Нет</li>';
}

async function loadRecommendation() {
  if (!state.studentId || !state.courseId) {
    recommendationEls.result.textContent = 'Сначала запустите входной тест.';
    return;
  }

  const response = await fetch(`/api/recommendations/next?studentId=${encodeURIComponent(state.studentId)}&courseId=${encodeURIComponent(state.courseId)}&recommenderVersion=${encodeURIComponent(state.recommenderVersion)}`);
  const payload = await response.json();

  state.recommendedChapterId = payload.chapterId || null;
  recommendationEls.result.innerHTML = `
    <p><strong>Глава:</strong> ${payload.chapterId || '—'}</p>
    <p><strong>Причина выбора:</strong> ${payload.reason || '—'}</p>
    <p><strong>Скор:</strong> ${(payload.score ?? 0).toFixed(2)}</p>
  `;
}


function acceptRecommendation() {
  emitEvent('recommendation_accept', { chapterId: state.recommendedChapterId || state.currentChapterId, payload: 'accepted=true' });
  recommendationEls.result.insertAdjacentHTML('beforeend', '<p><em>Рекомендация принята.</em></p>');
}

function persistReadingPosition() {
  localStorage.setItem('reading-position-y', String(window.scrollY));
}

function restoreReadingPosition() {
  const value = localStorage.getItem('reading-position-y');
  if (!value) return;
  const top = Number(value);
  if (!Number.isNaN(top)) {
    window.scrollTo({ top, behavior: 'auto' });
  }
}


function emitEvent(eventType, options = {}) {
  if (!state.studentId || !state.courseId) return;
  const event = {
    studentId: state.studentId,
    courseId: state.courseId,
    chapterId: options.chapterId || null,
    eventType,
    ts: new Date().toISOString(),
    payload: options.payload || '',
    recommenderVersion: state.recommenderVersion
  };
  fetch('/api/analytics/events', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ events: [event] })
  }).catch(() => undefined);
}
