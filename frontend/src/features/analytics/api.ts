import { z } from 'zod';
export const periods = ['7d', '30d', 'all'] as const;
export type Period = (typeof periods)[number];
const percent = z.number().nullable();
export const daySchema = z.object({
  date: z.string(),
  questionsAnswered: z.number(),
  fullyCorrect: z.number(),
  accuracy: percent,
  earnedPoints: z.number(),
  maxPoints: z.number(),
  testsCompleted: z.number(),
  testTimeSecs: z.number(),
  theoryReads: z.number(),
  lessonCompletions: z.number(),
  plannerTasksCompleted: z.number(),
  assignmentsSubmitted: z.number(),
  errorsResolved: z.number(),
  studyActions: z.number(),
});
const topic = z.object({
  topicId: z.string(),
  titleRu: z.string(),
  titleKz: z.string().nullable(),
  attempts: z.number(),
  mastery: z.number(),
  recentAccuracy: z.number(),
});
export const analyticsSchema = z.object({
  period: z.enum(periods),
  timeZone: z.string(),
  asOf: z.string(),
  from: z.string(),
  to: z.string(),
  hasPracticeHistory: z.boolean(),
  summary: z.object({
    questionsAnswered: z.number(),
    fullyCorrectAnswers: z.number(),
    accuracyPercent: percent,
    earnedPoints: z.number(),
    maxPoints: z.number(),
    pointsPercent: percent,
    testsCompleted: z.number(),
    activeDays: z.number(),
    currentStreak: z.number(),
    errorsResolved: z.number(),
    theoriesRead: z.number(),
    lessonsCompleted: z.number(),
    plannerTasksCompleted: z.number(),
    assignmentsSubmitted: z.number(),
    testTimeSecs: z.number(),
  }),
  comparison: z
    .object({
      available: z.boolean(),
      from: z.string(),
      to: z.string(),
      questionsDelta: percent,
      accuracyDelta: percent,
      pointsPercentDelta: percent,
      testsCompletedDelta: percent,
      activeDaysDelta: percent,
    })
    .nullable(),
  daily: z.array(daySchema),
  heatmap: z.array(daySchema),
  subjects: z.array(
    z.object({
      subjectId: z.string(),
      titleRu: z.string(),
      titleKz: z.string(),
      questionsAnswered: z.number(),
      accuracyPercent: percent,
      pointsPercent: percent,
      attempts: z.number(),
      accuracyDelta: percent,
    }),
  ),
  weakTopics: z.array(topic),
  strongTopics: z.array(topic),
});
export const historySchema = z.object({
  items: z.array(
    z.object({
      id: z.string(),
      kind: z.string(),
      occurredAt: z.string(),
      titleRu: z.string().nullable(),
      titleKz: z.string().nullable(),
      href: z.string(),
    }),
  ),
  total: z.number(),
  page: z.number(),
  size: z.number(),
});
export type Analytics = z.infer<typeof analyticsSchema>;
export type Day = z.infer<typeof daySchema>;
export function activityDate(value: string, timeZone: string, locale: string) {
  const offset = timeZone.match(/^(?:UTC|GMT|UT)?([+-])(\d{2}):(\d{2})(?::(\d{2}))?$/);
  const seconds = offset
    ? (offset[1] === '+' ? 1 : -1) *
      (Number(offset[2]) * 3600 + Number(offset[3]) * 60 + Number(offset[4] || 0))
    : 0;
  return new Intl.DateTimeFormat(locale, {
    timeZone: offset || timeZone === 'Z' || timeZone === 'UT' ? 'UTC' : timeZone,
    year: 'numeric',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(new Date(value).getTime() + seconds * 1000));
}
export const percentage = (value: number | null) =>
  value === null ? '—' : `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;
