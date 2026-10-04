# Original starter authoring — research agent

`pilot.json`: 15 lessons / 150 questions across mathematics, physics and Kazakhstan history. `core.json`: 16 lessons / 160 questions across the other eight non-language directions. These are authoring inputs, not application seed migrations.

Every lesson contains original RU/KZ theory (at least 1,500 characters per language), ten distinct questions, answer explanations, official curriculum source/code and factual source links. The two reading passages are original fictional school scenarios; `contexts[].contentRu/contentKz` stores each once and twenty questions reference `contextExternalKey`.

Question provenance is `AI_GENERATED`. The review flags represent model checks, never human subject-expert approval. An independent review agent read question 03 and 08 of every pilot/core lesson, all ten typed pilot questions, both reading passages and the current law sources. Its sampled key/translation checks passed; editorial spacing and technical source IDs in student explanations were corrected. This is a sampled independent review, not independent human verification of all 310 questions.

Offline answer checking:

```powershell
.venv/Scripts/python.exe content/starter/build_pilot.py
.venv/Scripts/python.exe content/starter/check_pilot.py
.venv/Scripts/python.exe content/starter/build_core.py
.venv/Scripts/python.exe content/starter/check_core.py
```

Pilot proof output checks all 150 records structurally and contains 83 explicit answer checks. Core checks all 160 records, both contexts and 20 context references; it contains 64 explicit checks. Arithmetic is independently computed from authored operands. The scripts do not execute downloaded expressions or source code. Historical interpretation, conceptual explanations and translation remain model-reviewed; the JSON proof files identify this limitation and bind results to the exact content SHA.

The law lessons use the Constitution adopted 15 March 2026, effective 1 July 2026, checked in the official RU and KZ Әділет texts on 4 October 2026. Each law question records its article, URL and applicability date. The exact read excerpts are in `content/research/current-law-2026.json`. Old article numbering cannot silently replace the current act. Official NTC source documents remain unchanged.

Physics questions state assumptions (for example g=10 where needed), quadratic examples specify real-number interpretations, and all matching tasks have two left items with independently checked keys. No official sample question set was copied. OpenStax and other sources were used as factual references for original explanations and exercises; their public availability is not a blanket licence to copy or ingest books. OpenStax currently displays CC BY-NC-SA and additional AI ingestion restrictions. No external book, illustration or full article is redistributed in this pack. App-level source permissions should retain restricted/external-link defaults unless separately established.

This starter pack does not cover every official topic, include sufficient contexts/difficulty distribution for a full official-format exam, or demonstrate app import/publication. Those require the application adapter, transactional import and end-to-end checks maintained by the parent task.
