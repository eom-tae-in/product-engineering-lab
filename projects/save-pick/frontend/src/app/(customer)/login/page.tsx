"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { ApiError } from "@/lib/api-client";
import { formatKstTime } from "@/lib/format";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type Banner =
  | { kind: "message"; text: string }
  | { kind: "retry"; text: string };

/**
 * SC-012 · 로그인 (docs/06-screen-list.md §3).
 * 비로그인 전용 화면이다. 로그인 상태로 진입하면 홈으로 돌려보낸다 — 원래는 진입
 * 직전 화면으로 돌아가는 게 이상적이지만, 이번 슬라이스에는 그 화면들이 아직 없어
 * 단순화했다(다음 슬라이스에서 개선 대상).
 */
export default function LoginPage() {
  const auth = useAuth();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const [banner, setBanner] = useState<Banner | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (auth.status === "authenticated") {
      router.replace("/");
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

    if (!EMAIL_REGEX.test(email)) {
      setEmailError("이메일 형식으로 입력해주세요");
      return;
    }
    setEmailError(null);
    setBanner(null);
    setSubmitting(true);
    try {
      await auth.login(email, password);
      router.push("/");
    } catch (error) {
      if (error instanceof ApiError && error.code === "INVALID_CREDENTIALS") {
        setBanner({ kind: "message", text: "이메일 또는 비밀번호를 확인해주세요" });
      } else if (error instanceof ApiError && error.code === "LOGIN_BLOCKED") {
        const retryAfterAt = error.details?.retryAfterAt;
        const time = typeof retryAfterAt === "string" ? formatKstTime(retryAfterAt) : null;
        setBanner({
          kind: "message",
          text: time
            ? `로그인 시도가 많아 10분간 잠겼어요. ${time} 이후 다시 시도해주세요`
            : "로그인 시도가 많아 10분간 잠겼어요",
        });
      } else {
        setBanner({ kind: "retry", text: "로그인하지 못했어요" });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 flex-col p-4">
      {banner?.kind === "message" ? (
        <div role="alert" className="font-caption mb-4 rounded-md bg-danger-weak px-4 py-3 text-danger">
          {banner.text}
        </div>
      ) : null}

      <form onSubmit={handleSubmit} noValidate className="flex flex-1 flex-col">
        <p className="font-caption mb-4 text-text-weak">장바구니에 담은 상품은 그대로 유지돼요</p>

        <TextField
          id="login-email"
          name="email"
          type="email"
          label="이메일"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={emailError ?? undefined}
          disabled={submitting}
        />
        <TextField
          id="login-password"
          name="password"
          type="password"
          label="비밀번호"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          disabled={submitting}
        />

        {banner?.kind === "retry" ? (
          <ErrorState message={banner.text} onRetry={() => setBanner(null)} />
        ) : null}

        <div className="mt-auto flex flex-col gap-2 pt-4">
          <Button type="submit" disabled={submitting}>
            {submitting ? "로그인하는 중이에요" : "로그인"}
          </Button>
          <Link
            href="/signup"
            className="font-body flex h-11 items-center justify-center text-brand"
          >
            회원가입
          </Link>
        </div>
      </form>
    </div>
  );
}
