import { useSyncExternalStore } from "react";
import { Navigate, Route, Routes } from "react-router";
import { getToken, subscribeToken } from "./api/client";
import { ChatScreen } from "./screens/ChatScreen";
import { MainScreen } from "./screens/MainScreen";
import { StartScreen } from "./screens/StartScreen";

export default function App() {
  const token = useSyncExternalStore(subscribeToken, getToken);
  if (!token) return <StartScreen />;

  return (
    <Routes>
      <Route path="/" element={<MainScreen />} />
      <Route path="/negotiations/:negotiationId" element={<ChatScreen />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
