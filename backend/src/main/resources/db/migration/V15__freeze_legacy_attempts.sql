-- Original historical wording cannot be recovered; freeze the content as it exists
-- at upgrade so subsequent editorial changes cannot alter legacy review screens.
UPDATE test_sessions s
SET question_snapshot = (
    SELECT jsonb_agg(jsonb_build_object(
        'id', q.id, 'topicId', q.topic_id,
        'topicRu', q.topic_ru, 'topicKz', q.topic_kz,
        'questionRu', q.question_ru, 'questionKz', q.question_kz,
        'options', q.options, 'correctOptionId', q.correct_option_id,
        'explanationRu', q.explanation_ru, 'explanationKz', q.explanation_kz,
        'difficulty', q.difficulty, 'year', q.year
    ) ORDER BY ids.position)
    FROM jsonb_array_elements_text(s.question_ids) WITH ORDINALITY AS ids(id, position)
    JOIN questions q ON q.id = ids.id::uuid
), topic_id = (
    SELECT q.topic_id FROM questions q
    WHERE q.id = (s.question_ids ->> 0)::uuid
)
WHERE s.question_snapshot IS NULL;
