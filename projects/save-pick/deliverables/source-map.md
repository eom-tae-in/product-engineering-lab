# savePick 발행물 출처 매핑

- 문서 목적: `deliverables/save-pick-overview.pptx`(발표용 PPT)와 `deliverables/save-pick-spec.pdf`(상세 PDF)의 모든 슬라이드·장이 어느 승인 문서의 어느 절에서 왔는지 역추적한다.
- 근거: `docs/15-review-report.md` 「재검토 (2026-08-29)」 — `docs/01`~`17` 전체 `승인` 확인. `수정필요` 문서 없음.
- 원문에 없는 수치·요구사항·일정·전망은 만들지 않았다. 모든 수치(할인율, 시간, 횟수, 일수)는 `04-business-rules.md` 확정값을 그대로 인용했다.

---

## 1. PPT — `save-pick-overview.pptx` (14장)

| 슬라이드 | 내용 | 출처 문서 | 출처 위치 |
|---|---|---|---|
| 1 | 타이틀 — savePick 로고, 한 줄 정의 | `08-brand-guide.md`, `01-service-plan.md` | 08 §1(브랜드 한 줄 정의), 01 §1(서비스 정의) |
| 2 | 문제 — 마감 폐기·헛걸음 등 4가지 문제 | `01-service-plan.md` | §2 「해결하려는 문제」 표 (P1~P4 "문제" 열) |
| 3 | 해결 — savePick의 해결 방식 4가지 | `01-service-plan.md` | §1(서비스 정의), §2 「해결하려는 문제」 표 ("savePick의 해결" 열) |
| 4 | 대상 사용자 — 고객 / 관리자 | `01-service-plan.md` | §3.1(고객), §3.2(관리자) |
| 5 | MVP 범위 — 포함 / 제외 | `17-mvp-scope.md`, `01-service-plan.md` | 17 §1(MVP에 포함되는 것), §2(제외되는 것) / 01 §5.1~5.2 |
| 6 | 고객 사용 흐름 — 흐름도 1장 | `07-user-flows.md` | FL-001 「상품 탐색부터 주문 확정까지」 다이어그램(§1.1 전체 흐름도 대비 단순화 재구성) |
| 7 | 핵심 화면 · 고객 — 목록/상세/주문서 | `06-screen-list.md`, `wireframes/*.html` | 06 SC-001·SC-003·SC-005 항목 / `wireframes/sc-001-home-product-list.html`, `sc-003-product-detail.html`, `sc-005-order-sheet.html` 스크린샷 |
| 8 | 핵심 화면 · 관리자 — 홈/픽업 현황 | `06-screen-list.md`, `07-user-flows.md`, `wireframes/*.html` | 06 SC-102·SC-111 항목, 07 FL-101·FL-102 / `wireframes/sc-102-admin-home.html`, `sc-111-pickup-slot-status.html` 스크린샷 |
| 9 | 핵심 규칙 — 할인·선점·취소·노쇼 | `04-business-rules.md` | BR-004(할인 구간), BR-007(선점 10분), BR-012(결제 재시도), BR-018·BR-019(취소·재고 복구), BR-021·BR-023(노쇼 유예·제재) |
| 10 | 재고 동시성 — 3층 방어선 | `13-inventory-concurrency.md`, `04-business-rules.md` | 13 §0.3(방어선 3층), §1(임시 선점 구조) / BR-027(부분 선점 금지) |
| 11 | 데이터 구조 — ERD 요약 | `10-erd.md` | §0(설계 원칙), §1(전체 ERD, 19개 엔티티 중 핵심 14개 발췌 재구성) |
| 12 | 브랜드 — 로고·컬러·톤 | `08-brand-guide.md` | §2(전달할 인상과 디자인 근거), §3(컬러 토큰), §7(톤 앤 매너, 쓰지 않는 어휘) |
| 13 | 완료 기준 | `17-mvp-scope.md` | §3.1(항목 4), §3.3(항목 14), §3.1(항목 7·8), §3.4(항목 19) |
| 14 | 미확정 사항과 다음 결정 | `01-service-plan.md`, `08-brand-guide.md`, `09-ui-design-brief.md`, `12-auth.md`, `10-erd.md`, `06-screen-list.md`, `17-mvp-scope.md` | 01 §7 U1(영업시간)·§6 A4/U2(지표 목표치), 08 미확정 U1(매장명), 09 A8(매장 예시값), 12 미확정 AU-U1(관리자 세션), 10 가정 T-A3 및 06 미확정 U2(게스트 장바구니 보관 불일치), 17 §5 P-1~P-3(게이팅 이후 비차단 미확정) |

### 이미지 자산 출처

