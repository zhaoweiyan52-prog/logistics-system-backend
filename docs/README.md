# Project Documentation

[Project overview and installation](../README.md) · [中文项目介绍](../README.zh-CN.md)

## Reading Order

| Document | Purpose |
|---|---|
| [Requirements specification](SRS.md) | Roles, functional requirements, acceptance criteria, constraints, and traceability |
| [Design and interfaces](DESIGN.md) | Architecture, entity relationships, order-creation sequence, state transitions, and API contract |
| [Verification and evolution](VERIFICATION.md) | Recorded test results, expected behavior, evidence, defect fixes, and a demonstration checklist |

The business baseline is `70bd1d2`, dated October 28, 2026. The November 4 [language extension](LANGUAGE.md) adds FR-12 and new frontend evidence. Historical documents may describe earlier behavior.

## Relationship to the Course Assessment

The table below uses the assessment brief supplied by the team. These technical documents support the submissions; they are not the final report, recorded presentation, or individual logbooks.

| Assessment | Criteria in the supplied brief | Evidence and preparation |
|---|---|---|
| Progress review — 10% | Structure: introduction, sequence, summary (5%); simple, clear presentation (5%) | Use the README for context, the design models for explanation, and the verification record for progress and open work. In-class review: weeks 10–12. |
| Recorded presentation — 10% | Structure: introduction, sequence, summary (5%); simple, clear presentation (5%) | Demonstrate the customer, staff, and administrator workflow. Explain decisions and limitations. Rehearse the checklist in Verification; avoid reading slides. |
| Project report — 15% | Software engineering and project management (12%); organization, design and analysis, writing (3%) | Use requirements, models, tests, and defect history as evidence. Add the team's actual development process, planning, decisions, and evaluation. |
| Individual logbook — 15% | Participation (10%); organization, design and analysis, writing (5%) | Each student records their own meetings, contributions, and reflection, including report and presentation work. Repository history alone cannot establish individual participation. |

## Submission Checklist

Based on the supplied brief:

- The group report must be a PDF of **no more than 1,000 words**. Source code is excluded from the word count.
- Put the accessible [GitHub repository link](https://github.com/zhaoweiyan52-prog/logistics-system-backend) on the **first line of the report**. The repository must include installation and run instructions.
- Only the group leader or representative submits the report through OLE. The report deadline is **December 8, 2026**; late submissions are not accepted.
- Each student submits an original individual logbook. Each meeting entry includes agenda, plans, participation, and an activity summary (**at most 200 words**), plus the student's own contribution (**at most 100 words**).
- The logbook also covers contributions to the project, report, and presentation. Interim dates are **September 30, October 15, October 30, and November 15**. The final deadline is **December 8, 2026**; students must also answer questions about their participation.
- Use actual records for dates and contributions. Completion of these documents does not establish that any course submission has been made.

## Supporting Records in Chinese

| Purpose | Documents |
|---|---|
| Current implementation and database upgrades | [Feature integration and upgrades](全功能对接与升级.md) |
| Repairs and deployment evidence | [Repair and verification log](修缮清单与验证.md), [Cloud deployment records](云端部署验证记录.md) |
| Environment setup and maintenance | [Local MySQL records](本机MySQL配置记录.md), [Render and Aiven deployment guide](免费部署-Render与Aiven.md), [Developer and local maintenance guide](开发与本机维护说明.md) |
| Phase-one history | [API review and open questions](接口核对与待确认问题.md), [Initial verification](验证记录.md), [Frontend integration fixes](第一阶段前端修复.md) |

Preserve historical results with their original dates and environments. They document evolution, not the current status of every feature. The [reference folder](../reference/README.md) contains the original frontend and mock materials.
