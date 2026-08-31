"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { fetchMe, fetchNoShowStatus, updateMe } from "@/features/account/api";
import type { MeResponse, NoShowStatusResponse } from "@/features/account/types";
import { ApiError } from "@/lib/api-client";
import { formatKstDateTime } from "@/lib/format";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/ErrorState";

/** 가정: 국내 휴대폰 번호, 숫자만(대시 없이), 010/011/016/017/018/019로 시작. */
const PHONE_REGEX = /^01[016789]\d{7,8}$/;

/**
 * SC-014 · 마이페이지 (docs/06-screen-list.md §3).
 * 로그인 필수 화면이다. useAuth().status로 접근 제어를 하므로 새로고침 직후에는
 * 잠깐 "확인 중" 스켈레톤이 보일 수 있다(ARCHITECTURE.md §인증 규칙9).
 */
export default function MyPage() {
  const auth = useAuth();
  const router = useRouter();

  const [me, setMe] = useState<MeResponse | null>(null);
  const [noShowStatus, setNoShowStatus] = useState<NoShowStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editPhone, setEditPhone] = useState("");
  const [phoneError, setPhoneError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState(false);
  const [saving, setSaving] = useState(false);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    return Promise.all([fetchMe(), fetchNoShowStatus()])
      .then(([meResponse, noShowResponse]) => {
        setMe(meResponse);
        setNoShowStatus(noShowResponse);
        setLoadError(null);
      })
      .catch((error: unknown) => {
        setLoadError(
          error instanceof ApiError
            ? error.defaultMessage
            : "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요."
        );
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const loadData = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad();
  }, [runLoad]);

  useEffect(() => {
    if (auth.status === "guest") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  useEffect(() => {
    if (auth.status === "authenticated") {
      runLoad();
    }
  }, [auth.status, runLoad]);

  if (auth.status !== "authenticated") {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-6 w-24" />
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-6 w-32" />
      </div>
    );
  }

  if (loadError) {
    return <ErrorState message={loadError} onRetry={loadData} />;
  }

  if (!me || !noShowStatus) {
    return null;
  }

  const currentMe = me;

  function startEditing() {
    setEditName(currentMe.name);
    setEditPhone(currentMe.phone);
    setPhoneError(null);
    setSaveError(false);
    setIsEditing(true);
  }

  function cancelEditing() {
    setIsEditing(false);
    setPhoneError(null);
    setSaveError(false);
  }

  async function submitUpdate() {
    if (!PHONE_REGEX.test(editPhone)) {
      setPhoneError("휴대폰 번호 형식을 확인해주세요");
      return;
    }
    setPhoneError(null);
    setSaveError(false);
    setSaving(true);
    try {
      const updated = await updateMe({ name: editName.trim(), phone: editPhone });
      setMe((prev) => (prev ? { ...prev, name: updated.name, phone: updated.phone } : prev));
      setIsEditing(false);
    } catch {
      setSaveError(true);
    } finally {
      setSaving(false);
    }
  }

  function handleFormSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submitUpdate();
  }

  async function handleLogout() {
    await auth.logout();
    router.push("/login");
  }

  const isRestricted = noShowStatus.orderPermission === "RESTRICTED";

  return (
    <div className="flex flex-col gap-3 p-4">
      {isRestricted && noShowStatus.restrictedUntil ? (
        <div className="rounded-lg bg-warning-weak p-4">
          <p className="font-body text-warning">
            {`노쇼 ${noShowStatus.recentNoShowCount}회 누적 · ${formatKstDateTime(
              noShowStatus.restrictedUntil
            )}까지 새 주문을 만들 수 없어요`}
          </p>
          <p className="font-caption mt-1 text-text-weak">확정된 주문은 그대로 유지돼요</p>
        </div>
      ) : null}

      {isEditing ? (
        <form
          onSubmit={handleFormSubmit}
          noValidate
          className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
        >
          <h2 className="font-heading mb-3">회원 정보 수정</h2>
          <TextField
            id="me-name"
            name="name"
            label="이름"
            value={editName}
            onChange={(event) => setEditName(event.target.value)}
            disabled={saving}
          />
          <div className="mb-4">
            <TextField id="me-email" name="email" label="이메일" value={me.email} disabled />
            <p className="font-caption mt-1 text-text-weak">이메일은 변경할 수 없어요</p>
          </div>
          <TextField
            id="me-phone"
            name="phone"
            label="휴대폰 번호"
            value={editPhone}
            onChange={(event) => setEditPhone(event.target.value)}
            error={phoneError ?? undefined}
            disabled={saving}
          />

          {saveError ? (
            <ErrorState message="저장하지 못했어요" onRetry={() => void submitUpdate()} />
          ) : null}

          <div className="mt-3 flex flex-col gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? "저장하는 중이에요" : "저장"}
            </Button>
            <Button type="button" variant="secondary" onClick={cancelEditing} disabled={saving}>
              취소
            </Button>
          </div>
        </form>
      ) : (
        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-heading">회원 정보</h2>
            <Button variant="secondary" className="h-11 w-auto px-4" onClick={startEditing}>
              수정
            </Button>
          </div>
          <dl className="flex flex-col gap-2">
            <div className="flex justify-between border-b border-border pb-2">
              <dt className="font-caption text-text-weak">이름</dt>
              <dd className="font-body">{me.name}</dd>
            </div>
            <div className="flex justify-between border-b border-border pb-2">
              <dt className="font-caption text-text-weak">이메일</dt>
              <dd className="font-body">{me.email}</dd>
            </div>
            <div className="flex justify-between pb-2">
              <dt className="font-caption text-text-weak">휴대폰 번호</dt>
              <dd className="font-body">{me.phone}</dd>
            </div>
          </dl>
        </div>
      )}

      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <h2 className="font-heading mb-3">픽업 기록</h2>
        <dl className="flex flex-col gap-2">
          <div className="flex justify-between border-b border-border pb-2">
            <dt className="font-caption text-text-weak">최근 30일 노쇼 횟수</dt>
            <dd className={`font-body ${isRestricted ? "text-danger" : ""}`}>
              {noShowStatus.recentNoShowCount}회
            </dd>
          </div>
          {isRestricted && noShowStatus.restrictedUntil ? (
            <div className="flex justify-between pb-2">
              <dt className="font-caption text-text-weak">주문 제한 해제 예정</dt>
              <dd className="font-body">{formatKstDateTime(noShowStatus.restrictedUntil)}</dd>
            </div>
          ) : null}
        </dl>
      </div>

      <div className="flex flex-col gap-2">
        <Link
          href="/orders"
          className="font-body flex h-12 items-center justify-center rounded-md border border-border text-text"
        >
          주문 내역 보기
        </Link>
        <Link
          href="/store"
          className="font-body flex h-12 items-center justify-center rounded-md border border-border text-text"
        >
          매장·픽업 안내
        </Link>
        <Button variant="secondary" onClick={handleLogout}>
          로그아웃
        </Button>
      </div>
    </div>
  );
}
