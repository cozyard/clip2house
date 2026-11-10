import { useQuery } from "@tanstack/react-query";
import { api } from "./client";
import type { Board, ChatSummary, FeedEntry, Leaderboard, Me, NegotiationView } from "./types";

export const FEED_REFRESH_MS = 10000;

export const POLL_FAST_MS = 2500;
export const POLL_SLOW_MS = 15000;

export function useFeed() {
  return useQuery({
    queryKey: ["feed"],
    queryFn: () => api<FeedEntry[]>("/api/feed"),
    refetchInterval: FEED_REFRESH_MS,
  });
}

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: () => api<Me>("/api/me"),
  });
}

export function useChats() {
  return useQuery({
    queryKey: ["chats"],
    queryFn: () => api<ChatSummary[]>("/api/negotiations"),
    refetchInterval: FEED_REFRESH_MS,
  });
}

export function useLeaderboard(board: Board) {
  return useQuery({
    queryKey: ["leaderboard", board],
    queryFn: () => api<Leaderboard>(`/api/leaderboard?board=${board}`),
  });
}

export function useNegotiation(negotiationId: number, streamOpen: boolean) {
  return useQuery({
    queryKey: ["negotiation", negotiationId],
    queryFn: () => api<NegotiationView>(`/api/negotiations/${negotiationId}`),

    refetchInterval: (query) => {
      if (query.state.data && query.state.data.status !== "ACTIVE") return false;
      return streamOpen ? POLL_SLOW_MS : POLL_FAST_MS;
    },
  });
}
