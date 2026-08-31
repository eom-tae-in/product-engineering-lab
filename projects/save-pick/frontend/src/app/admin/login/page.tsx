"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAdminAuth } from "@/lib/auth/admin-auth";
import { ApiError } from "@/lib/api-client";
import { DEFAULT_ERROR_MESSAGES } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { Skeleton } from "@/components/ui/Skeleton";

/**
 * SC-101 · 관리자 로그인 (docs/06-screen-list.md §4).
 * `app/admin/layout.tsx`의 AdminGate가 이 경로만 인증 가드 없이 통과시킨다.
 * 고객 계정으로 시도하면 API-101이 FORBIDDEN을 준다(어떤 데이터도 함께 오지 않는다).
 */
export default function AdminLoginPage() {
  const auth = useAdminAuth();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (auth.status === "authenticated") {
      router.replace("/admin");
    }
  }, [auth.status, router]);

  if (auth.status !== "guest") {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-7 w-24" />
        <Skeleton className="h-[52px] w-full" />
        <Skeleton className="h-[52px] w-full" />
      </div>
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);
    try {
      await auth.login(email, password);
      router.push("/admin");
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === "INVALID_CREDENTIALS") {
          setErrorMessage("이메일 또는 비밀번호를 확인해주세요");
        } else if (error.code === "FORBIDDEN") {
          setErrorMessage("관리자 권한이 없는 계정이에요");
        } else if (error.code === "LOGIN_BLOCKED") {
          setErrorMessage("로그인 시도가 많아 10분간 잠겼어요");
        } else {
          setErrorMessage(error.defaultMessage);
        }
      } else {
        setErrorMessage(DEFAULT_ERROR_MESSAGES.INTERNAL_ERROR);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 flex-col p-4">
      {errorMessage ? (
        <div role="alert" className="font-caption mb-4 rounded-md bg-danger-weak px-4 py-3 text-danger">
          {errorMessage}
        </div>
      ) : null}

      <form onSubmit={handleSubmit} noValidate className="flex flex-1 flex-col">
        <p className="font-display mb-1 text-brand">savePick</p>
        <p className="font-caption mb-4 text-text-weak">관리자 계정은 운영자가 직접 부여해요</p>

        <TextField
          id="admin-email"
          name="email"
          type="email"
          label="이메일"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          id="admin-password"
          name="password"
          type="password"
          label="비밀번호"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        <div className="mt-auto pt-4">
          <Button type="submit" disabled={submitting}>
            {submitting ? "확인하는 중이에요" : "관리자 로그인"}
          </Button>
        </div>
      </form>
    </div>
  );
}
