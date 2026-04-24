type AmbientGlowsProps = {
  /** When true, glows are confined to the hero (absolute), not the full page. */
  scoped?: boolean;
};

export function AmbientGlows({ scoped = false }: AmbientGlowsProps) {
  return (
    <div className={`mao-ambient${scoped ? " mao-ambient--scoped" : ""}`} aria-hidden="true">
      <div className="mao-ambient__orb" />
      <div className="mao-ambient__orb mao-ambient__orb--2" />
      <div className="mao-ambient__orb mao-ambient__orb--3" />
    </div>
  );
}
