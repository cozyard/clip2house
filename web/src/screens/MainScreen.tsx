import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate } from "react-router";
import { api } from "../api/client";
import logo from "../assets/c2h.png";
import medalGold from "../assets/items/medal-gold.webp";
import medalSilver from "../assets/items/medal-silver.webp";
import medalBronze from "../assets/items/medal-bronze.webp";
import stallIcon from "../assets/items/stall.webp";
import chatIcon from "../assets/items/chat.webp";
import backpackIcon from "../assets/items/backpack.webp";
import trophyIcon from "../assets/items/trophy.webp";
import { useChats, useFeed, useLeaderboard, useMe } from "../api/queries";
import type { Board, Me, NegotiationOpened } from "../api/types";
import { Dot, ItemCard, Thumb } from "../components/ItemCard";
import { itemEmoji } from "../lib/emoji";
import { Mascot } from "../components/Mascot";
import { TutorialTour, shouldShowTutorial } from "../components/TutorialTour";
import { EmptyState, ErrorState, RankEmptyState, SkeletonList } from "../components/Feedback";
import { useToast } from "../components/Toast";
import { btnPrimary, inputCls } from "../lib/ui";
import { isUrgent, responseTime, timeAgo, timeLeft } from "../lib/time";

type Tab = "feed" | "chat" | "me" | "rank";

const TABS: Array<{ id: Tab; icon: string; label: string }> = [
  { id: "feed", icon: stallIcon, label: "피드" },
  { id: "chat", icon: chatIcon, label: "채팅" },
  { id: "me", icon: backpackIcon, label: "내 물건" },
  { id: "rank", icon: trophyIcon, label: "랭킹" },
];

export function MainScreen() {

  const initialTab = (useLocation().state as { tab?: Tab } | null)?.tab ?? "feed";
  const [tab, setTab] = useState<Tab>(initialTab);

  const [tourOpen, setTourOpen] = useState(shouldShowTutorial);
  const me = useMe();

  return (
    <section className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b-2 border-line bg-surface">
        <div className="relative flex h-[52px] items-center justify-end px-[18px]">

          <img
            src={logo}
            alt="클립 투 하우스"
            className="absolute -bottom-[22px] left-[14px] h-[52px] w-auto -rotate-3 drop-shadow-[0_3px_4px_rgb(60_40_20/0.14)]"
          />
          <div className="flex items-center gap-2">
            <span className="text-[13px] font-bold text-mut">{me.data?.nickname}</span>
            <button
              onClick={() => setTourOpen(true)}
              aria-label="튜토리얼 다시 보기"
              title="튜토리얼 다시 보기"
              className="grid h-11 w-11 cursor-pointer place-items-center rounded-xl hover:bg-bg"
            >
              <Mascot pose="wave" size={40} />
            </button>
          </div>
        </div>
      </header>

      {tourOpen && <TutorialTour setTab={setTab} onClose={() => setTourOpen(false)} />}

      {me.data?.cleared && (
        <div className="flex items-center gap-2.5 bg-[linear-gradient(90deg,var(--color-accent)_0%,var(--color-gold)_100%)] px-[18px] py-2 text-[13px] font-extrabold text-white">
          <Mascot pose="cheer" size={30} />
          <span>집에 도달했어요! 거래를 계속 이어갈 수 있어요.</span>
        </div>
      )}

      <div className="px-4 pt-10 pb-[110px]" data-tour="content">
        {tab === "feed" && <FeedTab />}
        {tab === "chat" && <ChatsTab />}
        {tab === "me" && (me.isPending ? <ul aria-label="내 물건 불러오는 중"><SkeletonList /></ul> : me.isError ? <ErrorState onRetry={() => void me.refetch()} /> : me.data && <MeTab me={me.data} />)}
        {tab === "rank" && <RankTab />}
      </div>

      <nav aria-label="주 메뉴" className="fixed bottom-0 left-1/2 z-10 flex w-full max-w-[520px] -translate-x-1/2 gap-1 border-t-2 border-line bg-surface px-3 pt-2 pb-[calc(8px+env(safe-area-inset-bottom))]">
        {TABS.map(({ id, icon, label }) => (
          <button
            key={id}
            data-tour={`tab-${id}`}
            aria-current={tab === id ? "page" : undefined}
            onClick={() => setTab(id)}
            className={`flex flex-1 cursor-pointer flex-col items-center gap-0.5 rounded-2xl border-2 py-1.5 text-[11px] font-extrabold transition-all duration-100 ${
              tab === id
                ? "border-accent-line bg-accent-soft text-accent"
                : "border-transparent text-mut hover:bg-bg"
            }`}
          >
            <img
              src={icon}
              alt=""
              className={`h-[22px] w-[22px] object-contain ${tab === id ? "" : "grayscale opacity-70"}`}
            />
            {label}
          </button>
        ))}
      </nav>
    </section>
  );
}

