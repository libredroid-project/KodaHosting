import { WiredGridPulses } from "./WiredGridPulses";

/** Site-wide void: powerline photo, void grit, shade, sparse signal — not a Tron grid. */
export function WiredBackdrop() {
  return (
    <div className="mao-wired-backdrop" aria-hidden="true">
      <div className="mao-wired-backdrop__photo-powerlines" />
      <div className="mao-wired-field" />
      <WiredGridPulses />
      <div className="mao-wired-backdrop__edge" />
      <div className="mao-wired-backdrop__noise" />
      <div className="mao-wired-backdrop__roll" />
    </div>
  );
}
