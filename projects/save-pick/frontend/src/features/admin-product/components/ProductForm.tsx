"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/TextField";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { ApiError } from "@/lib/api-client";
import { formatKstTime } from "@/lib/format";
import { createProduct, updateProduct } from "../api";
import type { AdminProductDetailResponse } from "../types";

/** BR-014: 영업 종료 시각 22:00. 마감 시각은 이 시각을 넘을 수 없다. */
const BUSINESS_CLOSE_MINUTES = 22 * 60;

export interface ProductFormProps {
  mode: "create" | "edit";
  productId?: number;
  initialProduct?: AdminProductDetailResponse;
}

interface FieldErrors {
  name?: string;
  saleUnit?: string;
  originalPrice?: string;
  closingAt?: string;
  maxOrderQuantity?: string;
}

/** "2026-08-28T21:00:00+09:00" → "2026-08-28T21:00" (datetime-local 입력값). */
function toDateTimeLocalValue(iso: string): string {
  return iso.slice(0, 16);
}

/** "2026-08-28T21:00" → "2026-08-28T21:00:00+09:00" (11번 §0.4, KST 고정 서비스). */
function toIsoWithKstOffset(dateTimeLocal: string): string {
  return `${dateTimeLocal}:00+09:00`;
}

function closingTimeError(dateTimeLocal: string): string | undefined {
  if (!dateTimeLocal) return "마감 시각을 입력해주세요";
  const closingDate = new Date(toIsoWithKstOffset(dateTimeLocal));
  if (closingDate.getTime() <= Date.now()) {
    return "마감 시각은 현재 시각 이후로 정해주세요";
  }
  const closingMinutes = Number(dateTimeLocal.slice(11, 13)) * 60 + Number(dateTimeLocal.slice(14, 16));
  if (closingMinutes > BUSINESS_CLOSE_MINUTES) {
    return "마감 시각은 영업 종료 시각 22:00을 넘을 수 없어요";
  }
  return undefined;
}

/**
 * SC-104 · 상품 등록·수정 (docs/06-screen-list.md §4).
 * `/admin/products/new`(등록)와 `/admin/products/[id]`(수정)가 공유한다.
 */