function FeedTab() {
  const feed = useFeed();
  const navigate = useNavigate();
  const toast = useToast();

  const open = useMutation({
    mutationFn: (listingId: number) =>
      api<NegotiationOpened>("/api/negotiations", {
        method: "POST",
        body: JSON.stringify({ listingId }),
      }),
    onSuccess: (opened) => navigate(`/negotiations/${opened.negotiationId}`),
    onError: (e) => toast(e.message),
  });

  if (feed.isPending) {
    return (
      <ul className="flex flex-col gap-3">
        <SkeletonList />
      </ul>
    );
  }
  if (feed.isError) return <ErrorState onRetry={() => void feed.refetch()} />;
  if (!feed.data?.length) {
    return (
      <EmptyState mascot="think">
        아직 매물이 없어요. 잠시 후 다시 확인해 주세요.
      </EmptyState>
    );
  }
  return (
    <ul className="flex flex-col gap-3">
      {feed.data.map((entry) => (
        <ItemCard
          key={entry.listingId}
          itemName={entry.itemName}
          description={entry.description}
          onClick={() => open.mutate(entry.listingId)}
          disabled={open.isPending}
          meta={
            <>
              <span className="flex w-full min-w-0 items-center gap-2">
                <span className="min-w-0 truncate" title={entry.sellerNickname}>{entry.sellerNickname}</span>
                {responseTime(entry.sellerAvgResponseSeconds) && <span className="shrink-0 whitespace-nowrap text-success-deep">
                  {responseTime(entry.sellerAvgResponseSeconds)}
                </span>}
              </span>
              <span>{timeAgo(entry.listedAt)}</span>
              <Dot />
              <span className={isUrgent(entry.expiresAt) ? "font-extrabold text-danger" : ""}>{timeLeft(entry.expiresAt)}</span>
            </>
          }
        />
      ))}
    </ul>
  );
}

function ChatsTab() {
  const chats = useChats();
  const navigate = useNavigate();

  if (chats.isPending) {
    return (
      <ul className="flex flex-col gap-3">
        <SkeletonList />
      </ul>
    );
  }
  if (chats.isError) return <ErrorState onRetry={() => void chats.refetch()} />;
  if (!chats.data?.length) {
    return (
      <EmptyState mascot="guide">
        피드에서 물건을 고르면 채팅을 시작할 수 있어요.
      </EmptyState>
    );
  }
  return (
    <ul className="flex flex-col gap-3">
      {chats.data.map((chat) => (
        <li
          key={chat.negotiationId}
        >
        <button type="button"
          onClick={() => navigate(`/negotiations/${chat.negotiationId}`)}
          className="flex w-full cursor-pointer items-start gap-3.5 text-left rounded-card border-2 border-line bg-surface p-3.5 shadow-[0_2px_0_var(--color-line)] transition-all duration-100 select-none hover:border-accent-line hover:shadow-[0_2px_0_var(--color-accent-line)] active:translate-y-[2px] active:shadow-none"
        >
          <Thumb emoji={itemEmoji(chat.itemName)} />
          <div className="min-w-0 flex-1">
            <div className="mb-0.5 flex items-baseline gap-2">
              <span className="truncate text-[15px] font-extrabold tracking-[-.2px]">
                {chat.itemName}
              </span>
              <span className="shrink-0 text-[12px] font-bold text-mut">{chat.personaNickname}</span>
            </div>
            <div className="line-clamp-1 text-[13px] font-medium text-ink-2">
              {chat.lastSender === "PLAYER" && <span className="text-mut">나: </span>}
              {chat.lastMessage}
            </div>
            <div className="mt-1 flex items-center gap-1.5 text-xs font-semibold text-mut">
              <span>{timeAgo(chat.lastAt)}</span>
              <Dot />
              {chat.awaitingPersona ? (
                <span className="text-success-deep">답장 기다리는 중</span>
              ) : (
                <span className="font-extrabold text-accent">새 답장 ●</span>
              )}
            </div>
          </div>
        </button>
        </li>
      ))}
    </ul>
  );
}

