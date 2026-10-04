import { assessmentSchema } from '../assessment/model';
import { z } from 'zod';
const optionalText = z
  .string()
  .nullish()
  .transform((v) => v ?? undefined)
  .optional();
const draftTranslation = z
  .string()
  .nullish()
  .transform((v) => v ?? '');
export const kinds = [
  'SUBJECT',
  'TOPIC',
  'THEORY',
  'QUESTION',
  'COURSE',
  'MODULE',
  'LESSON',
  'QUIZ',
  'ASSIGNMENT',
  'CONTEXT',
] as const;
export type Kind = (typeof kinds)[number];
export const kindLabels: Record<Kind, readonly [string, string]> = {
  SUBJECT: ['Предмет', 'Пән'],
  TOPIC: ['Тема', 'Тақырып'],
  THEORY: ['Теория', 'Теория'],
  QUESTION: ['Вопрос', 'Сұрақ'],
  COURSE: ['Курс', 'Курс'],
  MODULE: ['Модуль', 'Модуль'],
  LESSON: ['Урок', 'Сабақ'],
  QUIZ: ['Тест урока', 'Сабақ тесті'],
  ASSIGNMENT: ['Задание', 'Тапсырма'],
  CONTEXT: ['Общий контекст', 'Ортақ мәтін'],
};
export const statusLabels = {
  DRAFT: ['Черновик', 'Жоба'],
  REVIEW: ['На проверке', 'Тексеруде'],
  PUBLISHED: ['Опубликовано', 'Жарияланды'],
  ARCHIVED: ['В архиве', 'Мұрағатта'],
} as const;
export const blockSchema = z.object({
  type: z.enum([
    'TEXT',
    'HEADING',
    'IMAGE',
    'FILE',
    'VIDEO',
    'QUOTE',
    'FORMULA',
    'CALLOUT',
    'PRACTICE',
    'CODE',
    'TABLE',
  ]),
  textRu: optionalText,
  textKz: optionalText,
  materialId: optionalText,
  url: optionalText,
  topicId: optionalText,
});
export type Block = z.infer<typeof blockSchema>;
export const option = z.object({ id: z.string(), textRu: z.string(), textKz: draftTranslation });
export const questionPayload = z.object({
  ...assessmentSchema.shape,
  titleRu: z.string(),
  titleKz: draftTranslation,
  options: z.array(option),
  correctOptionId: optionalText,
  explanationRu: optionalText,
  explanationKz: optionalText,
});
export type QuizQuestion = z.infer<typeof questionPayload>;
export const payloadSchema = z.object({
  ...assessmentSchema.shape,
  contextId: optionalText,
  contextVersion: z.number().nullish(),
  answerEvidence: z.string().optional(),
  difficultyReason: z.string().optional(),
  sourceIds: z.array(z.string()).optional(),
  reviewChecks: z.record(z.string(), z.boolean()).optional(),
  contentLanguage: optionalText,
  studiedLanguage: optionalText,
  curriculumVariant: optionalText,
  examVersion: optionalText,
  category: z.enum(['MANDATORY', 'PROFILE', 'OTHER']).optional(),
  curriculum: z.record(z.string(), z.string()).optional(),
  offlineAllowed: z.boolean().optional(),
  titleRu: z.string(),
  titleKz: draftTranslation,
  descriptionRu: z.string().nullish(),
  descriptionKz: z.string().nullish(),
  contentRu: z.string().nullish(),
  contentKz: z.string().nullish(),
  sortOrder: z
    .number()
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  icon: z.string().nullish(),
  color: z.string().nullish(),
  durationMinutes: z
    .number()
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  options: z.array(option).optional(),
  correctOptionId: optionalText,
  explanationRu: z.string().nullish(),
  explanationKz: z.string().nullish(),
  difficulty: z
    .enum(['easy', 'medium', 'hard'])
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  year: z.number().nullish(),
  sourceType: z
    .enum(['OFFICIAL_SAMPLE', 'EDITOR_CREATED', 'AI_GENERATED', 'IMPORTED'])
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  sourceUrl: optionalText,
  sourceName: optionalText,
  language: z
    .enum(['ru', 'kz', 'both'])
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  verified: z
    .boolean()
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  blocks: z.array(blockSchema).default([]),
  visibility: z
    .enum(['PUBLIC', 'PRIVATE'])
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  selfEnroll: z
    .boolean()
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  dueAt: optionalText,
  maxScore: z
    .number()
    .nullish()
    .transform((v) => v ?? undefined)
    .optional(),
  questions: z.array(questionPayload).optional(),
});
export type Payload = z.infer<typeof payloadSchema>;
export const contentSchema = z.object({
  id: z.string().uuid(),
  kind: z.enum(kinds),
  parentId: z.string().uuid().nullable(),
  ownerId: z.string().uuid().nullable(),
  titleRu: z.string(),
  titleKz: z.string(),
  payload: payloadSchema,
  status: z.enum(['DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED']),
  version: z.number(),
  publishedVersion: z.number().nullable(),
  createdBy: z.string().nullable(),
  updatedBy: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Content = z.infer<typeof contentSchema>;
export const contentPage = z.object({
  items: z.array(contentSchema.omit({ payload: true })),
  page: z.number(),
  size: z.number(),
  total: z.number(),
});
export const publishedSchema = z.object({
  id: z.string(),
  kind: z.enum(kinds),
  parentId: z.string().nullable(),
  content: payloadSchema,
});
export const materialSchema = z.object({
  id: z.string(),
  contentId: z.string(),
  titleRu: z.string(),
  titleKz: z.string(),
  originalFileName: z.string(),
  mimeType: z.string(),
  size: z.number(),
  published: z.boolean(),
  scanStatus: z
    .enum(['CLEAN', 'UNSCANNED', 'UNSCANNED_LEGACY', 'INFECTED', 'SCAN_FAILED'])
    .optional(),
});
export type Material = z.infer<typeof materialSchema>;
export const materialsSchema = z.array(materialSchema);
export const savedSchema = z.object({ saved: z.boolean() });
export const idSchema = z.object({ id: z.string().uuid() });
export const blankQuestion = (): QuizQuestion => ({
  titleRu: '',
  titleKz: '',
  options: [
    { id: 'A', textRu: '', textKz: '' },
    { id: 'B', textRu: '', textKz: '' },
  ],
  correctOptionId: 'A',
});