export function ProductForm({ mode, productId, initialProduct }: ProductFormProps) {
  const router = useRouter();

  const [name, setName] = useState(initialProduct?.name ?? "");
  const [description, setDescription] = useState(initialProduct?.description ?? "");
  const [saleUnit, setSaleUnit] = useState(initialProduct?.saleUnit ?? "");
  const [originalPriceInput, setOriginalPriceInput] = useState(
    initialProduct ? String(initialProduct.originalPrice) : ""
  );
  const [closingAtInput, setClosingAtInput] = useState(
    initialProduct ? toDateTimeLocalValue(initialProduct.closingAt) : ""
  );
  const [maxOrderQuantityInput, setMaxOrderQuantityInput] = useState(
    initialProduct ? String(initialProduct.maxOrderQuantity) : "5"
  );

  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [confirmSheet, setConfirmSheet] = useState<{
    affectedConfirmedOrderCount: number;
  } | null>(null);

  const isClosed = initialProduct?.status === "CLOSED";

  function validate(): boolean {
    const errors: FieldErrors = {};
    if (!name.trim()) errors.name = "상품명을 입력해주세요";
    if (!saleUnit.trim()) errors.saleUnit = "판매 단위를 입력해주세요";

    const originalPrice = Number(originalPriceInput);
    if (!originalPriceInput || Number.isNaN(originalPrice)) {
      errors.originalPrice = "정가를 입력해주세요";
    } else if (originalPrice < 100) {
      errors.originalPrice = "정가는 100원 이상이어야 해요";
    }

    const closingError = closingTimeError(closingAtInput);
    if (closingError) errors.closingAt = closingError;

    const maxOrderQuantity = Number(maxOrderQuantityInput);
    if (!maxOrderQuantityInput || !Number.isInteger(maxOrderQuantity) || maxOrderQuantity < 1) {
      errors.maxOrderQuantity = "1회 주문 최대 수량은 1 이상이어야 해요";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function submit(confirmEarlierClosing = false) {
    setSubmitError(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      if (mode === "create") {
        const created = await createProduct({
          name: name.trim(),
          description: description.trim(),
          saleUnit: saleUnit.trim(),
          originalPrice: Number(originalPriceInput),
          closingAt: toIsoWithKstOffset(closingAtInput),
          maxOrderQuantity: Number(maxOrderQuantityInput),
        });
        router.push(`/admin/products/${created.productId}`);
        return;
      }

      if (productId) {
        await updateProduct(productId, {
          name: name.trim(),
          description: description.trim(),
          saleUnit: saleUnit.trim(),
          originalPrice: Number(originalPriceInput),
          closingAt: toIsoWithKstOffset(closingAtInput),
          maxOrderQuantity: Number(maxOrderQuantityInput),
          ...(confirmEarlierClosing ? { confirmEarlierClosing: true } : {}),
        });
        setConfirmSheet(null);
        router.push("/admin/products");
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === "CLOSING_TIME_INVALID") {
        setFieldErrors((prev) => ({
          ...prev,
          closingAt: closingTimeError(closingAtInput) ?? "마감 시각을 확인해주세요",
        }));
      } else if (
        error instanceof ApiError &&
        error.code === "VALIDATION_ERROR" &&
        typeof error.details?.affectedConfirmedOrderCount === "number"
      ) {
        setConfirmSheet({
          affectedConfirmedOrderCount: error.details.affectedConfirmedOrderCount,
        });
      } else if (error instanceof ApiError && error.code === "PRODUCT_STATUS_TRANSITION_DENIED") {
        setSubmitError("마감된 상품의 마감 시각은 바꿀 수 없어요");
      } else if (error instanceof ApiError) {
        setSubmitError(error.defaultMessage);
      } else {
        setSubmitError("일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submit();
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">{mode === "create" ? "상품 등록" : "상품 수정"}</h1>

      <form onSubmit={handleSubmit} noValidate className="flex flex-col">
        <TextField
          id="product-name"
          name="name"
          label="상품명"
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={fieldErrors.name}
          disabled={submitting}
        />
        <TextField
          id="product-description"
          name="description"
          label="설명"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          disabled={submitting}
        />
        <TextField
          id="product-sale-unit"
          name="saleUnit"
          label="판매 단위"
          value={saleUnit}
          onChange={(event) => setSaleUnit(event.target.value)}
          error={fieldErrors.saleUnit}
          disabled={submitting}
        />
        <TextField
          id="product-original-price"
          name="originalPrice"
          label="정가"
          type="number"
          min={100}
          value={originalPriceInput}
          onChange={(event) => setOriginalPriceInput(event.target.value)}
          error={fieldErrors.originalPrice}
          disabled={submitting}
        />
        <TextField
          id="product-closing-at"
          name="closingAt"
          label="마감 시각"
          type="datetime-local"
          step={60}
          value={closingAtInput}
          onChange={(event) => setClosingAtInput(event.target.value)}
          error={fieldErrors.closingAt}
          disabled={submitting || isClosed}
        />
        {mode === "edit" && initialProduct ? (
          <p className="font-caption -mt-3 mb-4 text-text-weak">
            {`현재 적용 할인율 ${initialProduct.currentDiscountRate}% · 다음 구간(${initialProduct.nextDiscountRate}%) 진입 ${
              initialProduct.nextDiscountAt ? formatKstTime(initialProduct.nextDiscountAt) : "-"
            }`}
          </p>
        ) : null}
        <TextField
          id="product-max-order-quantity"
          name="maxOrderQuantity"
          label="1회 주문 최대 수량"
          type="number"
          min={1}
          value={maxOrderQuantityInput}
          onChange={(event) => setMaxOrderQuantityInput(event.target.value)}
          error={fieldErrors.maxOrderQuantity}
          disabled={submitting}
        />

        {mode === "create" ? (
          <p className="font-caption mb-3 text-text-weak">
            등록하면 DRAFT 상태가 되고 고객에게 보이지 않아요
          </p>
        ) : null}

        {isClosed ? (
          <p className="font-caption mb-3 text-danger">마감된 상품의 마감 시각은 바꿀 수 없어요</p>
        ) : null}

        {submitError ? (
          <p role="alert" className="font-caption mb-3 text-danger">
            {submitError}
          </p>
        ) : null}

        <Button type="submit" disabled={submitting}>
          {submitting ? "저장하는 중이에요" : mode === "create" ? "등록" : "저장"}
        </Button>
      </form>

      <BottomSheet open={confirmSheet !== null} onClose={() => setConfirmSheet(null)}>
        {confirmSheet ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">
              {`확정된 주문 ${confirmSheet.affectedConfirmedOrderCount}건의 픽업 시간대보다 빨라져요. 그래도 저장할까요?`}
            </p>
            <Button variant="danger" onClick={() => void submit(true)} disabled={submitting}>
              그래도 저장
            </Button>
            <Button variant="secondary" onClick={() => setConfirmSheet(null)} disabled={submitting}>
              취소
            </Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
