import { useCallback, useEffect, useRef, useState } from 'react';
import type { z } from 'zod';
import { request } from '../../api';
/** Keep data during same-query refresh; hide it immediately when query/owner changes. */
export function useStudyResource<T>(path: string, schema: z.ZodType<T>) {
  const [state, setState] = useState<{ path: string; data?: T; error?: unknown; version: number }>({
    path,
    version: -1,
  });
  const [version, setVersion] = useState(0);
  const schemaRef = useRef(schema);
  useEffect(() => {
    schemaRef.current = schema;
  }, [schema]);
  useEffect(() => {
    const controller = new AbortController();
    request(path, schemaRef.current, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setState({ path, data, version });
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setState((old) => ({
            path,
            data: old.path === path ? old.data : undefined,
            error,
            version,
          }));
      });
    return () => controller.abort();
  }, [path, version]);
  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return {
    ...(state.path === path ? state : { data: undefined, error: undefined }),
    loading: state.path !== path || state.version !== version,
    reload,
  };
}
