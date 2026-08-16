import { useState } from "react";
import { Link } from "react-router-dom";
import { HERO_BG_IMAGE, HERO_LAIN_GHOST } from "../config";
import { AmbientGlows } from "./AmbientGlows";

export function HeroFuture() {
  const [lainMissing, setLainMissing] = useState(false);
  const showLain = Boolean(HERO_LAIN_GHOST) && !lainMissing;

  return (
    <section className="mao-hero-future" id="top" aria-label="Mao Client">
      <div className="mao-hero-future__bg">
        {HERO_BG_IMAGE ? (
          <div className="mao-hero-future__img-wrap mao-xerox-hover">
            <div
              className="mao-hero-future__img"
              style={{ backgroundImage: `url(${HERO_BG_IMAGE})` }}
              aria-hidden="true"
            />
          </div>
        ) : null}
      </div>
      <AmbientGlows scoped />
      <div className="mao-hero-future__horizon" aria-hidden="true" />
      <div className="mao-hero-future__landscape" aria-hidden="true" />
      <div className="mao-hero-future__moon" aria-hidden="true" />

      <div className="mao-hero-future__edge-meta mao-hero-future__edge-meta--bl" aria-hidden="true">
        <span>RESOLUTION: 72dpi</span>
        <span>STATUS: CONNECTED</span>
      </div>
      <div className="mao-hero-future__edge-meta mao-hero-future__edge-meta--br" aria-hidden="true">
        <span>LATENCY: 22ms</span>
        <span>BUFFER: 88%</span>
      </div>

      <div className="mao-hero-future__shell">
        <div className="mao-hero-future__content">
          <div className="mao-hero-future__focal">
            {showLain ? (
              <div className="mao-hero-future__lain" aria-hidden="true">
                <img
                  className="mao-hero-future__lain-img"
                  src={HERO_LAIN_GHOST!}
                  alt=""
                  decoding="async"
                  onError={() => setLainMissing(true)}
                />
              </div>
            ) : null}
            <p className="mao-hero-future__masthead">THE WIRED</p>
            <h1 className="mao-hero-future__wordmark">
              <span className="mao-hero-future__jp" aria-hidden="true">
                マオ
              </span>
              MAO CLIENT
            </h1>
            <p className="mao-hero-future__lead">
              The new generation of Minecraft Ghost Clients has come. It injects without any injector or file on your pc.
            </p>
            <div className="mao-hero-future__cta-row">
              <a className="mao-btn mao-btn--hero-learn" href="#features">
                Learn more
              </a>
              <Link className="mao-btn mao-btn--hero-buy" to="/checkout?plan=lifetime">
                Buy now
              </Link>
            </div>
          </div>
          <a className="mao-hero-future__video-link" href="#showcase">
            <span className="mao-hero-future__play" aria-hidden="true" />
            Watch videos
          </a>
        </div>
      </div>
    </section>
  );
}
