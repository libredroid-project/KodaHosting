import { useEffect, useRef } from "react";

const PULSE = "#62727b";

type Spark = { x: number; y: number; vx: number; vy: number; life: number };

/**
 * Sparse steel “signal scraps” drifting diagonally — no mathematical grid (Tron look removed).
 */
export function WiredGridPulses() {
  const ref = useRef<HTMLCanvasElement>(null);
  const sparks = useRef<Spark[]>([]);
  const scrollVel = useRef(0);
  let lastScrollT = 0;
  let lastScrollY = 0;
  const raf = useRef(0);

  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;

    const build = (w: number, h: number) => {
      const n = Math.min(48, Math.max(22, Math.floor((w * h) / 45000)));
      const s: Spark[] = [];
      for (let i = 0; i < n; i++) {
        s.push({
          x: Math.random() * w,
          y: Math.random() * h,
          vx: 0.35 + Math.random() * 1.1,
          vy: -0.25 - Math.random() * 0.95,
          life: Math.random() * Math.PI * 2,
        });
      }
      sparks.current = s;
    };

    const onScroll = () => {
      const y = window.scrollY;
      const now = performance.now();
      const dt = Math.max(1, now - lastScrollT);
      const dy = y - lastScrollY;
      lastScrollT = now;
      lastScrollY = y;
      scrollVel.current = scrollVel.current * 0.88 + (dy / dt) * 0.12;
    };

    let w = 0;
    let h = 0;
    let dpr = 1;

    const resize = () => {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = window.innerWidth;
      h = window.innerHeight;
      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      build(w, h);
    };

    lastScrollY = window.scrollY;
    lastScrollT = performance.now();

    const draw = () => {
      scrollVel.current *= 0.98;
      ctx.clearRect(0, 0, w, h);

      if (!reduced) {
        const boost = scrollVel.current * 0.02;
        for (const p of sparks.current) {
          p.life += 0.04;
          p.x += p.vx + boost;
          p.y += p.vy;
          if (p.x > w + 20) p.x = -20;
          if (p.y < -20) p.y = h + 20;
          if (p.x < -20) p.x = w + 20;
          if (p.y > h + 20) p.y = -20;
          const a = 0.35 + Math.sin(p.life) * 0.22;
          ctx.fillStyle = PULSE;
          ctx.globalAlpha = a;
          const flick = (Math.sin(p.life * 3.1) + 1) * 0.5;
          ctx.fillRect(Math.floor(p.x), Math.floor(p.y), 2 + flick, 1 + flick * 0.5);
        }
        ctx.globalAlpha = 1;
      }

      raf.current = requestAnimationFrame(draw);
    };

    resize();
    window.addEventListener("resize", resize);
    window.addEventListener("scroll", onScroll, { passive: true });
    raf.current = requestAnimationFrame(draw);
    return () => {
      window.removeEventListener("resize", resize);
      window.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(raf.current);
    };
  }, []);

  return <canvas ref={ref} className="mao-wired-pulses" aria-hidden="true" />;
}
