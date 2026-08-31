"use client";

import { ProductForm } from "@/features/admin-product/components/ProductForm";

/** SC-104 · 상품 등록 (docs/06-screen-list.md §4 "기본(등록)"). */
export default function AdminProductNewPage() {
  return <ProductForm mode="create" />;
}
