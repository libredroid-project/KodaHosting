import { useCallback, useEffect, useRef, useState } from "react";

type Leak = { id: number; x: number; y: number };

/**
 * “Data leak” cursor: messy HUD + RGB rings + vertical stack bleed + click burst.
 */
export function CursorTelemetry() {
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [showHud, setShowHud] = useState(false);
  const [leaks, setLeaks] = useState<Leak[]>([]);
  const id = useRef(0);

  const move = useCallback((e: MouseEvent) => {
    setPos({ x: e.clientX, y: e.clientY });
    setShowHud(true);
  }, []);

  const down = useCallback((e: MouseEvent) => {
    const nid = ++id.current;
    setLeaks((s) => [...s.slice(-5), { id: nid, x: e.clientX, y: e.clientY }]);
    window.setTimeout(() => setLeaks((s) => s.filter((x) => x.id !== nid)), 580);
  }, []);

  useEffect(() => {
    window.addEventListener("mousemove", move, { passive: true });
    window.addEventListener("mousedown", down);
    return () => {
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mousedown", down);
    };
  }, [move, down]);

  const reduced = typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  return (
    <>
      {!reduced ? (
        <div className="mao-cursor-leak-column" aria-hidden="true">
          <span>ERR_STREAM</span>
          <span>0xDEADFACE</span>
          <span>CHK_FAIL</span>
          <span>IRQ 0x07</span>
          <span>MEM 64K</span>
          <span>STACK??</span>
        </div>
      ) : null}
      {leaks.map((L) => (
        <div key={L.id} className="mao-cursor-leak" style={{ left: L.x, top: L.y }} aria-hidden="true">
          <span className="mao-cursor-leak__distort" />
          <span className="mao-cursor-leak__ring mao-cursor-leak__ring--r" />
          <span className="mao-cursor-leak__ring mao-cursor-leak__ring--g" />
          <span className="mao-cursor-leak__ring mao-cursor-leak__ring--b" />
        </div>
      ))}
      {!reduced && showHud ? (
        <div
          className="mao-cursor-hud"
          style={{ transform: `translate(${Math.round(pos.x) + 22}px, ${Math.round(pos.y) + 24}px) rotate(-0.4deg)` }}
          aria-hidden="true"
        >
          <span className="mao-cursor-hud__line">
            X:{pos.x.toFixed(1)} Y:{pos.y.toFixed(1)}
          </span>
          <span className="mao-cursor-hud__line mao-cursor-hud__line--bleed">RAW / NO_FILTER</span>
        </div>
      ) : null}
    </>
  );
}
