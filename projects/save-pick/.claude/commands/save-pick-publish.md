---
description: 승인된 문서로 발표용 PPT와 상세 PDF를 만든다
allowed-tools: Task, Read, Glob, Grep
---

`documentation-publisher` Agent를 실행해 최종 산출물을 만든다.

실행 전 확인:
1. `docs/15-review-report.md`가 존재하는가. 없으면 중단하고 `quality-reviewer` 실행이 먼저라고 알린다.
2. 「문서 판정」 표에 `수정필요`가 하나라도 있는가. 있으면 **발행하지 말고** 해당 문서와 소유 Agent를 보고한다.

두 조건을 통과하면 `documentation-publisher`를 실행한다.

산출물:
- `deliverables/save-pick-overview.pptx` (발표용, 12~15장)
- `deliverables/save-pick-spec.pdf` (상세 설계)
- `deliverables/source-map.md` (슬라이드 → 출처 문서)

Publisher는 승인된 문서만 읽고, 원문에 없는 내용을 만들지 않는다.
