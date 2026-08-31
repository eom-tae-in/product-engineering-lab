/**
 * docs/09-ui-design-brief.md §3.1: 실제 레이아웃과 같은 크기의 회색 블록.
 * "스피너만 도는 화면을 만들지 않는다" — 이 컴포넌트는 크기를 실제 콘텐츠에
 * 맞춰 쓰는 용도이며, 무엇을 기다리는지 문구는 부모가 별도로 보여줘야 한다.
 */
export function Skeleton({ className = "" }: { className?: string }) {
  return (
    <div
      role="status"
      aria-label="불러오는 중"
      className={`animate-pulse rounded-md bg-border ${className}`}
    />
  );
}
