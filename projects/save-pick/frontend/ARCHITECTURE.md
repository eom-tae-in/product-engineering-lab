# savePick 프론트엔드 아키텍처

`docs/06`~`09`, `11`, `12`이 확정한 화면·API·인증 설계를 Next.js 16(App Router) 코드로 옮기기 위한 구현 규칙이다. `docs/` 산출물과 달리 이 파일은 구현 단계에서만 참조하는 내부 규약이며, 값이 아니라 구조를 정의한다. `docs/14-project-structure.md`가 `backend/`의 역할을 하듯 이 파일이 `frontend/`의 역할을 한다.

## 스택

Next.js 16 (App Router) · React 19 · TypeScript · Tailwind v4 · Vitest + Testing Library + MSW (테스트)

## 디렉터리 구조

```
frontend/src/
├── app/
│   ├── (customer)/          고객 화면. 하단 탭 레이아웃 공유
│   │   ├── layout.tsx        BottomTabBar 포함 공통 레이아웃
│   │   ├── page.tsx           SC-001 홈
│   │   ├── search/            SC-002
│   │   ├── products/[id]/     SC-003
│   │   ├── cart/               SC-004
│   │   ├── orders/
│   │   │   ├── new/            SC-005 주문서 작성
│   │   │   ├── new/pickup/     SC-006 픽업 시간대
│   │   │   ├── new/payment/    SC-007 결제
│   │   │   ├── [id]/           SC-010 주문 상세 (SC-008 완료 직후도 여기로 리다이렉트)
│   │   │   ├── [id]/cancel/    SC-011 취소 확인
│   │   │   └── page.tsx        SC-009 주문 내역
│   │   ├── login/               SC-012
│   │   ├── signup/              SC-013
│   │   ├── me/                  SC-014 마이페이지
│   │   └── store/                SC-015 매장·픽업 안내
│   └── admin/                 관리자 화면. 별도 레이아웃(사이드 네비), 별도 진입 주소
│       ├── layout.tsx
│       ├── login/               SC-101 (레이아웃 밖, 인증 가드 제외)
│       ├── page.tsx              SC-102 관리자 홈
│       ├── products/             SC-103, SC-104
│       ├── discount-policy/      SC-107
│       ├── stock/                SC-105
│       ├── stock/history/        SC-106
│       ├── orders/               SC-108, SC-110
│       ├── pickup-lookup/        SC-109
│       ├── pickup-status/        SC-111
│       ├── no-shows/             SC-112
│       └── settings/             SC-113
├── features/<domain>/        account · store · product · stock · cart · pickup · order · admin-dashboard
│   ├── api.ts                  이 도메인 API 호출 함수 (apiClient 사용, 11번 응답 타입)
│   ├── types.ts                 요청/응답 타입 (11번 그대로)
│   └── components/              이 도메인 전용 화면 조각
├── components/ui/             화면 공통 컴포넌트 (09번 §2): Button, Badge, OrderStatusBadge,
│                                ProductCard, HoldTimerBar, PickupSlotChip, BottomSheet, TextField,
│                                AdminTable, Skeleton, EmptyState, ErrorState
├── lib/
│   ├── api-client.ts            fetch 래퍼, 오류 코드 매핑, 인증 헤더 부착 (아래 §인증)
│   ├── auth/                    AuthProvider, useAuth, 토큰 갱신 직렬화
│   ├── server-time.ts           API-008 기반 서버 시각 동기화 (아래 §서버 시각)
│   └── errors.ts                 11번 §0.5 코드 → 06번 화면별 한국어 문구 매핑
└── test/                      setup.ts, server.ts(MSW), 도메인별 handlers
```

각 화면(`page.tsx`)은 `docs/06`의 진입/이탈 경로, 표시 정보, **기본/로딩/빈 상태/오류 4종**을 그대로 구현한다. 상태 이름·문구는 06번 원문을 그대로 쓴다(의역 금지).

## 데이터 페칭 규칙

- 비로그인도 볼 수 있는 조회 화면(SC-001·002·003·015, 관리자 SC-107 등 읽기 전용 목록)은 **Server Component에서 직접 백엔드로 fetch**한다(`lib/api-client.ts`의 서버용 함수 사용). 클라이언트는 이 초기 데이터를 받아 상호작용만 담당한다.
- 로그인이 필요한 화면(장바구니 이후 전 구매 흐름, 마이페이지, 관리자 전 화면)은 **Client Component**에서 마운트 시 `useAuth()`의 액세스 토큰으로 직접 호출한다. 액세스 토큰이 서버에 없기 때문이다(아래 §인증).
- 폼 제출·상태 변경(담기, 수량 변경, 주문서 생성, 결제, 취소, 관리자 CRUD 등)은 Client Component에서 `api-client.ts`를 직접 호출한다. 이 프로젝트는 백엔드가 Spring Boot로 이미 분리돼 있어 Server Action을 쓰지 않는다 — Server Action은 Next.js 서버 자체가 뮤테이션을 처리할 때의 패턴이며, 여기서는 모든 뮤테이션이 어차피 외부 REST 호출이라 이득이 없다.
- Next.js `app/api/**/route.ts`는 만들지 않는다. 우리가 REST API를 자체 제공할 필요가 없고(백엔드가 이미 제공), 프런트가 자체 Route Handler를 두면 이중 프록시만 늘어난다.

## 인증

`docs/12-auth.md` 그대로: 액세스 토큰은 `Authorization: Bearer`, 리프레시 토큰은 `HttpOnly` 쿠키(`sp_refresh`, `Path=/api`, `SameSite=Lax`).

