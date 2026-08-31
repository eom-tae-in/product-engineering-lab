"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  fetchAdminStoreSettings,
  updateAdminStoreSettings,
} from "@/features/store/api";
import { ApiError } from "@/lib/api-client";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/ErrorState";

interface SaveSummary {
  excludedFutureSlotCount: number;
  keptConfirmedOrderCount: number;
}

/**
 * SC-113 · 픽업 운영 설정 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다
 * (ARCHITECTURE.md §인증). Client Component에서 마운트 시 `authScope: "admin"`으로
 * 직접 호출한다.
 *
 * 범위: API-120·API-121만 다룬다 — 영업시간, 시간대 정원, 휴무일 목록(추가/삭제).
 * 개별 시간대 차단·해제(API-119)는 SC-111의 몫이라 이 화면에 두지 않는다.
 *
 * 판단: 06이 정의한 "경고(기존 예약 초과)"·"경고(영업 종료 단축)"는 저장 전에
 * 실제 예약 건수·영향받는 시간대를 알아야 하는데, 그 값은 API-118(SC-111 전용
 * 시간대별 현황 조회)에서만 얻을 수 있어 API-120/121만으로는 만들 수 없다. 대신
 * 저장에 성공하면 응답에 담긴 `excludedFutureSlotCount`·`keptConfirmedOrderCount`를
 * 저장 직후 요약 문구로 보여준다. 06번에 이 응답 필드를 보여주는 정확한 문구가
 * 없어 여기서 만든 표현이다 — SC-111이 이 화면에 붙는 다음 슬라이스에서 사전
 * 경고 시트로 교체할지 재검토가 필요하다.
 */
