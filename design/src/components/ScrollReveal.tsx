import { type ReactNode, useEffect, useRef, useState } from "react";

type ScrollRevealProps = {
  className?: string;
  children: ReactNode;
  /** Stagger index for grouped reveals (multiplied by 70ms). */
  stagger?: number;
};

export function ScrollReveal({ className = "", children, stagger = 0 }: ScrollRevealProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const reduced =
      typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced) {
      setVisible(true);
      return;
    }

    const io = new IntersectionObserver(
      ([e]) => {
        if (e.isIntersecting) {
          setVisible(true);
          io.disconnect();
        }
      },
      { threshold: 0.08, rootMargin: "0px 0px -6% 0px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <div
      ref={ref}
      className={`mao-reveal${visible ? " mao-reveal--visible" : ""}${className ? ` ${className}` : ""}`}
      style={{ transitionDelay: `${stagger * 0.07}s` }}
    >
      {children}
    </div>
  );
}