| 파일 | 생성 방법 | 원본 |
|---|---|---|
| `assets/logo/logo-full.svg` → PNG 변환 | 헤드리스 브라우저 렌더링(래스터화만, 내용 변경 없음) | `08-brand-guide.md` §5 로고 파일 |
| 고객·관리자 화면 스크린샷 5종 | `wireframes/*.html`을 헤드리스 브라우저로 촬영 | `wireframes/sc-001-home-product-list.html`, `sc-003-product-detail.html`, `sc-005-order-sheet.html`, `sc-102-admin-home.html`, `sc-111-pickup-slot-status.html` |
| 고객 흐름도 | `07-user-flows.md` FL-001 Mermaid 원문을 그대로 옮겨 `08-brand-guide.md` 컬러 토큰으로 렌더링(레이아웃만 LR로 재배치, 노드·분기·라벨 내용은 원문과 동일) | `07-user-flows.md` §1 FL-001 |
| ERD 요약도 | `10-erd.md` §1 Mermaid 원문에서 19개 엔티티 중 핵심 14개만 발췌해 동일한 관계 표기로 재렌더링 | `10-erd.md` §1 |

---

## 2. PDF — `save-pick-spec.pdf` (표지 포함 207쪽)

PDF는 승인된 `docs/01`~`17`을 **그 순서 그대로 전문 수록**하고, 부록 2종을 덧붙인 구조다. 장 단위 매핑은 1:1이므로 아래 표로 갈음한다.

| PDF 장 | 문서 ID | 원본 파일 | 비고 |
|---|---|---|---|
| 표지 | — | `08-brand-guide.md` §5(로고), `15-review-report.md`(승인 근거) | 로고·문서명·날짜·버전 |
| 목차 | — | — | 01~17 + 부록 A·B 링크 |
| 문서 01 | 01 | `docs/01-service-plan.md` | 전문 수록 |
| 문서 02 | 02 | `docs/02-user-scenarios.md` | 전문 수록, Mermaid 2개 → 이미지 변환 |
| 문서 03 | 03 | `docs/03-functional-requirements.md` | 전문 수록 |
| 문서 04 | 04 | `docs/04-business-rules.md` | 전문 수록 |
| 문서 05 | 05 | `docs/05-state-rules.md` | 전문 수록, Mermaid 2개 → 이미지 변환 |
| 문서 06 | 06 | `docs/06-screen-list.md` | 전문 수록 |
| 문서 07 | 07 | `docs/07-user-flows.md` | 전문 수록, Mermaid 13개 → 이미지 변환 |
| 문서 08 | 08 | `docs/08-brand-guide.md` | 전문 수록 |
| 문서 09 | 09 | `docs/09-ui-design-brief.md` | 전문 수록 |
| 문서 10 | 10 | `docs/10-erd.md` | 전문 수록, Mermaid 1개(전체 ERD) → 이미지 변환 |
| 문서 11 | 11 | `docs/11-api-spec.md` | 전문 수록 |
| 문서 12 | 12 | `docs/12-auth.md` | 전문 수록 |
| 문서 13 | 13 | `docs/13-inventory-concurrency.md` | 전문 수록 |
| 문서 14 | 14 | `docs/14-project-structure.md` | 전문 수록 |
| 문서 15 | 15 | `docs/15-review-report.md` | 전문 수록 (1차 검토 + 재검토 절 포함) |
| 문서 16 | 16 | `docs/16-test-plan.md` | 전문 수록 |
| 문서 17 | 17 | `docs/17-mvp-scope.md` | 전문 수록 |
| 부록 A | — | `06-screen-list.md` §1·§2, `15-review-report.md` §1 | 화면 목록 전체표(28개) + FR↔화면↔API↔테스트 3방향 추적표(49개 FR) 재수록 |
| 부록 B | — | `01`~`17` 각 문서의 「가정 / 미확정」 절 중 "미확정 (결정 대기)" 항목 | 문서 순서대로 통합. 상세 근거는 각 장 본문 참고 |

각 장 머리말에 "문서 XX" 배지를 넣어 원문 문서 ID를 표기했고, 모든 페이지 하단에 페이지 번호(`N / 207`)를 넣었다.

Mermaid 다이어그램은 `08-brand-guide.md`의 컬러 토큰(`--color-brand` 등)으로 재렌더링했을 뿐, 노드·화살표·조건 라벨 등 다이어그램이 표현하는 내용은 원문과 동일하다(레이아웃 엔진이 자동 배치한 좌표만 다르다).

---

## 3. 검토 체크

- [x] `15-review-report.md`에 `수정필요` 없음을 확인했다 (「재검토 (2026-08-29)」 절, 01~17 전체 `승인`).
- [x] PPT가 14장이다 (12~15장 범위 안).
- [x] 모든 슬라이드가 위 1절 표에서 출처 문서로 추적된다.
- [x] 브랜드 가이드(`08-brand-guide.md` §3, §8) 외의 색상·폰트 토큰을 쓰지 않았다. 폰트는 Pretendard 미설치 환경을 고려해 08 §4.1이 명시한 폴백 체인의 2순위 `Apple SD Gothic Neo`를 사용했다.
- [x] 원문에 없는 수치(시장 규모, 매출 전망, 일정, 팀 구성, 경쟁사 비교, 사용자 수 목표)를 넣지 않았다. 슬라이드에 등장하는 모든 수치는 `04-business-rules.md`·`17-mvp-scope.md`의 확정값 그대로다.
