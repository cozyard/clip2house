export function timeLeft(expiresAt: string): string {
  const ms = new Date(expiresAt).getTime() - Date.now();
  if (ms <= 0) return "마감";
  if (ms < 60000) return "1분 미만 남음";
  const h = Math.floor(ms / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  return h > 0 ? `${h}시간 남음` : `${m}분 남음`;
}

export function isUrgent(expiresAt: string): boolean {
  return new Date(expiresAt).getTime() - Date.now() < 3600000;
}

export function timeAgo(createdAt: string): string {
  const s = Math.max(0, (Date.now() - new Date(createdAt).getTime()) / 1000);
  if (s < 60) return "방금 전";
  if (s < 3600) return `${Math.floor(s / 60)}분 전`;
  if (s < 86400) return `${Math.floor(s / 3600)}시간 전`;
  return `${Math.floor(s / 86400)}일 전`;
}

export function clock(createdAt: string): string {
  return new Date(createdAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

export function responseTime(seconds: number | null | undefined): string | null {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return null;
  if (seconds < 60) return "평균 1분 미만";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `평균 ${minutes}분`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `평균 ${hours}시간`;
  return `평균 ${Math.round(hours / 24)}일`;
}