function MeTab({ me }: { me: Me }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const promote = useMutation({
    mutationFn: () =>
      api("/api/account/promote", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      }),
    onSuccess: () => {
      toast("계정을 만들었어요. 다른 기기에서도 이어 할 수 있어요.");
      queryClient.invalidateQueries({ queryKey: ["me"] });
    },
    onError: (e) => toast(e.message),
  });

  return (
    <>

      <div className="mb-4 rounded-card border-2 border-line bg-surface p-4 shadow-[0_2px_0_var(--color-line)]">
        <div className="text-lg font-black tracking-[-.3px]">
          {me.nickname}
          {me.cleared && (
            <span className="ml-2 rounded-full border-2 border-gold bg-gold-soft px-2.5 py-0.5 align-[3px] text-xs font-extrabold text-accent-deep">
              🏠 클리어
            </span>
          )}
        </div>
        <div className="mt-1 text-[13px] font-bold text-mut">
          이번 시즌 거래 <b className="text-accent">{me.tradesCount}회</b>
          {me.clearRank ? (
            <>
              {" "}
              · 클리어 <b className="text-accent">{me.clearRank}호</b>
            </>
          ) : null}
        </div>
      </div>

      <ul className="flex flex-col gap-3">
        {me.holdings.map((h) => (
          <ItemCard
            key={h.holdingId}
            itemName={h.itemName}
            description={h.description}
            meta={<span>{timeAgo(h.acquiredAt)} 획득</span>}
          />
        ))}
      </ul>

      {!me.promoted && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            promote.mutate();
          }}
          className="mt-5 rounded-card border-2 border-line bg-surface p-4 shadow-[0_2px_0_var(--color-line)]"
        >
          <h3 className="mb-1 text-[15px] font-extrabold">계정 만들기</h3>
          <p className="mb-3 text-[13px] font-medium text-ink-2">
            계정을 만들면 다른 기기에서도 내 물건과 기록을 이어갈 수 있어요.
          </p>
          <div className="flex flex-col gap-2">
            <input
              type="email"
              aria-label="이메일"
              autoComplete="email"
              required
              placeholder="이메일"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputCls}
            />
            <input
              type="password"
              aria-label="비밀번호"
              autoComplete="new-password"
              minLength={8}
              required
              placeholder="비밀번호 (8자 이상)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputCls}
            />
            <button type="submit" disabled={promote.isPending} className={btnPrimary}>
              {promote.isPending ? "만드는 중…" : "계정 만들기"}
            </button>
          </div>
        </form>
      )}
    </>
  );
}

const BOARDS: Array<{ id: Board; label: string }> = [
  { id: "VALUE", label: "보유 가치" },
  { id: "CLEAR_ORDER", label: "클리어 순서" },
  { id: "CLEAR_TIME", label: "클리어 시간" },
  { id: "CLEAR_TRADES", label: "최소 거래" },
];

const MEDALS: Record<number, string> = { 1: medalGold, 2: medalSilver, 3: medalBronze };

const TOP_STYLES: Record<number, string> = {
  1: "border-gold bg-gold-soft shadow-[0_2px_0_var(--color-gold)]",
  2: "border-[#c9ced6] bg-[#f4f6f8] shadow-[0_2px_0_#c9ced6]",
  3: "border-[#e8b98a] bg-[#fdf3e7] shadow-[0_2px_0_#e8b98a]",
};

function RankTab() {
  const [board, setBoard] = useState<Board>("VALUE");
  const leaderboard = useLeaderboard(board);
  const entries = leaderboard.data?.entries ?? [];

  return (
    <>
      <div role="group" aria-label="랭킹 기준" className="mb-4 grid grid-cols-2 gap-2">
        {BOARDS.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setBoard(id)}
            aria-pressed={board === id}
            className={`min-h-11 min-w-0 cursor-pointer rounded-xl border-2 px-3 py-2 text-[13px] font-extrabold transition-colors duration-100 ${
              board === id
                ? "border-info bg-info text-white shadow-[0_2px_0_var(--color-info-deep)]"
                : "border-line bg-surface text-mut hover:bg-bg"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {leaderboard.isPending ? <ol aria-label="랭킹 불러오는 중" className="flex flex-col gap-2"><SkeletonList count={3} /></ol> : leaderboard.isError ? <ErrorState onRetry={() => void leaderboard.refetch()} /> : entries.length === 0 ? (
        <RankEmptyState>
          아직 등록된 기록이 없어요.
        </RankEmptyState>
      ) : (
        <ol className="flex flex-col gap-2">
          {entries.map((entry) => (
            <li
              key={entry.rank}
              className={`flex items-center gap-3 rounded-card border-2 bg-surface px-4 py-3 ${
                TOP_STYLES[entry.rank] ?? "border-line shadow-[0_2px_0_var(--color-line)]"
              }`}
            >
              <span className="flex min-w-8 justify-center font-black tabular-nums text-mut">
                {MEDALS[entry.rank] ? (
                  <img src={MEDALS[entry.rank]} alt={`${entry.rank}위`} className="h-8 w-8 object-contain" />
                ) : (
                  entry.rank
                )}
              </span>
              <span className="font-extrabold tracking-[-.2px]">
                {entry.nickname}
                {entry.cleared && " 🏠"}
              </span>
              <span className="ml-auto text-[13px] font-bold text-mut">
                {entry.tradesCount}회 거래
              </span>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
