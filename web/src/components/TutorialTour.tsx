import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Mascot, type MascotPose } from "./Mascot";
import { useModalFocus } from "../hooks/useModalFocus";
import { btn, btnPrimary } from "../lib/ui";

const SEEN_KEY = "c2h_tutorial_v2";
export function shouldShowTutorial(): boolean {
  return !localStorage.getItem(SEEN_KEY);
}

type Tab = "feed" | "chat" | "me" | "rank";
type Step = { tab?: Tab; target?: string; pose: MascotPose; title: string; body: string };
const STEPS: Step[] = [
  { pose: "wave", title: "클립 하나로 집까지", body: "이웃과 물건을 바꾸며 집에 도전해 보세요. 거래 방법을 안내할게요." },
  { tab: "feed", target: "tab-feed", pose: "guide", title: "피드에서 물건 찾기", body: "교환하고 싶은 매물을 고르세요. 카드를 누르면 판매자와 채팅할 수 있어요." },
  { tab: "feed", target: "listing", pose: "guide", title: "거래 전 확인하기", body: "닉네임 옆에는 평균 응답 시간, 아래에는 매물 마감 시간이 표시돼요." },
  { tab: "chat", target: "tab-chat", pose: "guide", title: "채팅으로 교환 제안", body: "대화 중 교환 버튼을 눌러 내 물건을 제안하세요. 상대가 수락하면 거래가 완료돼요." },
  { tab: "me", target: "tab-me", pose: "guide", title: "내 물건 확인하기", body: "보유한 물건을 볼 수 있어요. 계정을 만들면 다른 기기에서도 이어 할 수 있어요." },
  { tab: "rank", target: "tab-rank", pose: "guide", title: "랭킹 확인하기", body: "보유 가치와 클리어 순서·시간·거래 횟수로 순위를 확인하세요." },
  { tab: "feed", pose: "cheer", title: "첫 거래를 시작해 보세요", body: "피드에서 마음에 드는 물건을 골라보세요. 도움이 필요하면 위쪽 수달을 눌러주세요." },
];

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), Math.max(min, max));
type Spotlight = { x: number; y: number; w: number; h: number; radii: number[] };
type Layout = { x: number; y: number; width: number; mascotX: number; mascotY: number; mascotSize: number; panelAbove: boolean; vw: number; vh: number; spot: Spotlight | null };

function roundedPath({ x, y, w, h, radii: [tl, tr, br, bl] }: Spotlight) {
  return `M${x + tl},${y} H${x + w - tr} A${tr},${tr} 0 0 1 ${x + w},${y + tr} V${y + h - br} A${br},${br} 0 0 1 ${x + w - br},${y + h} H${x + bl} A${bl},${bl} 0 0 1 ${x},${y + h - bl} V${y + tl} A${tl},${tl} 0 0 1 ${x + tl},${y} Z`;
}

