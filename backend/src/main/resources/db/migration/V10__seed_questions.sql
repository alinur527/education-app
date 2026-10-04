INSERT INTO questions (
    topic_id,
    subject_id,
    topic_ru,
    topic_kz,
    question_ru,
    question_kz,
    options,
    correct_option_id,
    explanation_ru,
    explanation_kz,
    difficulty,
    year,
    is_active
)
SELECT
    t.id,
    t.subject_id,
    t.title_ru,
    t.title_kz,
    'Чему равна производная функции f(x) = x^2?',
    'f(x) = x^2 функциясының туындысы неге тең?',
    '[{"id":"A","textRu":"2x","textKz":"2x"},{"id":"B","textRu":"x","textKz":"x"},{"id":"C","textRu":"x^2","textKz":"x^2"},{"id":"D","textRu":"1","textKz":"1"}]'::jsonb,
    'A',
    'Производная x^2 находится по правилу степени: (x^n)'' = n*x^(n-1), поэтому ответ 2x.',
    'x^2 туындысы дәрежелік функция ережесімен табылады: (x^n)'' = n*x^(n-1), сондықтан жауап 2x.',
    'easy',
    2024,
    TRUE
FROM topics t
WHERE t.title_ru = 'Производная';

INSERT INTO questions (
    topic_id,
    subject_id,
    topic_ru,
    topic_kz,
    question_ru,
    question_kz,
    options,
    correct_option_id,
    explanation_ru,
    explanation_kz,
    difficulty,
    year,
    is_active
)
SELECT
    t.id,
    t.subject_id,
    t.title_ru,
    t.title_kz,
    'Чему равна производная константы 7?',
    '7 тұрақтысының туындысы неге тең?',
    '[{"id":"A","textRu":"7","textKz":"7"},{"id":"B","textRu":"1","textKz":"1"},{"id":"C","textRu":"0","textKz":"0"},{"id":"D","textRu":"x","textKz":"x"}]'::jsonb,
    'C',
    'Производная любой константы равна нулю.',
    'Кез келген тұрақтының туындысы нөлге тең.',
    'easy',
    2024,
    TRUE
FROM topics t
WHERE t.title_ru = 'Производная';

INSERT INTO questions (
    topic_id,
    subject_id,
    topic_ru,
    topic_kz,
    question_ru,
    question_kz,
    options,
    correct_option_id,
    explanation_ru,
    explanation_kz,
    difficulty,
    year,
    is_active
)
SELECT
    t.id,
    t.subject_id,
    t.title_ru,
    t.title_kz,
    'Чему равен log10(100)?',
    'log10(100) неге тең?',
    '[{"id":"A","textRu":"1","textKz":"1"},{"id":"B","textRu":"2","textKz":"2"},{"id":"C","textRu":"10","textKz":"10"},{"id":"D","textRu":"100","textKz":"100"}]'::jsonb,
    'B',
    'Так как 10^2 = 100, то log10(100) = 2.',
    '10^2 = 100 болғандықтан, log10(100) = 2.',
    'easy',
    2024,
    TRUE
FROM topics t
WHERE t.title_ru = 'Логарифмы';

INSERT INTO questions (
    topic_id,
    subject_id,
    topic_ru,
    topic_kz,
    question_ru,
    question_kz,
    options,
    correct_option_id,
    explanation_ru,
    explanation_kz,
    difficulty,
    year,
    is_active
)
SELECT
    t.id,
    t.subject_id,
    t.title_ru,
    t.title_kz,
    'Какое выражение равно loga(xy)?',
    'loga(xy) қандай өрнекке тең?',
    '[{"id":"A","textRu":"loga(x) + loga(y)","textKz":"loga(x) + loga(y)"},{"id":"B","textRu":"loga(x) - loga(y)","textKz":"loga(x) - loga(y)"},{"id":"C","textRu":"loga(x) * loga(y)","textKz":"loga(x) * loga(y)"},{"id":"D","textRu":"loga(x / y)","textKz":"loga(x / y)"}]'::jsonb,
    'A',
    'Логарифм произведения равен сумме логарифмов множителей.',
    'Көбейтіндінің логарифмі көбейткіштер логарифмдерінің қосындысына тең.',
    'medium',
    2024,
    TRUE
FROM topics t
WHERE t.title_ru = 'Логарифмы';

INSERT INTO questions (
    topic_id,
    subject_id,
    topic_ru,
    topic_kz,
    question_ru,
    question_kz,
    options,
    correct_option_id,
    explanation_ru,
    explanation_kz,
    difficulty,
    year,
    is_active
)
SELECT
    t.id,
    t.subject_id,
    t.title_ru,
    t.title_kz,
    'Кто считается одним из основателей Казахского ханства?',
    'Қазақ хандығының негізін қалаушылардың бірі кім?',
    '[{"id":"A","textRu":"Керей хан","textKz":"Керей хан"},{"id":"B","textRu":"Абылай хан","textKz":"Абылай хан"},{"id":"C","textRu":"Тауке хан","textKz":"Тәуке хан"},{"id":"D","textRu":"Кенесары хан","textKz":"Кенесары хан"}]'::jsonb,
    'A',
    'Казахское ханство связывают с именами Керея и Жанибека.',
    'Қазақ хандығы Керей мен Жәнібек есімдерімен байланыстырылады.',
    'easy',
    2023,
    TRUE
FROM topics t
WHERE t.title_ru = 'Казахское ханство';

INSERT INTO questions (
    topic_id,
    subject_id,
    topic_ru,
    topic_kz,
    question_ru,
    question_kz,
    options,
    correct_option_id,
    explanation_ru,
    explanation_kz,
    difficulty,
    year,
    is_active
)
SELECT
    t.id,
    t.subject_id,
    t.title_ru,
    t.title_kz,
    'Какая физическая величина измеряется в ньютонах?',
    'Қандай физикалық шама ньютонмен өлшенеді?',
    '[{"id":"A","textRu":"Скорость","textKz":"Жылдамдық"},{"id":"B","textRu":"Сила","textKz":"Күш"},{"id":"C","textRu":"Масса","textKz":"Масса"},{"id":"D","textRu":"Работа","textKz":"Жұмыс"}]'::jsonb,
    'B',
    'Ньютон — единица измерения силы в системе СИ.',
    'Ньютон — ХБЖ жүйесіндегі күштің өлшем бірлігі.',
    'easy',
    2024,
    TRUE
FROM topics t
WHERE t.title_ru = 'Механика';
