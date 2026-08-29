---
description: savePick 설계 파이프라인을 시작하거나 다음 단계를 진행한다
argument-hint: [선택: 특정 게이트명 예) G2]
allowed-tools: Task, Read, Write, Glob, Grep
---

`save-pick-orchestrator` Agent를 실행해 savePick 설계 파이프라인을 진행한다.

인자: $ARGUMENTS

1. `docs/00-status.md`가 있으면 읽고, 없으면 G0(부트스트랩)부터 시작한다.
2. 인자로 게이트가 지정되면 그 게이트부터 실행한다. 없으면 상태 파일의 "다음 작업"을 이어서 실행한다.
3. G1 완료 후에는 반드시 멈추고 사용자 승인을 받는다.
4. G2는 `experience-designer`와 `technical-architect`를 한 메시지에서 동시에 호출한다.
5. 각 게이트가 끝날 때마다 `docs/00-status.md`를 갱신한다.

앞 단계 산출물이 없으면 다음 단계를 실행하지 말고 무엇이 빠졌는지 보고한다.
