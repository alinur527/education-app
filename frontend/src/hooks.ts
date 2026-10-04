import { useEffect, useState } from 'react';
import type { z } from 'zod';
import { request } from './api';
export function useResource<T>(path: string, schema: z.ZodType<T>) {
  const [result, setResult] = useState<{ key: string; data: T | null; error: unknown } | null>(
    null,
  );
  const [revision, setRevision] = useState(0);
  const key = `${revision}:${path}`;
  useEffect(() => {
    const controller = new AbortController();
    request(path, schema, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key, data, error: null });
      })
      .catch((error) => {
        if (!controller.signal.aborted) setResult({ key, data: null, error });
      });
    return () => controller.abort();
  }, [path, schema, key]);
  const current = result?.key === key ? result : null;
  return {
    data: current?.data ?? null,
    error: current?.error ?? null,
    loading: !current,
    reload: () => setRevision((v) => v + 1),
  };
}
