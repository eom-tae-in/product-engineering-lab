import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { DEFAULT_ERROR_MESSAGES, ERROR_CODES, isKnownErrorCode } from "./errors";

/**
 * docs/11-api-spec.md §0.5 오류 코드 카탈로그에서 코드만 뽑아낸다.
 * 표 형식: `| \`CODE\` | HTTP | 의미 | 규칙 |`
 */
function errorCodesInSpec(): string[] {
  const spec = readFileSync(resolve(process.cwd(), "../docs/11-api-spec.md"), "utf-8");
  const start = spec.indexOf("### 0.5 오류 코드 카탈로그");
  expect(start).toBeGreaterThan(-1);
  const rest = spec.slice(start + 1);
  const end = rest.search(/\n#{2,3} /);
  const section = end === -1 ? rest : rest.slice(0, end);
  return [...section.matchAll(/^\|\s*`([A-Z_]+)`\s*\|/gm)].map((m) => m[1]);
}

/**
 * TC-117 [X1] · 오류 코드와 화면 상태의 1:1 대응 회귀 검증 (docs/16-test-plan.md).
 *
 * 06번이 화면별 오류 상태를 정의하는 근거가 11번 §0.5 카탈로그다. 백엔드가 카탈로그에
 * 코드를 추가·삭제했는데 화면이 따라가지 않으면, 사용자는 처리되지 않은 오류를 만나게
 * 된다(`errors.ts` 상단 주석이 정한 규칙: 여기 없는 코드가 오면 화면에서 처리하지 말고
 * 보고한다). 그 어긋남을 사람 눈이 아니라 테스트가 잡도록 카탈로그 원문과 직접 대조한다.
 */
describe("TC-117 오류 코드 카탈로그 ↔ 화면 문구 대응", () => {
  it("11번 §0.5의 코드 집합과 정확히 일치한다", () => {
    const spec = errorCodesInSpec();

    expect(spec.length).toBeGreaterThan(0);
    expect([...ERROR_CODES].sort()).toEqual([...spec].sort());
  });

  it("모든 코드에 화면에 보여줄 한국어 기본 문구가 있다", () => {
    for (const code of ERROR_CODES) {
      expect(DEFAULT_ERROR_MESSAGES[code]?.trim()).toBeTruthy();
    }
  });

  it("카탈로그에 없는 코드는 알 수 없는 코드로 판정한다", () => {
    expect(isKnownErrorCode("NOT_IN_CATALOG")).toBe(false);
    expect(isKnownErrorCode(ERROR_CODES[0])).toBe(true);
  });
});
