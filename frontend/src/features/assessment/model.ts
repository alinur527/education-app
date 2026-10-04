import { z } from 'zod';
export const assessmentOption = z.object({
  id: z.string(),
  textRu: z.string(),
  textKz: z.string().nullish(),
});
export const pairSchema = z.object({ leftId: z.string(), rightId: z.string() });
export const answerSchema = z.object({
  selectedOptionId: z.string().optional(),
  selectedOptionIds: z.array(z.string()).optional(),
  pairs: z.array(pairSchema).optional(),
});
export type Answer = z.infer<typeof answerSchema>;
export const assessmentSchema = z.object({
  questionType: z.enum(['SINGLE_CHOICE', 'MULTIPLE_SELECT', 'MATCHING']).optional(),
  options: z.array(assessmentOption).optional(),
  leftOptions: z.array(assessmentOption).optional(),
  correctOptionId: z.string().nullish(),
  correctOptionIds: z.array(z.string()).optional(),
  correctPairs: z.array(pairSchema).optional(),
  scoringPolicy: z.string().optional(),
  maxPoints: z.number().optional(),
  contextKey: z.string().optional(),
});
export type Assessment = z.infer<typeof assessmentSchema>;
export const contextSchema = z.object({
  titleRu: z.string(),
  titleKz: z.string().nullish(),
  contentRu: z.string().nullish(),
  contentKz: z.string().nullish(),
});
export function completeAnswer(q: Assessment, a: Answer | undefined) {
  if (!a) return false;
  if (q.questionType === 'MULTIPLE_SELECT') return Boolean(a.selectedOptionIds?.length);
  if (q.questionType === 'MATCHING')
    return (
      q.leftOptions?.every((o) => a.pairs?.some((p) => p.leftId === o.id && p.rightId)) ?? false
    );
  return Boolean(a.selectedOptionId);
}
