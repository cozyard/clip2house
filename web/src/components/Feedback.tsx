import type { ReactNode } from "react";
import { Mascot, type MascotPose } from "./Mascot";
import { btn } from "../lib/ui";

export function SkeletonList({ count = 5 }: { count?: number }) {
  return <>{Array.from({ length: count }, (_, i) => (
    <li key={i} aria-hidden="true" className="h-[112px] animate-shimmer rounded-card border-2 border-line/60 bg-[linear-gradient(90deg,#ececec_25%,#f8f8f8_50%,#ececec_75%)] bg-[length:200%_100%]" />
  ))}</>;
}

export function ErrorState({ onRetry }: { onRetry: () => void }) {
  return <div role="alert" className="px-5 py-12 text-center text-sm text-ink-2">
    <p className="mb-3">불러오지 못했어요. 다시 시도해 주세요.</p>
    <button className={btn} onClick={onRetry}>다시 시도</button>
  </div>;
}

export function EmptyState({ emoji, mascot, children }: { emoji?: string; mascot?: MascotPose; children: ReactNode }) {
  return <div className="px-5 py-12 text-center text-sm leading-relaxed text-ink-2">
    {mascot ? <Mascot pose={mascot} size={104} className="mx-auto mb-3" /> : <span aria-hidden="true" className="mb-3 inline-block text-5xl">{emoji}</span>}
    <div>{children}</div>
  </div>;
}

export function RankEmptyState({ children }: { children: ReactNode }) {
  return <div className="px-5 py-10 text-center text-sm leading-relaxed text-ink-2">
    <svg viewBox="0 0 180 128" aria-hidden="true" className="mx-auto mb-5 h-32 w-44" fill="none">
      <rect x="26" y="12" width="128" height="104" rx="16" fill="white" stroke="#d9d0c6" strokeWidth="2" />
      <path d="M44 32h40" stroke="#9b7253" strokeWidth="5" strokeLinecap="round" />
      <rect x="38" y="46" width="104" height="18" rx="6" fill="#fff1e6" />
      <circle cx="49" cy="55" r="5" fill="#d89659" />
      <path d="M66 55h43" stroke="#d5b99f" strokeWidth="4" strokeLinecap="round" />
      <circle cx="49" cy="78" r="5" fill="#d9d6d1" />
      <path d="M66 78h58" stroke="#e8e2db" strokeWidth="4" strokeLinecap="round" />
      <circle cx="49" cy="99" r="5" fill="#e4c8af" />
      <path d="M66 99h34" stroke="#e8e2db" strokeWidth="4" strokeLinecap="round" />
    </svg>
    <div>{children}</div>
  </div>;
}
