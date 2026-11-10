import { createPortal } from "react-dom";
import { useModalFocus } from "../hooks/useModalFocus";
import { ErrorState, SkeletonList } from "../components/Feedback";
import { useLayoutEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Navigate, useNavigate, useParams } from "react-router";
import { api } from "../api/client";
import { useMe, useNegotiation } from "../api/queries";
import type { AcceptPayload, NegotiationView, Offer, TradeResult } from "../api/types";
import { ItemCard, Thumb } from "../components/ItemCard";
import { useToast } from "../components/Toast";
import { useNegotiationStream } from "../hooks/useNegotiationStream";
import { itemEmoji } from "../lib/emoji";
import handshakeIcon from "../assets/items/handshake.webp";
import { clock, responseTime } from "../lib/time";
import { btn, btnPrimary, btnSuccess } from "../lib/ui";

export function ChatScreen() {
  const { negotiationId: idParam } = useParams();
  const negotiationId = Number(idParam);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [draft, setDraft] = useState("");
  const [pickerOpen, setPickerOpen] = useState(false);
  const [reward, setReward] = useState<TradeResult | null>(null);

  const cached = queryClient.getQueryData<NegotiationView>(["negotiation", negotiationId]);
  const active = cached ? cached.status === "ACTIVE" : true;
  const streamOpen = useNegotiationStream(negotiationId, active);
  const negotiation = useNegotiation(negotiationId, streamOpen);
  const view = negotiation.data;

  const send = useMutation({
    mutationFn: (content: string) =>
      api(`/api/negotiations/${negotiationId}/messages`, {
        method: "POST",
        body: JSON.stringify({ content }),
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["negotiation", negotiationId] }),
    onError: (e, content) => {
      toast(e.message);
      setDraft(content);
    },
  });

  const placeOffer = useMutation({
    mutationFn: (holdingId: number) =>
      api(`/api/negotiations/${negotiationId}/offers`, {
        method: "POST",
        body: JSON.stringify({ holdingId }),
      }),
    onSuccess: () => {
      toast("교환을 제안했어요.");
      queryClient.invalidateQueries({ queryKey: ["negotiation", negotiationId] });
    },
    onError: (e) => toast(e.message),
  });

  const accept = useMutation({
    mutationFn: (payload: AcceptPayload) =>
      api<TradeResult>(`/api/negotiations/${negotiationId}/accept`, {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify(payload),
      }),
    onSuccess: (result) => {
      toast(
        `거래 성사! ${result.givenItemName} → ${result.receivedItemName}` +
          (result.cleared ? " · 🏠 클리어!" : ""),
      );
      queryClient.invalidateQueries({ queryKey: ["me"] });
      queryClient.invalidateQueries({ queryKey: ["negotiation", negotiationId] });

      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        navigate("/", { state: { tab: "me" } });
      } else {
        setReward(result);
      }
    },
    onError: (e) => {

      toast(e.message);
      queryClient.invalidateQueries({ queryKey: ["negotiation", negotiationId] });
    },
  });

  const decline = useMutation({
    mutationFn: (offerId: number) =>
      api(`/api/negotiations/${negotiationId}/offers/${offerId}/decline`, { method: "POST" }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["negotiation", negotiationId] }),
    onError: (e) => toast(e.message),
  });

  const sendDraft = () => {
    const content = draft.trim();
    if (!content || send.isPending) return;
    setDraft("");
    send.mutate(content);
  };

  if (!Number.isFinite(negotiationId)) {
    return <Navigate to="/" replace />;
  }

  return (
    <section className="flex h-dvh flex-col bg-chat-bg">
      <header className="flex items-center gap-2 border-b-2 border-line bg-surface px-4 py-3">
        <button
          onClick={() => navigate("/")}
          aria-label="뒤로"
          className="cursor-pointer rounded-xl px-2.5 py-1 text-xl font-black text-ink-2 transition hover:bg-bg"
        >
          ←
        </button>
        <Thumb size="sm" emoji={view ? itemEmoji(view.listingItemName) : "📦"} />
        <div className="min-w-0">
          <div className="text-[15px] font-extrabold tracking-[-.2px]">{view?.listingItemName}</div>
          <div className="flex min-w-0 flex-wrap items-center gap-x-1.5 text-xs font-semibold text-mut">
            <span>{view?.sellerNickname}</span>
            {view && responseTime(view.sellerAvgResponseSeconds) && (
              <>
                <span aria-hidden>·</span>
                <span className="font-semibold text-success-deep">
                  {responseTime(view.sellerAvgResponseSeconds)}
                </span>
              </>
            )}
          </div>
        </div>
      </header>

      {view && view.status !== "ACTIVE" && (
        <div className="border-b-2 border-danger/20 bg-danger-bg px-[18px] py-3 text-center text-[13px] font-extrabold text-danger">
          {view.statusDetail || "협상이 종료되었습니다."}
        </div>
      )}

      {negotiation.isError ? <ErrorState onRetry={() => void negotiation.refetch()} /> : negotiation.isPending ? <p role="status" className="flex-1 p-5 text-sm text-ink-2">대화 불러오는 중…</p> : <MessageList view={view} />}

      <div className="px-4">
        {view?.offers.map((offer) => (
          <OfferCard
            key={offer.offerId}
            view={view}
            offer={offer}
            onAccept={(payload) => accept.mutate(payload)}
            onDecline={(offerId) => decline.mutate(offerId)}
            busy={accept.isPending || decline.isPending}
          />
        ))}
      </div>

      <div className="flex gap-2 border-t-2 border-line bg-surface px-3 pt-2.5 pb-[calc(10px+env(safe-area-inset-bottom))]">
        <button
          onClick={() => setPickerOpen(true)}
          title="내 물건으로 교환 제안"
          disabled={!view || view.status !== "ACTIVE" || placeOffer.isPending}
          className={`${btn} px-3`}
        >
          <img src={handshakeIcon} alt="교환 제안" className="h-6 w-6 object-contain" />
        </button>
        <input
          type="text"
          aria-label="메시지"
          disabled={!view || view.status !== "ACTIVE" || send.isPending}
          placeholder="메시지 보내기"
          maxLength={2000}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.nativeEvent.isComposing) sendDraft();
          }}
          className="min-w-0 flex-1 rounded-2xl border-2 border-line bg-surface py-2.5 pr-3 pl-4 text-sm font-medium placeholder:text-mut transition focus:border-accent focus:outline-none focus:ring-[3px] focus:ring-accent-soft"
        />
        <button disabled={!view || view.status !== "ACTIVE" || send.isPending || !draft.trim()} onClick={sendDraft} className={`${btnPrimary} px-5`}>
          {send.isPending ? "전송 중" : "전송"}
        </button>
      </div>

      {pickerOpen && (
        <HoldingPicker
          onPick={(holdingId) => {
            setPickerOpen(false);
            placeOffer.mutate(holdingId);
          }}
          onClose={() => setPickerOpen(false)}
        />
      )}

      {reward && (
        <TradeReward
          itemName={reward.receivedItemName}
          onDone={() => navigate("/", { state: { tab: "me" } })}
        />
      )}
    </section>
  );
}

