"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { signup } from "@/features/account/api";
import { ApiError, getStoredGuestToken } from "@/lib/api-client";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface FieldErrors {
  email?: string;
  password?: string;
  name?: string;
  phone?: string;
}

/**
 * SC-013 · 회원가입 (docs/06-screen-list.md §3).
 * 가입 성공 응답을 바로 useAuth().setSession에 넘겨 별도 로그인 없이 인증 상태로
 * 전환한다(API-001). 진입 직전 화면 복귀는 아직 없어 홈으로 단순화했다.
 */
export default function SignupPage() {
  const auth = useAuth();
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [duplicateEmail, setDuplicateEmail] = useState(false);
  const [communicationError, setCommunicationError] = useState(false);
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

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!EMAIL_REGEX.test(email)) errors.email = "이메일 형식으로 입력해주세요";
    if (password.length < 8) errors.password = "비밀번호는 8자 이상이어야 해요";
    if (!name.trim()) errors.name = "이름을 입력해주세요";
    if (!phone.trim()) errors.phone = "휴대폰 번호를 입력해주세요";
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const errors = validate();
    setFieldErrors(errors);
    setDuplicateEmail(false);
    setCommunicationError(false);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const response = await signup({
        email,
        password,
        name,
        phone,
        guestToken: getStoredGuestToken() ?? undefined,
      });
      auth.setSession(
        { memberId: response.memberId, name: response.name, role: response.role },
        response.accessToken
      );
      router.push("/");
    } catch (error) {
      if (error instanceof ApiError && error.code === "EMAIL_DUPLICATED") {
        setDuplicateEmail(true);
      } else {
        setCommunicationError(true);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 flex-col p-4">
      {duplicateEmail ? (
        <div role="alert" className="font-caption mb-4 rounded-md bg-danger-weak px-4 py-3 text-danger">
          이미 가입된 이메일이에요
        </div>
      ) : null}

      <form onSubmit={handleSubmit} noValidate className="flex flex-1 flex-col">
        <p className="font-caption mb-4 text-text-weak">이름과 휴대폰 번호는 매장 픽업 응대에만 써요</p>

        <TextField
          id="signup-email"
          name="email"
          type="email"
          label="이메일"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldErrors.email}
        />
        <TextField
          id="signup-password"
          name="password"
          type="password"
          label="비밀번호 (8자 이상)"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldErrors.password}
        />
        <TextField
          id="signup-name"
          name="name"
          type="text"
          label="이름"
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={fieldErrors.name}
        />
        <TextField
          id="signup-phone"
          name="phone"
          type="tel"
          label="휴대폰 번호"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          error={fieldErrors.phone}
        />

        {duplicateEmail ? (
          <Link
            href="/login"
            className="font-body mb-4 flex h-12 items-center justify-center rounded-md border border-border text-text"
          >
            로그인하기
          </Link>
        ) : null}

        {communicationError ? (
          <ErrorState message="가입하지 못했어요" onRetry={() => setCommunicationError(false)} />
        ) : null}

        <div className="mt-auto pt-4">
          <Button type="submit" disabled={submitting}>
            {submitting ? "가입하는 중이에요" : "가입하고 계속하기"}
          </Button>
        </div>
      </form>
    </div>
  );
}
