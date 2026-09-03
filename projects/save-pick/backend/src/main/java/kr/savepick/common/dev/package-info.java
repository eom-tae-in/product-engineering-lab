/**
 * 로컬 개발·데모 전용 구성 요소. {@code dev} 프로파일에서만 활성화된다.
 *
 * <p>14-project-structure.md §4의 패키지 표에는 없는 추가 패키지다 — 운영 코드 경로에
 * 데모 데이터 생성 로직이 섞이지 않도록 별도 패키지로 분리했다. 이 패키지의 빈은 모두
 * {@code @Profile("dev")}를 달아 기본·운영·테스트 프로파일에서는 로드되지 않는다.
 */
package kr.savepick.common.dev;
