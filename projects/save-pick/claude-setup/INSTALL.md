# savePick Agent · Skill 설치

`.claude` 경로는 원격 도구가 직접 쓸 수 없어, 파일을 `claude-setup/`에 넣었습니다.
아래 명령 한 줄이면 `.claude/`로 옮겨집니다.

```bash
cd ~/Desktop/save-pick
bash claude-setup/install.sh
```

또는 Finder에서 `claude-setup` 안의 `agents`, `skills`, `commands` 폴더를 `.claude/` 아래로 옮겨도 됩니다.
(Finder에서 숨김 폴더 보기: `Cmd + Shift + .`)

## 설치되는 것

```
.claude/
├── agents/                          6개
│   ├── save-pick-orchestrator.md    파이프라인 진행 관리
│   ├── product-planner.md           기획 · 요구사항 · 업무 규칙  (G1)
│   ├── experience-designer.md       화면 · 흐름 · 브랜드 · 와이어프레임  (G2 병렬 A)
│   ├── technical-architect.md       ERD · API · 인증 · 동시성  (G2 병렬 B)
│   ├── quality-reviewer.md          일관성 검토 · 테스트 · 완료 기준  (G3)
│   └── documentation-publisher.md   PPT · PDF 발행  (G4)
├── skills/                          8개
│   ├── doc-conventions/             문서 공통 규칙 (전체 공유)
│   ├── spec-format/                 요구사항 · 업무 규칙 형식
│   ├── flow-and-screen-map/         화면 목록 · 흐름도 형식
│   ├── brand-identity-kit/          브랜드 · 로고 규칙
│   ├── wireframe-kit/               와이어프레임 · 프로토타입 규칙
│   ├── tech-spec-format/            ERD · API · 동시성 형식
│   ├── verification-checklist/      추적표 · 테스트 케이스 형식
│   └── deliverable-deck-spec/       슬라이드 구성 · 출처 표기 규칙
└── commands/                        3개
    ├── save-pick-start.md           /save-pick-start
    ├── save-pick-status.md          /save-pick-status
    └── save-pick-publish.md         /save-pick-publish
```

PPT·PDF 파일 생성은 별도 Skill을 만들지 않고 Claude 내장 `pptx` / `pdf` Skill을 사용합니다.

## 사용

```
/save-pick-start      파이프라인 시작 또는 다음 단계 진행
/save-pick-status     진행 상태 · 문서 커버리지 · 결정 대기 항목 확인
/save-pick-publish    승인된 문서로 PPT · PDF 발행
```

## 실행 순서

```
G1  product-planner            → 사용자 승인에서 멈춤
G2  experience-designer  ∥  technical-architect   (병렬)
G3  quality-reviewer           → 수정필요 있으면 해당 Agent만 재실행
G4  documentation-publisher    → G3 전원 승인 시에만 실행
```

## 생성될 문서

| 번호 | 문서 | 소유 |
|---|---|---|
| 00 | status | orchestrator |
| 01~05 | 기획서 · 시나리오 · 요구사항 · 업무규칙 · 상태규칙 | product-planner |
| 06~09 | 화면목록 · 흐름 · 브랜드 · UI브리프 | experience-designer |
| 10~14 | ERD · API · 인증 · 동시성 · 구조 | technical-architect |
| 15~17 | 검토리포트 · 테스트 · MVP범위 | quality-reviewer |
| — | deliverables/*.pptx, *.pdf | documentation-publisher |