export default function AdminSettingsPage() {
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [openTime, setOpenTime] = useState("");
  const [closeTime, setCloseTime] = useState("");
  const [capacityInput, setCapacityInput] = useState("");
  const [holidays, setHolidays] = useState<string[]>([]);
  const [newHoliday, setNewHoliday] = useState("");

  const [businessHourError, setBusinessHourError] = useState<string | null>(null);
  const [capacityError, setCapacityError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveSummary, setSaveSummary] = useState<SaveSummary | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    return fetchAdminStoreSettings()
      .then((data) => {
        setOpenTime(data.openTime);
        setCloseTime(data.closeTime);
        setCapacityInput(String(data.defaultSlotCapacity));
        setHolidays(data.holidays);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const loadSettings = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad();
  }, [runLoad]);

  useEffect(() => {
    runLoad();
  }, [runLoad]);

  if (loading) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-6 w-24" />
        <Skeleton className="h-[52px] w-full" />
        <Skeleton className="h-6 w-24" />
        <Skeleton className="h-[52px] w-full" />
        <Skeleton className="h-6 w-24" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="p-4">
        <ErrorState message={loadError} onRetry={loadSettings} />
      </div>
    );
  }

  function addHoliday() {
    if (!newHoliday || holidays.includes(newHoliday)) {
      setNewHoliday("");
      return;
    }
    setHolidays((prev) => [...prev, newHoliday].sort());
    setNewHoliday("");
  }

  function removeHoliday(date: string) {
    setHolidays((prev) => prev.filter((holiday) => holiday !== date));
  }

  function validate(): boolean {
    let valid = true;

    if (!openTime || !closeTime || closeTime <= openTime) {
      setBusinessHourError("영업 종료 시각은 시작 시각보다 늦어야 해요");
      valid = false;
    } else {
      setBusinessHourError(null);
    }

    const capacity = Number(capacityInput);
    if (!Number.isInteger(capacity) || capacity < 1) {
      setCapacityError("정원은 1 이상 정수여야 해요");
      valid = false;
    } else {
      setCapacityError(null);
    }

    return valid;
  }

  async function submit() {
    setSaveError(null);
    setSaveSummary(null);
    if (!validate()) return;

    setSaving(true);
    try {
      const result = await updateAdminStoreSettings({
        openTime,
        closeTime,
        defaultSlotCapacity: Number(capacityInput),
        holidays,
      });
      setOpenTime(result.openTime);
      setCloseTime(result.closeTime);
      setCapacityInput(String(result.defaultSlotCapacity));
      setHolidays(result.holidays);
      setSaveSummary({
        excludedFutureSlotCount: result.excludedFutureSlotCount,
        keptConfirmedOrderCount: result.keptConfirmedOrderCount,
      });
    } catch (error) {
      if (error instanceof ApiError && error.code === "BUSINESS_HOUR_INVALID") {
        setBusinessHourError("영업 종료 시각은 시작 시각보다 늦어야 해요");
      } else if (error instanceof ApiError && error.code === "VALIDATION_ERROR") {
        setCapacityError("정원은 1 이상 정수여야 해요");
      } else if (error instanceof ApiError) {
        setSaveError(error.defaultMessage);
      } else {
        setSaveError("일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.");
      }
    } finally {
      setSaving(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submit();
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">픽업 운영 설정</h1>

      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3">
        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <h2 className="font-heading mb-3">영업시간</h2>
          <div className="flex gap-2">
            <TextField
              id="open-time"
              name="openTime"
              label="시작"
              type="time"
              step={1800}
              value={openTime}
              onChange={(event) => setOpenTime(event.target.value)}
              disabled={saving}
            />
            <TextField
              id="close-time"
              name="closeTime"
              label="종료"
              type="time"
              step={1800}
              value={closeTime}
              onChange={(event) => setCloseTime(event.target.value)}
              error={businessHourError ?? undefined}
              disabled={saving}
            />
          </div>
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <h2 className="font-heading mb-3">시간대 정원</h2>
          <TextField
            id="capacity"
            name="capacity"
            label="시간대당 예약 정원 (건)"
            type="number"
            min={1}
            value={capacityInput}
            onChange={(event) => setCapacityInput(event.target.value)}
            error={capacityError ?? undefined}
            disabled={saving}
          />
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <h2 className="font-heading mb-3">휴무일</h2>
          {holidays.length > 0 ? (
            <ul className="flex flex-col gap-2">
              {holidays.map((date) => (
                <li
                  key={date}
                  className="flex items-center justify-between border-b border-border pb-2"
                >
                  <span className="font-body tabular-nums">{date}</span>
                  <Button
                    type="button"
                    variant="secondary"
                    className="h-9 w-auto px-3"
                    onClick={() => removeHoliday(date)}
                    disabled={saving}
                  >
                    삭제
                  </Button>
                </li>
              ))}
            </ul>
          ) : null}
          <div className="mt-3 flex items-end gap-2">
            <TextField
              id="new-holiday"
              name="newHoliday"
              label="휴무일 날짜"
              type="date"
              value={newHoliday}
              onChange={(event) => setNewHoliday(event.target.value)}
              disabled={saving}
              className="flex-1"
            />
            <Button
              type="button"
              variant="secondary"
              className="h-[52px] w-auto px-4"
              onClick={addHoliday}
              disabled={saving}
            >
              휴무일 추가
            </Button>
          </div>
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <p className="font-caption text-text-weak">
            설정을 바꿔도 이미 확정된 주문의 픽업 시간대는 바뀌지 않아요
          </p>
        </div>

        {saveSummary ? (
          <div role="status" className="rounded-md bg-brand-weak p-3">
            <p className="font-caption text-brand">
              {`설정을 저장했어요 · 제외된 미래 시간대 ${saveSummary.excludedFutureSlotCount}개, 유지된 확정 주문 ${saveSummary.keptConfirmedOrderCount}건`}
            </p>
          </div>
        ) : null}

        {saveError ? <ErrorState message={saveError} onRetry={() => void submit()} /> : null}

        <Button type="submit" disabled={saving}>
          {saving ? "저장하는 중이에요" : "저장"}
        </Button>
      </form>
    </div>
  );
}
