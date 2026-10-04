import { assessmentSchema, answerSchema, contextSchema } from './features/assessment/model';
import { z } from 'zod';

export const userSchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  language: z.enum(['ru', 'kz']),
  role: z.enum(['STUDENT', 'TEACHER', 'CONTENT_EDITOR', 'ADMIN']),
});
export type User = z.infer<typeof userSchema>;
export const authSchema = z.object({ token: z.string().min(1), user: userSchema });
export const subjectSchema = z.object({
  id: z.string().uuid(),
  nameRu: z.string(),
  nameKz: z.string(),
  icon: z.string().nullable(),
  color: z.string().nullable(),
  questionCount: z.number(),
  durationMinutes: z.number(),
  category: z.string().nullish(),
});
export type Subject = z.infer<typeof subjectSchema>;
export const topicSchema = z.object({
  id: z.string().uuid(),
  subjectId: z.string().uuid(),
  titleRu: z.string(),
  titleKz: z.string().nullable(),
  descriptionRu: z.string().nullable(),
  descriptionKz: z.string().nullable(),
  sortOrder: z.number(),
  questionCount: z.number(),
  theoryCount: z.number(),
  contentRole: z.string().optional(),
  curriculum: z
    .object({
      extractionStatus: z.string().optional(),
      sourceId: z.string().optional(),
      officialCode: z.string().optional(),
      page: z.string().optional(),
      sectionRu: z.string().optional(),
      sectionKz: z.string().optional(),
      variant: z.string().optional(),
      examVersion: z.string().optional(),
    })
    .nullish(),
  documentLanguage: z.string().nullish(),
  sourceUrl: z.string().nullish(),
});
export const theorySchema = z.object({
  id: z.string().uuid(),
  topicId: z.string().uuid(),
  titleRu: z.string(),
  titleKz: z.string().nullable(),
  contentRu: z.string(),
  contentKz: z.string().nullable(),
  sortOrder: z.number(),
});
export const optionSchema = z.object({ id: z.string(), textRu: z.string(), textKz: z.string() });
export const questionSchema = z.object({
  sessionId: z.string().uuid(),
  index: z.number(),
  totalQuestions: z.number(),
  questionId: z.string().uuid(),
  topicId: z.string().uuid().nullable(),
  topicRu: z.string().nullable(),
  topicKz: z.string().nullable(),
  questionRu: z.string(),
  questionKz: z.string().nullable(),
  options: z.array(optionSchema).min(2),
  difficulty: z.string(),
  year: z.number().nullable(),
  assessment: assessmentSchema.nullish(),
  context: contextSchema.nullish(),
});
export const sessionSchema = z.object({
  sessionId: z.string().uuid(),
  topicId: z.string().uuid().nullable(),
  subjectId: z.string().uuid().nullable(),
  status: z.enum(['IN_PROGRESS', 'COMPLETED', 'ABANDONED']),
  totalQuestions: z.number(),
  startedAt: z.string(),
  answers: z.array(
    z.object({
      questionId: z.string().uuid(),
      selectedOptionId: z.string().nullable(),
      answer: answerSchema.nullish(),
    }),
  ),
  questionIds: z.array(z.string().uuid()).optional(),
  practiceMode: z.string().optional(),
  deadlineAt: z.string().nullish(),
  maxPoints: z.number().nullish(),
});
export const startSchema = z.object({
  sessionId: z.string().uuid(),
  totalQuestions: z.number(),
  startedAt: z.string(),
});
export const receiptSchema = z.object({
  questionId: z.string().uuid(),
  selectedOptionId: z.string().nullable(),
  answer: answerSchema.nullish(),
});
export const finishSchema = z.object({
  sessionId: z.string().uuid(),
  correctAnswers: z.number(),
  totalQuestions: z.number(),
  score: z.number(),
  timeTakenSecs: z.number(),
  earnedPoints: z.number().nullish(),
  maxPoints: z.number().nullish(),
});
export const resultsSchema = finishSchema.extend({
  topicId: z.string().uuid().nullable(),
  subjectId: z.string().uuid().nullable(),
  status: z.string(),
  answers: z.array(
    z.object({
      questionId: z.string().uuid(),
      questionRu: z.string(),
      questionKz: z.string().nullable(),
      options: z.array(optionSchema),
      selectedOptionId: z.string().nullable(),
      correctOptionId: z.string().nullable(),
      assessment: assessmentSchema.nullish(),
      answer: answerSchema.nullish(),
      context: contextSchema.nullish(),
      earnedPoints: z.number().optional(),
      maxPoints: z.number().optional(),
      topicId: z.string().nullish(),
      subjectId: z.string().nullish(),
      isCorrect: z.boolean(),
      explanationRu: z.string().nullable(),
      explanationKz: z.string().nullable(),
    }),
  ),
});
const names = { nameRu: z.string(), nameKz: z.string() };
export const statsSchema = z.object({
  testsTaken: z.number(),
  averageScore: z.number(),
  bestScore: z.number(),
  timeTakenSecs: z.number(),
  recentAttempts: z.array(
    z.object({
      sessionId: z.string().uuid(),
      topicId: z.string().uuid().nullable(),
      subjectId: z.string().uuid().nullable(),
      ...names,
      score: z.number(),
      totalQuestions: z.number(),
      correctAnswers: z.number(),
      completedAt: z.string(),
    }),
  ),
  subjects: z.array(
    z.object({
      subjectId: z.string().uuid(),
      ...names,
      testsTaken: z.number(),
      averageScore: z.number(),
      bestScore: z.number(),
    }),
  ),
  activeAttempts: z.array(
    z.object({
      sessionId: z.string().uuid(),
      topicId: z.string().uuid().nullable(),
      ...names,
      totalQuestions: z.number(),
      answeredQuestions: z.number(),
    }),
  ),
});
export type Statistics = z.infer<typeof statsSchema>;
export const TOKEN_KEY = 'education.session';
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
  ) {
    super(code);
  }
}
export async function request<T>(
  path: string,
  schema: z.ZodType<T>,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  const token = sessionStorage.getItem(TOKEN_KEY);
  let response: Response;
  try {
    response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') || ''}/api${path}`,
      {
        method: options.method || 'GET',
        signal: options.signal
          ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)])
          : AbortSignal.timeout(15000),
        headers: {
          ...(options.body && !(options.body instanceof FormData)
            ? { 'Content-Type': 'application/json' }
            : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body:
          options.body instanceof FormData
            ? options.body
            : options.body
              ? JSON.stringify(options.body)
              : undefined,
      },
    );
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'NETWORK');
  }
  if (!response.ok) {
    if (
      response.status === 401 &&
      token === sessionStorage.getItem(TOKEN_KEY) &&
      !['/auth/login', '/auth/register'].includes(path)
    ) {
      sessionStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event('session-expired'));
    }
    const failure = (await response.json().catch(() => ({}))) as { code?: string };
    throw new ApiError(response.status, failure.code || `HTTP_${response.status}`);
  }
  let data: unknown;
  try {
    data = await response.json();
  } catch {
    throw new ApiError(0, 'INVALID_RESPONSE');
  }
  const parsed = schema.safeParse(data);
  if (!parsed.success) throw new ApiError(0, 'INVALID_RESPONSE');
  return parsed.data;
}
