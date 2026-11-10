import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getToken } from "../api/client";

export function useNegotiationStream(negotiationId: number, active: boolean): boolean {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!active) return;
    const token = getToken();
    if (!token) return;

    const source = new EventSource(
      `/api/negotiations/${negotiationId}/events?token=${encodeURIComponent(token)}`,
    );
    source.addEventListener("update", () => {
      queryClient.invalidateQueries({ queryKey: ["negotiation", negotiationId] });
    });
    source.onopen = () => setOpen(true);
    source.onerror = () => setOpen(false);

    return () => {
      source.close();
      setOpen(false);
    };
  }, [negotiationId, active, queryClient]);

  return open;
}
