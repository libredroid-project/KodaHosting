import type { PointerEvent as ReactPointerEvent, ReactNode } from "react";
import { useCallback, useRef, useState } from "react";

type Trail = { id: number; x: number; y: number; o: number };

export type TerminalHudCorners = Partial<{
  tl: string;
  tr: string;
  bl: string;
  br: string;
}>;

type Props = {
  children: ReactNode;
  className?: string;
  /** Shown as `REF_ID:` footer (system id). */
  refId?: string;
  /** Brutalist inverted strip on the top border (hardware label). */
  hardwareLabel?: string;
  /** Screen-reader title when using hardwareLabel (defaults to hardwareLabel). */
  a11yTitle?: string;
  /** Corner metadata; unspecified corners get generic “noise” strings. */
  hud?: TerminalHudCorners;
};

const DEFAULT_HUD = {
  tl: "STATUS: CONNECTED",
  tr: "ENCRYPTION: 128-BIT",
  bl: "[LOG_ID: 0x88AF]",
  br: "SCHEME: DARK",
} as const;

/**
 * Terminal “data card”: 2px shell, drag anywhere except controls; afterimages
 * trail the last positions (CRT persistence / slow bus).
 */
export function DraggableTerminalCard({
  children,
  className = "",
  refId,
  hardwareLabel,
  a11yTitle,
  hud,
}: Props) {
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const [trail, setTrail] = useState<Trail[]>([]);
  const drag = useRef<{ ox: number; oy: number; px: number; py: number } | null>(null);
  const trailId = useRef(0);
  const lastTrail = useRef(0);
  const posRef = useRef({ x: 0, y: 0 });

  const corners = { ...DEFAULT_HUD, ...hud };

  const pushTrail = useCallback((x: number, y: number) => {
    const id = ++trailId.current;
    setTrail((t) => [...t.slice(-6), { id, x, y, o: 0.38 }]);
    window.setTimeout(() => setTrail((t) => t.filter((p) => p.id !== id)), 520);
  }, []);

  const onPointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    const el = e.target as HTMLElement;
    if (el.closest("a, button, input, select, textarea, [role='switch']")) return;
    (e.currentTarget as HTMLDivElement).setPointerCapture(e.pointerId);
    drag.current = { ox: offset.x, oy: offset.y, px: e.clientX, py: e.clientY };
    posRef.current = { x: offset.x, y: offset.y };
    setDragging(true);
    lastTrail.current = performance.now();
    e.preventDefault();
    window.getSelection()?.removeAllRanges();
  };

  const onPointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!drag.current) return;
    e.preventDefault();
    const d = drag.current;
    const nx = d.ox + (e.clientX - d.px);
    const ny = d.oy + (e.clientY - d.py);
    const now = performance.now();
    const prev = posRef.current;
    if (now - lastTrail.current > 42 && (nx !== prev.x || ny !== prev.y)) {
      lastTrail.current = now;
      pushTrail(prev.x, prev.y);
    }
    posRef.current = { x: nx, y: ny };
    setOffset({ x: nx, y: ny });
  };

  const end = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (drag.current) {
      try {
        (e.currentTarget as HTMLDivElement).releasePointerCapture(e.pointerId);
      } catch {
        /* */
      }
    }
    drag.current = null;
    setDragging(false);
  };

  const cardClass = [
    "mao-terminal-card",
    hardwareLabel ? "mao-terminal-card--labeled" : "",
    className.trim(),
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div
      className={`mao-terminal-shell${dragging ? " mao-terminal-shell--drag" : ""}`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={end}
      onPointerCancel={end}
    >
      {trail.map((p, i) => (
        <div
          key={p.id}
          className="mao-terminal-shell__ghost"
          style={{
            transform: `translate(${p.x}px, ${p.y}px)`,
            opacity: p.o * (0.55 + i * 0.06),
          }}
          aria-hidden="true"
        />
      ))}
      <article
        className={cardClass}
        style={{ transform: `translate(${offset.x}px, ${offset.y}px)` }}
        {...(refId ? { "data-ref-id": refId } : {})}
      >
        {hardwareLabel ? (
          <>
            <div className="mao-hardware-label">
              <span>{hardwareLabel}</span>
            </div>
            <h3 className="mao-sr-only">{a11yTitle ?? hardwareLabel}</h3>
          </>
        ) : null}
        <div className="mao-terminal-hud" aria-hidden="true">
          <span className="mao-terminal-hud__corner mao-terminal-hud__corner--tl">{corners.tl}</span>
          <span className="mao-terminal-hud__corner mao-terminal-hud__corner--tr">{corners.tr}</span>
          <span className="mao-terminal-hud__corner mao-terminal-hud__corner--bl">{corners.bl}</span>
          <span className="mao-terminal-hud__corner mao-terminal-hud__corner--br">{corners.br}</span>
        </div>
        {children}
      </article>
    </div>
  );
}
