import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api, setToken } from "../api/client";
import type { GuestCreated, LoginResult } from "../api/types";
import { useToast } from "../components/Toast";
import { Mascot } from "../components/Mascot";
import { btn, btnPrimary, inputCls } from "../lib/ui";
import logo from "../assets/c2h.png";
import clipIcon from "../assets/items/clip.webp";
import penIcon from "../assets/items/pen.webp";
import laptopIcon from "../assets/items/laptop.webp";
import carIcon from "../assets/items/car.webp";
import houseIcon from "../assets/items/house.webp";

const JOURNEY = [
  { icon: clipIcon, label: "클립" },
  { icon: penIcon, label: "펜" },
  { icon: laptopIcon, label: "노트북" },
  { icon: carIcon, label: "자동차" },
  { icon: houseIcon, label: "집" },
];

export function StartScreen() {
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const startGuest = useMutation({
    mutationFn: () => api<GuestCreated>("/api/guests", { method: "POST" }),
    onSuccess: (created) => {
      toast(`${created.nickname}님, 빨간 클립을 받았어요.`);
      setToken(created.token);
    },
    onError: (e) => toast(e.message),
  });

  const login = useMutation({
    mutationFn: () =>
      api<LoginResult>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      }),
    onSuccess: (result) => setToken(result.token),
    onError: (e) => toast(e.message),
  });

  return (
    <section className="bg-[radial-gradient(140%_90%_at_50%_-10%,#ffe9d6_0%,#fff7ef_48%,var(--color-bg)_100%)]">
      <div className="flex min-h-dvh flex-col items-center justify-center gap-5 px-7 py-10 text-center">
        <h1 className="flex flex-col items-center gap-3">
          <img
            src={logo}
            alt="클립 투 하우스"
            className="w-full max-w-[260px] drop-shadow-[0_10px_18px_rgb(255_111_15/.18)]"
          />
        </h1>
        <p className="text-[15px] leading-[1.7] font-semibold text-ink-2">
          빨간 클립을 물물교환해
          <br />
          <b className="text-accent">집까지</b> 도전해 보세요.
        </p>

        <div className="relative mt-1 flex items-end gap-3">
          <span className="absolute inset-x-0 -bottom-1 mx-auto h-20 w-60 rounded-full bg-[radial-gradient(closest-side,#ffdfc2,transparent)]" />
          <div className="relative origin-bottom motion-reduce:animate-none">
            <Mascot pose="wave" size={116} />
          </div>
          <div className="relative mb-16 animate-pop rounded-2xl rounded-bl-md border-2 border-line bg-surface px-4 py-3 text-left text-[13px] leading-relaxed font-bold text-ink-2 shadow-[0_3px_0_var(--color-line)]">
            첫 거래를 도와드릴게요.
          </div>
        </div>

        <div className="w-full max-w-[340px] rounded-card border-2 border-line bg-surface p-4 shadow-[0_3px_0_var(--color-line)]">
          <div className="mb-3 flex items-center justify-between">
            <span className="rounded-full bg-accent-soft px-2.5 py-1 text-[11px] font-extrabold text-accent">
              클립에서 집까지
            </span>

          </div>
          <div className="flex items-center">
            {JOURNEY.map((step, i) => (
              <div key={step.label} className="contents">
                {i > 0 && (
                  <div
                    className={`h-1 flex-1 rounded-full ${i === 1 ? "bg-[linear-gradient(90deg,var(--color-accent-line),var(--color-line))]" : "bg-line"}`}
                  />
                )}
                <div
                  className={`flex h-11 w-11 items-center justify-center rounded-full border-2 ${
                    i === 0
                      ? "border-accent bg-accent-soft shadow-[0_2px_0_var(--color-accent-line)]"
                      : i === JOURNEY.length - 1
                        ? "border-gold bg-gold-soft shadow-[0_2px_0_var(--color-gold)]"
                        : "border-line bg-bg"
                  }`}
                >
                  <img src={step.icon} alt={step.label} className="h-7 w-7 object-contain" />
                </div>
              </div>
            ))}
          </div>
          <div className="mt-2 flex justify-between text-[11px] font-bold text-mut">
            <span className="text-accent">시작</span>
            <span className="text-accent-deep">클리어 🎉</span>
          </div>
        </div>

        <div className="w-full max-w-[340px]">
          <button
            onClick={() => startGuest.mutate()}
            disabled={startGuest.isPending}
            className={`${btnPrimary} w-full px-8 py-4 text-base tracking-[-.2px] shadow-[0_4px_0_var(--color-accent-deep)] active:translate-y-[4px]`}
          >
            <img src={clipIcon} alt="" className="mr-1.5 inline-block h-5 w-5 align-[-4px]" />
            {startGuest.isPending ? "시작하는 중…" : "클립 하나로 시작하기"}
          </button>
          <p className="mt-2 text-[12px] font-semibold text-mut">
            가입 없이 시작하고 나중에 계정을 만들 수 있어요.
          </p>
        </div>

        <details className="w-full max-w-[340px] text-left">
          <summary className="cursor-pointer text-center text-[13px] font-bold text-mut underline decoration-dotted underline-offset-4 hover:text-ink-2">
            계정이 있어요
          </summary>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              login.mutate();
            }}
            className="mt-3 flex flex-col gap-2"
          >
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
              autoComplete="current-password"
              required
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputCls}
            />
            <button type="submit" disabled={login.isPending} className={btn}>
              {login.isPending ? "로그인 중…" : "로그인"}
            </button>
          </form>
        </details>
      </div>
    </section>
  );
}
