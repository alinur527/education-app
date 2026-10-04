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
