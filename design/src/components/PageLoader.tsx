import { useEffect, useLayoutEffect, useRef, useState } from "react";

/**
 * CRT “tube” boot (~2s): black field + noise/grid, flickering scan line, vertical open,
 * heavy scan/static that eases off, then overlay dismisses.
 */
const TUBE_MS = 420;
const OPEN_MS = 1180;
const SETTLE_MS = 400;
const LOAD_TOTAL_MS = TUBE_MS + OPEN_MS + SETTLE_MS;
const EXIT_MS = 720;

type Phase = "tube" | "open" | "settle" | "exit" | "gone";

export function PageLoader() {
  const [phase, setPhase] = useState<Phase>("tube");
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const clearTimers = () => {
    timers.current.forEach(clearTimeout);
    timers.current = [];
  };

  useLayoutEffect(() => {
    clearTimers();
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) {
      setPhase("exit");
      return clearTimers;
    }

    setPhase("tube");
    timers.current.push(
      setTimeout(() => {
        setPhase("open");
      }, TUBE_MS),
    );
    timers.current.push(
      setTimeout(() => {
        setPhase("settle");
      }, TUBE_MS + OPEN_MS),
    );
    timers.current.push(
      setTimeout(() => {
        setPhase("exit");
      }, LOAD_TOTAL_MS),
    );
    return clearTimers;
  }, []);

  useEffect(() => {
    if (phase !== "exit") return;
    const t = setTimeout(() => setPhase("gone"), EXIT_MS + 40);
    return () => clearTimeout(t);
  }, [phase]);

  if (phase === "gone") return null;

  return (
    <div
      className={`mao-page-loader mao-page-loader--crt${phase === "exit" ? " mao-page-loader--crt-exit" : ""}`}
      data-crt-phase={phase}
      role="presentation"
      aria-live="polite"
      aria-label="Display warming up"
    >
      <div className="mao-page-loader__crt-noise" aria-hidden="true" />
      <div className="mao-page-loader__crt-grid" aria-hidden="true" />
      <div className="mao-page-loader__crt-curtain mao-page-loader__crt-curtain--top" aria-hidden="true" />
      <div className="mao-page-loader__crt-curtain mao-page-loader__crt-curtain--bottom" aria-hidden="true" />
      <div className="mao-page-loader__crt-line" aria-hidden="true" />
      <div className="mao-page-loader__crt-static" aria-hidden="true" />
    </div>
  );
}
