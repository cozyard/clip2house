const btnBase =
  "cursor-pointer select-none rounded-2xl px-4 py-2.5 text-sm font-extrabold " +
  "transition-all duration-100 active:translate-y-[3px] active:shadow-none " +
  "disabled:cursor-default disabled:opacity-60 disabled:active:translate-y-0";

export const btn =
  `${btnBase} border-2 border-line bg-surface text-ink-2 ` +
  "shadow-[0_3px_0_var(--color-line)] hover:bg-bg";

export const btnPrimary =
  `${btnBase} bg-accent text-white ` +
  "shadow-[0_3px_0_var(--color-accent-deep)] hover:brightness-105";

export const btnSuccess =
  `${btnBase} bg-success text-white ` +
  "shadow-[0_3px_0_var(--color-success-deep)] hover:brightness-105";

export const inputCls =
  "w-full rounded-2xl border-2 border-line bg-surface px-4 py-3 text-sm font-medium " +
  "placeholder:text-mut transition duration-150 " +
  "focus:border-accent focus:outline-none focus:ring-[3px] focus:ring-accent-soft";
