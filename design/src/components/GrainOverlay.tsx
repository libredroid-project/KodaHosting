/** Subtle film grain — stays below UI (z-index), no extra full-screen noise stack. */
export function GrainOverlay() {
  return (
    <>
      <div className="mao-grain mao-grain--film" aria-hidden="true" />
      <div className="mao-grain mao-grain--buzz" aria-hidden="true" />
    </>
  );
}
