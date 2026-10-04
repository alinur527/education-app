import { z } from 'zod';
export const profileSchema = z.object({
  goal: z.string(),
  targetDate: z.string(),
  availableDays: z.array(z.number()),
  minutesPerDay: z.number(),
  timeZone: z.string(),
  selectedSubjects: z.array(z.string()),
  revision: z.number(),
});
export type Profile = z.infer<typeof profileSchema>;
export const taskSchema = z.object({
  id: z.string(),
  kind: z.string(),
  targetKind: z.string(),
  targetId: z.string(),
  titleRu: z.string(),
  titleKz: z.string().nullable(),
  reason: z.string(),
  scheduledAt: z.string().nullable(),
  durationMinutes: z.number(),
  status: z.enum(['PLANNED', 'COMPLETED', 'SKIPPED']),
  pinned: z.boolean(),
  manuallyMoved: z.boolean(),
  revision: z.number(),
  available: z.boolean(),
  url: z.string().nullable(),
});
export type Task = z.infer<typeof taskSchema>;
export const calendarSchema = z.object({
  items: z.array(taskSchema),
  page: z.number(),
  size: z.number(),
  total: z.number(),
  unscheduled: z.number(),
});
export const planSchema = z.object({
  planned: z.number(),
  preserved: z.number(),
  unscheduled: z.number(),
  requiredMinutes: z.number(),
  capacityMinutes: z.number(),
  capacityWarning: z.boolean(),
});
export const deadlineSchema = z.object({
  id: z.string(),
  titleRu: z.string(),
  titleKz: z.string().nullable(),
  dueAt: z.string(),
  submitted: z.boolean(),
  url: z.string(),
});
export const pageSchema = <T extends z.ZodType>(item: T) =>
  z.object({ items: z.array(item), page: z.number(), size: z.number(), total: z.number() });
export const deadlinesSchema = pageSchema(deadlineSchema);
export const noteSchema = z.object({
  id: z.string(),
  targetKind: z.string(),
  targetId: z.string(),
  title: z.string(),
  body: z.string(),
  bookmarked: z.boolean(),
  cardFront: z.string(),
  cardBack: z.string(),
  nextReviewAt: z.string().nullable(),
  intervalDays: z.number(),
  reviewCount: z.number(),
  revision: z.number(),
  available: z.boolean(),
  url: z.string().nullable(),
  updatedAt: z.string(),
});
export type Note = z.infer<typeof noteSchema>;
export const notesSchema = pageSchema(noteSchema);
export const preferencesSchema = z.object({
  enabled: z.boolean(),
  plans: z.boolean(),
  deadlines: z.boolean(),
  grades: z.boolean(),
  materials: z.boolean(),
  quietEnabled: z.boolean(),
  quietStart: z.string(),
  quietEnd: z.string(),
  revision: z.number(),
});
export type Preferences = z.infer<typeof preferencesSchema>;
export const notificationSchema = z.object({
  id: z.string(),
  kind: z.string(),
  titleRu: z.string(),
  titleKz: z.string().nullable(),
  read: z.boolean(),
  createdAt: z.string(),
  available: z.boolean(),
});
export const notificationsSchema = pageSchema(notificationSchema).extend({ unread: z.number() });
export const openedSchema = z.object({ url: z.string() });
export const savedSchema = z.object({ saved: z.boolean() });
export const searchSchema = z.array(
  z.object({
    id: z.string(),
    kind: z.string(),
    titleRu: z.string(),
    titleKz: z.string().nullable(),
  }),
);

/** Calendar days use the profile zone; browser/device time zone must not change them. */
export function dayInZone(value: string | Date, zone: string) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value));
}
export function localInput(value: string, zone: string) {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(value));
  const get = (type: string) => parts.find((p) => p.type === type)?.value;
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}
/** Resolve a wall clock time through Intl, including non-hour offsets. Reject DST gaps. */
export function zonedInstant(value: string, zone: string) {
  const raw = Date.parse(value + 'Z');
  if (!Number.isFinite(raw)) throw new Error('INVALID_DATE');
  let guess = raw;
  for (let i = 0; i < 3; i++) {
    const represented = Date.parse(localInput(new Date(guess).toISOString(), zone) + 'Z');
    guess += raw - represented;
  }
  const result = new Date(guess).toISOString();
  if (localInput(result, zone) !== value) throw new Error('INVALID_DATE');
  return result;
}
export function shiftedDay(day: string, days: number) {
  const d = new Date(day + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
export function windowFor(day: string, mode: 'day' | 'week' | 'month') {
  if (mode === 'day') return { from: day, to: day };
  if (mode === 'month') {
    const from = day.slice(0, 7) + '-01';
    const d = new Date(from + 'T12:00:00Z');
    d.setUTCMonth(d.getUTCMonth() + 1);
    return { from, to: shiftedDay(d.toISOString().slice(0, 10), -1) };
  }
  const weekday = new Date(day + 'T12:00:00Z').getUTCDay();
  const from = shiftedDay(day, -((weekday + 6) % 7));
  return { from, to: shiftedDay(from, 6) };
}