function TradeReward({ itemName, onDone }: { itemName: string; onDone: () => void }) {
  const emoji = itemEmoji(itemName);
  return (
    <div className="fixed inset-0 z-30 flex animate-fade items-center justify-center bg-[rgb(20_23_28/.5)] backdrop-blur-[2px]">
      <div
        className="relative flex animate-trade-reward flex-col items-center"
        onAnimationEnd={(e) => {
          if (e.target === e.currentTarget) onDone();
        }}
      >
        <div className="relative grid h-28 w-28 place-items-center">

          <span className="pointer-events-none absolute inset-0 animate-trade-ring rounded-full border-4 border-gold" />

          <div className="grid h-28 w-28 place-items-center rounded-[28px] border-2 border-accent-line bg-surface text-[64px] shadow-[0_6px_0_var(--color-accent-line)]">
            <span className={emoji === "📎" ? "red-clip" : ""}>{emoji}</span>
          </div>
        </div>
        <div className="mt-4 rounded-full border-2 border-line bg-surface px-4 py-1.5 text-sm font-extrabold tracking-[-.2px] shadow-[0_2px_0_var(--color-line)]">
          획득! {itemName}
        </div>
      </div>
    </div>
  );
}

function MessageList({ view }: { view: NegotiationView | undefined }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const messageCount = view?.messages.length ?? 0;

  useLayoutEffect(() => {
    const el = containerRef.current;
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight;
  }, [messageCount]);

  const last = view?.messages[view.messages.length - 1];
  const waiting = view?.status === "ACTIVE" && last?.sender === "PLAYER";
  const waitHint = waiting ? responseTime(view!.sellerAvgResponseSeconds) : null;

  return (
    <div
      ref={containerRef}
      onScroll={(e) => {
        const el = e.currentTarget;
        stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
      }}
      className="flex flex-1 flex-col gap-[3px] overflow-y-auto px-4 py-[18px]"
    >
      {view?.messages.map((m) => {
        const mine = m.sender === "PLAYER";
        return (
          <div
            key={m.id}
            className={`mb-1.5 max-w-[78%] animate-pop border-2 px-3.5 py-2.5 text-sm leading-normal font-medium whitespace-pre-wrap [word-break:break-word] ${
              mine
                ? "self-end rounded-[18px_18px_6px_18px] border-accent-line bg-mine"
                : "self-start rounded-[18px_18px_18px_6px] border-line bg-surface"
            }`}
          >
            {m.content}
            <span className="mt-1 block text-right text-[10px] font-bold text-mut">
              {mine && m.readAt ? "읽음 · " : ""}
              {clock(m.createdAt)}
            </span>
          </div>
        );
      })}
      {waiting && (
        <div className="mt-1 flex items-center gap-2 self-center rounded-full bg-surface/80 px-3.5 py-1.5 text-[12px] font-semibold text-mut">
          <span className="flex gap-1">
            <span className="h-1.5 w-1.5 animate-bob rounded-full bg-mut [animation-duration:1s]" />
            <span className="h-1.5 w-1.5 animate-bob rounded-full bg-mut [animation-delay:.15s] [animation-duration:1s]" />
            <span className="h-1.5 w-1.5 animate-bob rounded-full bg-mut [animation-delay:.3s] [animation-duration:1s]" />
          </span>
          {waitHint ? `답장 기다리는 중 · ${waitHint}` : "답장 기다리는 중"}
        </div>
      )}
    </div>
  );
}

