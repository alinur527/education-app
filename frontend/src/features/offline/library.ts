import { z } from 'zod';
import { ApiError, request, TOKEN_KEY } from '../../api';
import { payloadSchema } from '../content/model';
export const exportSchema = z.object({
  id: z.string().uuid(),
  version: z.number(),
  policy: z.literal('PUBLIC_THEORY_V1'),
  content: payloadSchema,
  files: z.array(
    z.object({
      id: z.string().uuid(),
      titleRu: z.string(),
      titleKz: z.string().nullable(),
      name: z.string(),
      mime: z.string(),
      size: z.number(),
    }),
  ),
});
export type SavedTheory = {
  id: string;
  document: z.infer<typeof exportSchema>;
  savedAt: string;
  size: number;
  files: { id: string; blob: Blob }[];
};
function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open('education-public-library', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('theories', { keyPath: 'id' });
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}
async function transact<T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('theories', mode),
      r = operation(tx.objectStore('theories'));
    tx.oncomplete = () => {
      db.close();
      resolve(r.result);
    };
    tx.onerror = () => {
      db.close();
      reject(tx.error);
    };
    tx.onabort = () => {
      db.close();
      reject(tx.error);
    };
  });
}
export const listSaved = () => transact<SavedTheory[]>('readonly', (store) => store.getAll());
export const deleteSaved = (id: string) => transact('readwrite', (store) => store.delete(id));
export async function saveTheory(id: string) {
  const document = await request(`/offline/theories/${id}`, exportSchema),
    files: SavedTheory['files'] = [];
  let size = new Blob([JSON.stringify(document)]).size;
  const old = await listSaved(),
    remaining = 100 * 1024 * 1024 - old.filter((d) => d.id !== id).reduce((n, d) => n + d.size, 0);
  if (size + document.files.reduce((n, f) => n + f.size, 0) > remaining)
    throw new Error('OFFLINE_QUOTA');
  for (const f of document.files) {
    const token = sessionStorage.getItem(TOKEN_KEY);
    const response = await fetch(
      `${import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') || ''}/api/offline/materials/${f.id}`,
      {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        cache: 'no-store',
        signal: AbortSignal.timeout(30000),
      },
    );
    if (!response.ok) throw new ApiError(response.status, 'OFFLINE_FILE_UNAVAILABLE');
    const blob = await response.blob();
    if (blob.size !== f.size || blob.size > 20 * 1024 * 1024) throw new Error('OFFLINE_FILE_SIZE');
    size += blob.size;
    files.push({ id: f.id, blob });
  }
  // A single IDB write replaces the complete saved edition, never a half-downloaded book.
  const entry: SavedTheory = { id, document, savedAt: new Date().toISOString(), size, files };
  // IDB serializes readwrite transactions across tabs. Recheck the total under the write lock.
  const db = await open();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('theories', 'readwrite'),
      store = tx.objectStore('theories'),
      all = store.getAll();
    let quota = false;
    all.onsuccess = () => {
      const used = (all.result as SavedTheory[])
        .filter((v) => v.id !== id)
        .reduce((n, v) => n + v.size, 0);
      if (used + size > 100 * 1024 * 1024) {
        quota = true;
        tx.abort();
      } else store.put(entry);
    };
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = tx.onabort = () => {
      db.close();
      reject(quota ? new Error('OFFLINE_QUOTA') : tx.error);
    };
  });
  return entry;
}
