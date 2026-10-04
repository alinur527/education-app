import { z } from 'zod';
export const submissionFileSchema = z.object({
  id: z.string(),
  assignmentId: z.string(),
  originalFileName: z.string(),
  mimeType: z.string(),
  size: z.number(),
  sha256: z.string(),
  scanStatus: z.enum(['PENDING', 'CLEAN', 'INFECTED', 'SCAN_FAILED', 'UNSCANNED']),
  scanAttempts: z.number(),
  scanMessage: z.string(),
  scannedAt: z.string().nullable(),
  createdAt: z.string(),
  bound: z.boolean(),
});
export const submissionFilesSchema = z.array(submissionFileSchema);
export type SubmissionFile = z.infer<typeof submissionFileSchema>;
export const currentSubmissionSchema = z.object({
  text: z.string(),
  submittedAt: z.string(),
  score: z.number().nullable(),
  feedback: z.string().nullable(),
  revision: z.number(),
  contentRevision: z.number(),
  submissionId: z.string(),
  files: submissionFilesSchema,
});
export type CurrentSubmission = z.infer<typeof currentSubmissionSchema>;
export const submissionHistorySchema = z.object({
  items: z.array(
    z.object({
      id: z.string(),
      contentRevision: z.number(),
      text: z.string(),
      submittedAt: z.string(),
      legacyImported: z.boolean(),
      files: submissionFilesSchema,
      grades: z.array(
        z.object({
          score: z.number(),
          maxScore: z.number(),
          feedback: z.string().nullable(),
          gradedAt: z.string(),
          legacyImported: z.boolean(),
        }),
      ),
    }),
  ),
  page: z.number(),
  size: z.number(),
  total: z.number(),
});
