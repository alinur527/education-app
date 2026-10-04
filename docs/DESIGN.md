# Design direction

Education App is a focused RU/KZ workspace for ЕНТ/ҰБТ preparation.

Palette: paper #FFFFFF, workspace #F4F6FA, ink #172544, secondary #53627A, cobalt #315AE8, success #21735B. Self-hosted Golos Text with Cyrillic extended supports Kazakh. Type 12/14/16/20/28/40/56, body measure 68ch. Spacing 4/8/12/16/24/32/48. Radius 8/16/24 by role.

Desktop: left-aligned sidebar, account/language header, broad study invitation, subjects and a quieter activity column. Mobile: full-width content, compact header and bottom navigation. Theory uses a reading column and contents navigation. Tests have native radio inputs, explicit save/next, visible progress and exit confirmation. Results offer score and full review; charts have text equivalents.

```
desktop                           mobile
| nav | header                  | | header             |
|     | study invitation        | | study invitation   |
|     | subjects     | activity | | subjects           |
|     | results / next steps    | | activity           |
                                  | bottom navigation  |
```

Plan critique: rejected gradient KPI hero and identical repeated cards. The characteristic element is a large study invitation with a geometric open-book drawing. Statistics stay secondary until real activity exists. Native controls minimize dependencies; Radix handles dialog focus. User-triggered transitions only, reduced motion respected. Validate via screenshots at desktop/tablet/mobile widths.


## Phase 2 surfaces

The pre-implementation plan and reference critique are in [PHASE2.md](PHASE2.md). The student shell retains Golos Text, the original blue/ink palette and responsive navigation. Courses add an ordered module/lesson outline; a focused reading surface joins theory, attachments, assignments and quizzes. Learning indicators disclose their real activity formula.

The staff workspace uses content rows, search/type/status filters, bilingual paired fields, explicit save state and review/publication controls. Status is written as text as well as color. Blocks use native labeled controls rather than unsafe rich HTML. Drag/drop has a normal file picker. Forms collapse to one column; tables scroll inside labeled regions. At 320px the header uses the compact book mark to preserve room for search, staff access and language controls. Screenshots were inspected at all five requested widths. Empty file sections are hidden; error states preserve recoverable input. No new UI or calendar dependency was added.