- **액세스 토큰은 메모리에만 둔다** (`AuthProvider`의 React state). `localStorage`/`sessionStorage`에 저장하지 않는다 — XSS 시 토큰이 영구 유출되는 것을 막기 위해서다.
- 앱 최초 로드 시 `AuthProvider`가 `POST /api/auth/token/refresh`를 `credentials:'include'`로 한 번 호출해 액세스 토큰을 조용히 발급받는다(쿠키가 없으면 401 → 비로그인 상태로 시작).
- `api-client.ts`는 401 응답을 받으면 **재발급을 1회만 직렬화해서 시도**한다(12번 §1.5: 여러 탭이 동시에 재발급을 시도하는 경우 진행 중인 요청 결과를 공유). 재발급도 401이면 로그인 상태를 종료하고 로그인 화면으로 보낸다.
- 프런트와 백엔드가 같은 등록 도메인(예: `savepick.com` / `api.savepick.com`, 로컬은 `localhost:3000` / `localhost:8080`)이라는 전제에서 `SameSite=Lax` 쿠키가 직접 fetch에도 실린다고 가정한다(같은 사이트, 다른 오리진). 배포 도메인이 이 전제를 벗어나면 재검토가 필요하다 — **가정, 확인 필요**.
- 관리자 인증은 별도 `AdminAuthProvider`로 분리한다(고객 세션과 같은 브라우저에서 섞이지 않게). 관리자 화면(`app/admin/**`, 로그인 제외)은 공통 레이아웃에서 인증 가드를 걸어 미인증 시 `SC-101`로 보낸다(06번 §4 공통 규칙).
- 비로그인 장바구니 식별자(`X-Guest-Token`)는 `localStorage`에 보관한다(서버가 최초 응답으로 발급한 값을 저장 후 재사용). 06번 U2가 미확정이라 보관 기간을 세션 한정이 아닌 영구 보관으로 잡는다 — **가정, 확인 필요**.

## 서버 시각 동기화

`API-008 GET /api/system/time`으로 받은 서버 시각과 클라이언트 시각의 차이(offset)를 앱 로드 시 한 번 계산해 `lib/server-time.ts`에 보관한다. 선점 타이머(SC-005~007)·노쇼 전환 카운트다운(SC-112)은 `Date.now() + offset`을 기준으로 계산한다 — 클라이언트 시계가 틀려도 서버 판정과 어긋나지 않게 하기 위해서다(FR-005). 서버가 매 응답에 실어 주는 `serverTime`(11번 §0.3)으로 관련 화면 진입 시마다 offset을 다시 보정한다.

## 오류 코드 처리

`lib/errors.ts`가 `11번 §0.5` 코드 전체를 다룬다. 화면마다 같은 코드라도 문구가 다를 수 있으므로(예: `CANCEL_NOT_ALLOWED`이 SC-011에서는 실제 상태별 3분기 문구), 공통 매핑은 "이 코드를 처리하지 않은 화면에서 보여줄 기본 문구"만 맡고, 06번이 화면별로 문구를 못박은 경우 그 화면 컴포넌트가 직접 분기한다. 카탈로그에 없는 코드가 응답에 오면 작업을 멈추고 보고한다(11번을 스스로 고치지 않는다) — `backend-engineer`와 동일한 규칙.

## 디자인 토큰

`docs/08-brand-guide.md` §3·4·8의 컬러·타이포·여백·모서리 토큰을 `app/globals.css`의 Tailwind v4 `@theme` 블록에 CSS 커스텀 프로퍼티로 그대로 옮긴다(라이트가 기준, §3.4 다크 모드 포함). 값을 임의로 바꾸지 않는다. `docs/09-ui-design-brief.md` §2의 공통 컴포넌트 규격(버튼 높이, 배지 형태, 카드 규격 등)을 `components/ui/`로 구현한다.

## 테스트

- 화면 단위: `docs/16-test-plan.md`에서 해당 화면의 TC를 찾아 Testing Library로 구현한다. 06번의 4개 상태(기본/로딩/빈/오류) 중 화면에 정의된 것은 전부 최소 1개 테스트로 커버한다.
- API 응답은 `src/test/server.ts`(MSW)에 도메인별 핸들러를 추가해 목킹한다. 실제 fetch를 그대로 코드에서 사용하고 테스트에서만 가로챈다.
- 인증·라우팅처럼 여러 화면이 공유하는 로직(`AuthProvider`, `api-client`)은 `lib/**/*.test.ts`로 별도 커버한다.
- `npm run verify`(`typecheck` → `lint` → `test`)가 전부 통과해야 슬라이스가 끝난 것이다.

## 가정 / 미확정 (구현 중 발견분)

| # | 가정한 내용 | 틀릴 경우 영향 |
|---|---|---|
| FE-A1 | 프런트·백엔드가 같은 등록 도메인 아래 배포된다 (SameSite=Lax 직접 fetch 전제) | 다른 도메인이면 BFF 프록시(Route Handler)나 토큰 브리지가 필요해진다 |
| FE-A2 | 비로그인 장바구니 `X-Guest-Token`을 `localStorage`에 영구 보관한다 (06번 U2 미확정 상태에서 임의 채택) | U2가 "세션 한정"으로 확정되면 `sessionStorage`로 교체해야 한다 |
| FE-A3 | 관리자 화면은 고객과 다른 라우트(`/admin/**`)로만 분리하고 별도 배포는 하지 않는다 | 운영상 관리자 도메인을 분리하려면 배포 구성이 바뀐다 |
