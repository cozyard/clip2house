import type { ReactNode } from "react";
import { itemEmoji } from "../lib/emoji";

interface ItemCardProps {
  itemName: string;
  description: string;
  meta?: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}

export function ItemCard({ itemName, description, meta, onClick, disabled }: ItemCardProps) {
  const style = "flex w-full items-start gap-3.5 rounded-card border-2 border-line bg-surface p-3.5 text-left shadow-[0_2px_0_var(--color-line)]";
  const content = <>
    <Thumb emoji={itemEmoji(itemName)} />
    <span className="min-w-0 flex-1">
      <span className="mb-0.5 block text-[15px] font-extrabold tracking-[-.2px]">{itemName}</span>
      <span className="mb-1.5 line-clamp-2 text-[13px] font-medium text-ink-2">{description}</span>
      {meta && <span className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs font-semibold text-mut">{meta}</span>}
    </span>
  </>;
  return <li data-tour={onClick ? "listing" : undefined} className="rounded-card">
    {onClick ? <button type="button" disabled={disabled} onClick={onClick}
      className={`${style} cursor-pointer transition-colors hover:border-accent-line active:translate-y-px disabled:cursor-wait disabled:opacity-60`}>{content}</button>
      : <div className={style}>{content}</div>}
  </li>;
}

export function Thumb({ emoji, size = "md" }: { emoji: string; size?: "md" | "sm" }) {
  const sizing = size === "md" ? "h-14 w-14 text-[28px]" : "h-10 w-10 text-xl";
  return <span aria-hidden="true" className={`flex shrink-0 items-center justify-center rounded-2xl border-2 border-line bg-bg ${sizing}`}>
    <span className={emoji === "📎" ? "red-clip" : ""}>{emoji}</span>
  </span>;
}

export function Dot() {
  return <span aria-hidden="true">·</span>;
}
