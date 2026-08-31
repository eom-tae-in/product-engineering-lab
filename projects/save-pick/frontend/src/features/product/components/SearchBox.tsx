"use client";

import { useState, type FormEvent } from "react";
import { usePathname, useRouter } from "next/navigation";
import { TextField } from "@/components/ui/TextField";
import { Button } from "@/components/ui/Button";

export interface SearchBoxProps {
  initialKeyword: string;
}

/** SC-002 검색 입력창 (docs/06-screen-list.md §3 SC-002 주요 액션: 검색어 입력·삭제, 취소). */
export function SearchBox({ initialKeyword }: SearchBoxProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [value, setValue] = useState(initialKeyword);

  function submit(nextValue: string) {
    const trimmed = nextValue.trim();
    const query = new URLSearchParams();
    if (trimmed) query.set("keyword", trimmed);
    const qs = query.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    submit(value);
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="flex items-end gap-2">
      <TextField
        id="search-keyword"
        name="keyword"
        label="상품명으로 검색"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder="상품명을 입력해주세요"
        className="flex-1"
      />
      <div className="mb-4 flex gap-2">
        <Button type="submit" variant="secondary" className="h-[52px] w-auto px-4">
          검색
        </Button>
        <Button
          type="button"
          variant="text"
          className="h-[52px] w-auto px-2"
          onClick={() => {
            setValue("");
            router.push("/");
          }}
        >
          취소
        </Button>
      </div>
    </form>
  );
}
