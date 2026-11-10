export interface GuestCreated {
  token: string;
  nickname: string;
}

export interface LoginResult {
  token: string;
}

export interface FeedEntry {
  listingId: number;
  itemName: string;
  description: string;
  sellerNickname: string;
  sellerAvgResponseSeconds: number | null;
  listedAt: string;
  expiresAt: string;
}

export interface Holding {
  holdingId: number;
  itemName: string;
  description: string;
  acquiredAt: string;
}

export interface Me {
  nickname: string;
  cleared: boolean;
  clearRank: number | null;
  tradesCount: number;
  promoted: boolean;
  holdings: Holding[];
}

export type Board = "VALUE" | "CLEAR_ORDER" | "CLEAR_TIME" | "CLEAR_TRADES";

export interface LeaderboardEntry {
  rank: number;
  nickname: string;
  cleared: boolean;
  tradesCount: number;
}

export interface Leaderboard {
  entries: LeaderboardEntry[];
}

export interface NegotiationOpened {
  negotiationId: number;
  listingId: number;
}

export interface ChatMessage {
  id: number;
  sender: "PLAYER" | "PERSONA";
  content: string;
  createdAt: string;
  readAt: string | null;
}

export interface ChatSummary {
  negotiationId: number;
  listingId: number;
  itemName: string;
  personaNickname: string;
  lastSender: "PLAYER" | "PERSONA";
  lastMessage: string;
  lastAt: string;
  awaitingPersona: boolean;
}

export interface Offer {
  offerId: number;
  proposer: "PLAYER" | "PERSONA";
  playerItemName: string | null;
  playerHoldingVersion: number;
}

export interface NegotiationView {
  status: "ACTIVE" | string;
  statusDetail: string | null;
  listingVersion: number;
  listingItemName: string;
  sellerNickname: string;
  sellerAvgResponseSeconds: number | null;
  messages: ChatMessage[];
  offers: Offer[];
}

export interface AcceptPayload {
  offerId: number;
  expectedListingVersion: number;
  expectedHoldingVersion: number;
}

export interface TradeResult {
  givenItemName: string;
  receivedItemName: string;
  cleared: boolean;
}
