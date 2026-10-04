import { Fragment, useEffect, useRef } from 'react';

// Only KaTeX creates DOM markup. User text is always rendered by React as text.
export function MathText({ text, display = false }: { text: string; display?: boolean }) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    let alive = true;
    void Promise.all([import('katex'), import('katex/dist/katex.min.css')])
      .then(([katex]) => {
        if (!alive || !ref.current) return;
        try {
          katex.default.render(text.slice(0, 5000), ref.current, {
            displayMode: display,
            throwOnError: true,
            trust: false,
            strict: 'ignore',
            maxExpand: 200,
            maxSize: 10,
            macros: {},
          });
        } catch {
          ref.current.textContent = text;
        }
      })
      .catch(() => {
        if (alive && ref.current) ref.current.textContent = text;
      });
    return () => {
      alive = false;
    };
  }, [text, display]);
  return (
    <span ref={ref} className={display ? 'math-display' : 'math-inline'}>
      {text}
    </span>
  );
}
export function InlineText({ text }: { text: string }) {
  return (
    <>
      {text
        .split(/(\$\$[\s\S]*?\$\$|\$[^$\n]+\$)/g)
        .map((part, i) =>
          part.startsWith('$$') && part.endsWith('$$') ? (
            <MathText key={i} text={part.slice(2, -2)} display />
          ) : part.startsWith('$') && part.endsWith('$') ? (
            <MathText key={i} text={part.slice(1, -1)} />
          ) : (
            <Fragment key={i}>{part}</Fragment>
          ),
        )}
    </>
  );
}
export function RichText({ text }: { text: string }) {
  return (
    <div className="rich-text">
      {text.split(/(```[\s\S]*?```)/g).map((part, i) =>
        part.startsWith('```') ? (
          <pre key={i}>
            <code>{part.slice(3, -3).replace(/^\w+\n/, '')}</code>
          </pre>
        ) : (
          <Fragment key={i}>
            {part
              .split(/\n\s*\n/)
              .filter(Boolean)
              .map((p, j) =>
                p.startsWith('## ') ? (
                  <h3 key={j}>
                    <InlineText text={p.slice(3)} />
                  </h3>
                ) : p.startsWith('|') ? (
                  <TextTable key={j} text={p} />
                ) : (
                  <p key={j}>
                    <InlineText text={p} />
                  </p>
                ),
              )}
          </Fragment>
        ),
      )}
    </div>
  );
}
export function TextTable({ text }: { text: string }) {
  const rows = text
    .trim()
    .split('\n')
    .filter((r) => !/^\|?[\s:|-]+\|?$/.test(r))
    .map((r) =>
      r
        .replace(/^\||\|$/g, '')
        .split('|')
        .map((v) => v.trim()),
    );
  return (
    <div className="table-scroll" tabIndex={0} role="region" aria-label="Таблица / Кесте">
      <table>
        <thead>
          <tr>
            {rows[0]?.map((c, i) => (
              <th key={i} scope="col">
                <InlineText text={c} />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.slice(1).map((row, i) => (
            <tr key={i}>
              {row.map((c, j) => (
                <td key={j}>
                  <InlineText text={c} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