function OfferCard({
  view,
  offer,
  onAccept,
  onDecline,
  busy,
}: {
  view: NegotiationView;
  offer: Offer;
  onAccept: (payload: AcceptPayload) => void;
  onDecline: (offerId: number) => void;
  busy: boolean;
}) {
  const myItem = offer.playerItemName || "물건";
  const swap = (
    <div className="my-2.5 flex items-center justify-center gap-2.5 rounded-2xl border-2 border-line/70 bg-surface px-3 py-2.5 text-sm font-extrabold">
      <span>
        {itemEmoji(myItem)} {myItem}
      </span>
      <span className="text-lg text-accent">⇄</span>
      <span>
        {itemEmoji(view.listingItemName)} {view.listingItemName}
      </span>
    </div>
  );

  if (offer.proposer === "PERSONA") {
    return (
      <div className="mb-2.5 animate-pop rounded-card border-2 border-accent-line bg-accent-soft px-4 py-3.5 text-sm shadow-[0_2px_0_var(--color-accent-line)]">
        <div className="flex items-center gap-1.5 font-bold">
          <img src={handshakeIcon} alt="" className="h-5 w-5 object-contain" />
          <span>
            <b className="font-extrabold">{view.sellerNickname}</b>님이 교환을 제안했어요
          </span>
        </div>
        {swap}
        <div className="mt-3 flex gap-2">
          <button
            disabled={busy}
            onClick={() =>
              onAccept({
                offerId: offer.offerId,
                expectedListingVersion: view.listingVersion,
                expectedHoldingVersion: offer.playerHoldingVersion,
              })
            }
            className={`${btnSuccess} flex-1`}
          >
            수락
          </button>
          <button disabled={busy} onClick={() => onDecline(offer.offerId)} className={btn}>
            거절
          </button>
        </div>
      </div>
    );
  }
  return (
    <div className="mb-2.5 animate-pop rounded-card border-2 border-line bg-surface px-4 py-3.5 text-sm shadow-[0_2px_0_var(--color-line)]">
      <div className="font-bold text-mut">내 제안 · 답장 대기</div>
      {swap}
    </div>
  );
}

function HoldingPicker({
  onPick,
  onClose,
}: {
  onPick: (holdingId: number) => void;
  onClose: () => void;
}) {
  const me = useMe();
  const dialogRef = useModalFocus(onClose);
  return createPortal(
    <div
      onClick={onClose}
      className="fixed inset-0 z-20 flex animate-fade items-end justify-center bg-[rgb(20_23_28/.45)]"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="holding-picker-title"
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[70vh] w-full max-w-[520px] animate-slide-up overflow-y-auto rounded-t-[24px] border-t-2 border-line bg-surface px-[18px] py-[22px]"
      >
        <h3 id="holding-picker-title" className="mb-3.5 text-base font-extrabold">교환할 물건 선택</h3>
        <ul className="flex flex-col gap-3">
          {me.isPending && <SkeletonList count={2} />}
          {me.data?.holdings.map((h) => (
            <ItemCard
              key={h.holdingId}
              itemName={h.itemName}
              description={h.description}
              onClick={() => onPick(h.holdingId)}
            />
          ))}
        </ul>
        {me.isError && <ErrorState onRetry={() => void me.refetch()} />}
        {me.data?.holdings.length === 0 && <p className="py-4 text-sm text-ink-2">교환할 물건이 없어요.</p>}
        <button onClick={onClose} className={`${btn} mt-4 w-full`}>
          닫기
        </button>
      </div>
    </div>, document.body,
  );
}
