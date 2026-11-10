const TOKEN_KEY = "c2h_token";
const listeners = new Set<() => void>();

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  for (const fn of listeners) fn();
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
  for (const fn of listeners) fn();
}

export function subscribeToken(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {

    clearToken();
    throw new ApiError("세션이 만료되었습니다. 다시 시작해 주세요.", 401, "UNAUTHORIZED");
  }
  const body = response.status === 204 ? {} : await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(body.message || "요청에 실패했습니다.", response.status, body.code);
  }
  return body as T;
}
