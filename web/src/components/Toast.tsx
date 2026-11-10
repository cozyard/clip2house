import { createContext, useCallback, useContext, useRef, useState } from "react";
import type { ReactNode } from "react";

const ToastContext = createContext<(message: string) => void>(() => {});

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);

  const show = useCallback((next: string) => {
    setMessage(next);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setMessage(null), 3500);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      {message && (
        <div
          role="status"
          className="fixed bottom-24 left-1/2 z-30 max-w-[88%] -translate-x-1/2 animate-toast-in rounded-2xl bg-[rgb(44_44_44/.96)] px-5 py-3 text-[13px] font-bold text-white shadow-[0_3px_0_rgb(0_0_0/.35)]"
        >
          {message}
        </div>
      )}
    </ToastContext.Provider>
  );
}