export function TutorialTour({ setTab, onClose }: { setTab: (tab: Tab) => void; onClose: () => void }) {
  const [index, setIndex] = useState(0);
  const [layout, setLayout] = useState<Layout | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const bubbleRef = useRef<HTMLDivElement>(null);
  const step = STEPS[index];
  const previousStep = useRef(step);
  const close = useCallback(() => {
    localStorage.setItem(SEEN_KEY, "1");
    onClose();
  }, [onClose]);
  const dialogRef = useModalFocus(close, layout !== null);

  useLayoutEffect(() => {
    if (step.tab) setTab(step.tab);
    if (previousStep.current !== step && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      bubbleRef.current?.animate([{ opacity: 0 }, { opacity: 0, offset: 0.7 }, { opacity: 1 }], { duration: 620 });
    }
    previousStep.current = step;
    const measure = () => {
      const vw = dialogRef.current?.clientWidth ?? document.documentElement.clientWidth;
      const vh = window.innerHeight;
      const mascotSize = clamp(Math.round(vh * 0.2), 88, 144);
      const width = Math.min(vw - 24, 360);
      const height = panelRef.current?.getBoundingClientRect().height ?? 230;
      const target = step.target ? document.querySelector(`[data-tour="${step.target}"]`) : null;
      const rect = target?.getBoundingClientRect();
      const style = target ? getComputedStyle(target) : null;
      const visible = rect && rect.bottom > 0 && rect.top < vh;
      const spot = visible && style ? {
        x: rect.x, y: rect.y, w: rect.width, h: rect.height,
        radii: [style.borderTopLeftRadius, style.borderTopRightRadius, style.borderBottomRightRadius, style.borderBottomLeftRadius]
          .map(radius => Math.min(parseFloat(radius) || 0, rect.width / 2, rect.height / 2)),
      } : null;
      const mascotX = clamp(spot ? spot.x + spot.w / 2 - mascotSize / 2 : (vw - mascotSize) / 2, 12, vw - mascotSize - 12);
      const panelAbove = Boolean(spot && spot.y > vh / 2);
      const mascotY = panelAbove
        ? clamp(spot!.y - mascotSize - 12, height + 24, vh - mascotSize - 12)
        : clamp(spot ? spot.y + spot.h + 12 : (vh - height - mascotSize - 12) / 2, 12, vh - height - mascotSize - 24);
      const x = clamp(mascotX + mascotSize / 2 - width / 2, 12, vw - width - 12);
      const y = panelAbove ? mascotY - height - 12 : mascotY + mascotSize + 12;
      setLayout({ x, y, width, mascotX, mascotY, mascotSize, panelAbove, vw, vh, spot });
    };
    const frame = requestAnimationFrame(measure);
    const observer = new ResizeObserver(measure);
    if (panelRef.current) observer.observe(panelRef.current);
    const content = document.querySelector('[data-tour="content"]');
    if (content) observer.observe(content);
    window.addEventListener("resize", measure);
    window.addEventListener("scroll", measure, true);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      window.removeEventListener("resize", measure);
      window.removeEventListener("scroll", measure, true);
    };
  }, [step, setTab]);

  const hole = layout?.spot ? roundedPath(layout.spot) : "";
  return createPortal(
    <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="tour-title" aria-describedby="tour-description" tabIndex={-1}
      className="fixed inset-0 z-50">
      {layout && <svg aria-hidden="true" className="absolute inset-0 h-full w-full" viewBox={`0 0 ${layout.vw} ${layout.vh}`} preserveAspectRatio="none">
        <path d={`M0,0 H${layout.vw} V${layout.vh} H0 Z ${hole}`} fill="rgb(22 18 15 / 0.52)" fillRule="evenodd" />
        {hole && <path d={hole} fill="none" stroke="var(--color-accent)" strokeWidth="2" />}
      </svg>}
      {layout && <div className="pointer-events-none absolute left-0 top-0 transition-transform duration-[620ms] ease-[cubic-bezier(0.5,0,0.3,1)]"
        style={{ transform: `translate(${layout.mascotX}px, ${layout.mascotY}px)` }}>
        <Mascot key={index} pose={step.pose} size={layout.mascotSize} className={index > 0 ? "animate-tour-hop" : ""} />
      </div>}
      <div ref={bubbleRef} className="absolute" style={{ left: layout?.x ?? 12, top: layout?.y ?? 12, width: layout?.width ?? "min(360px, calc(100vw - 24px))", visibility: layout ? "visible" : "hidden" }}>
        {layout && <span aria-hidden="true" className="absolute h-4 w-4 rotate-45 border-line bg-surface"
          style={{ left: clamp(layout.mascotX + layout.mascotSize / 2 - layout.x - 8, 20, layout.width - 36),
            ...(layout.panelAbove ? { bottom: -7, borderRightWidth: 2, borderBottomWidth: 2 } : { top: -7, borderLeftWidth: 2, borderTopWidth: 2 }) }} />}
        <div ref={panelRef} className="overflow-y-auto rounded-card border-2 border-line bg-surface p-4 shadow-lg"
          style={{ maxHeight: `calc(100dvh - ${(layout?.mascotSize ?? 144) + 36}px)` }}>
          <div aria-live="polite" aria-atomic="true">
            <h2 id="tour-title" className="mb-2 text-base font-extrabold">{step.title}</h2>
            <p id="tour-description" className="text-sm leading-relaxed text-ink-2">{step.body}</p>
          </div>
          <div className="mt-2 flex items-center justify-between gap-3">
            <p className="text-xs font-semibold text-mut">{index + 1} / {STEPS.length}</p>
            <button onClick={close} className="min-h-11 cursor-pointer rounded-lg px-2 text-xs font-semibold text-ink-2 hover:bg-bg">건너뛰기</button>
          </div>
          <div className="mt-3 flex gap-2">
            {index > 0 && <button onClick={() => setIndex(index - 1)} className={`${btn} px-4`}>이전</button>}
            <button data-autofocus onClick={() => index === STEPS.length - 1 ? close() : setIndex(index + 1)} className={`${btnPrimary} flex-1`}>
              {index === STEPS.length - 1 ? "시작하기" : "다음"}
            </button>
          </div>
        </div>
      </div>
    </div>, document.body,
  );
}
