import { useState } from "react";
import { Link } from "react-router-dom";
import { EXTERNAL_PRICES, LINKS, PRICING, type BillingPlan } from "../config";
import { DraggableTerminalCard } from "./DraggableTerminalCard";
import { ScrollReveal } from "./ScrollReveal";

function PlanSpectrum() {
  const ticks = [-10, -5, 0, 5, 10, 15] as const;
  return (
    <div className="mao-plan-spectrum" aria-hidden="true">
      <div className="mao-plan-spectrum__inner">
        <div className="mao-plan-spectrum__track" />
        <div className="mao-plan-spectrum__glow" />
        <div className="mao-plan-spectrum__ticks">
          {ticks.map((n) => (
            <span key={n} className="mao-plan-spectrum__tick">
              <i />
              <b>{n}</b>
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function PhantomRail({ jp, en }: { jp: string; en: string }) {
  return (
    <div className="mao-phantom-rail" aria-hidden="true">
      <span className="mao-phantom-rail__jp">{jp}</span>
      <span className="mao-phantom-rail__en">{en}</span>
    </div>
  );
}

export function LandingSections() {
  const [billing, setBilling] = useState<BillingPlan>("lifetime");
  const [externalTagTapped, setExternalTagTapped] = useState(false);
  const external = EXTERNAL_PRICES[billing];

  const toggleExternalBilling = () => {
    setBilling((b) => (b === "monthly" ? "lifetime" : "monthly"));
    setExternalTagTapped(true);
  };

  return (
    <div className="mao-phantom-page">
      <section className="mao-section mao-phantom-section mao-phantom-section--asym-1" id="plans">
        <PhantomRail jp="計画" en="PLANS" />
        <div className="mao-phantom-container">
          <ScrollReveal>
            <header className="mao-phantom-block">
              <h2 className="mao-phantom-display">
                <span className="mao-display-zh" aria-hidden="true">
                  プラン
                </span>
                Plans
              </h2>
              <p className="mao-phantom-sub">Choose the plan that fits you the most.</p>
            </header>
          </ScrollReveal>
        </div>
        <ScrollReveal className="mao-reveal--block">
          <div className="mao-plans-shell">
            <div className="mao-plans-shell__glow" aria-hidden="true" />
            <div className="mao-plans-shell__inner">
              <span className="mao-plans-shell__bar">PRICING · NODE</span>
              <PlanSpectrum />
              <div className="mao-pricing-grid mao-stagger">
                <DraggableTerminalCard
                  className="mao-price-card"
                  refId="EXT-PLN-MIAO"
                  hardwareLabel="MAO MIAO"
                  a11yTitle="Mao Miao"
                  hud={{
                    tr: "TIER: PUBLIC",
                    bl: "[NODE: FABRIC]",
                    br: "REL: 1.21.8",
                  }}
                >
                  <span className="mao-card-tag">MIAO · FREE</span>
                  <span className="mao-price-tag" aria-label={`Price ${PRICING.miaoTag}`}>
                    {PRICING.miaoTag}
                  </span>
                  <div className="mao-price">Free · Fabric mod · 1.21.8</div>
                  <ul className="mao-price-list mao-price-list--checks">
                    <li>Standard cheat client on Fabric</li>
                    <li>Community builds, full module surface, zero paywall.</li>
                    <li>Perfect for the lesser needs.</li>
                  </ul>
                  <a className="mao-btn mao-btn--outline" href={LINKS.downloadMiao} style={{ alignSelf: "flex-start" }}>
                    Coming soon
                  </a>
                </DraggableTerminalCard>
                <DraggableTerminalCard
                  className="mao-price-card mao-price-card--highlight"
                  refId="EXT-PLN-PAID"
                  hardwareLabel="MAO EXTERNAL"
                  a11yTitle="Mao External"
                  hud={{
                    tr: "TIER: SECURE",
                    bl: "[LIC: HW-BIND]",
                    br: "REL: 1.21.x",
                  }}
                >
                  <span className="mao-card-tag">EXTERNAL · PAID</span>
                  <button
                    type="button"
                    className={`mao-price-tag mao-price-tag--accent mao-price-tag--toggle${externalTagTapped ? "" : " mao-price-tag--nudge"}`}
                    role="switch"
                    aria-checked={billing === "lifetime"}
                    aria-label={`${billing === "monthly" ? "Monthly" : "Lifetime"} — ${external.tag} ${external.sub}. Click tag to switch billing.`}
                    onClick={toggleExternalBilling}
                  >
                    <span className="mao-price-tag__amount">{external.tag}</span>
                    <span className="mao-price-tag__sub">{external.sub}</span>
                  </button>
                  <div className="mao-price">Paid · injection · 1.21.1 - 1.21.10</div>
                  <ul className="mao-price-list mao-price-list--checks">
                    <li>Premium injection Version </li>
                    <li>Hardware-bound licensing, priority updates, premium-only modules.</li>
                    <li>The Safest Client out there</li>
                  </ul>
                  <Link className="mao-btn mao-btn--solid" to={`/checkout?plan=${billing}`} style={{ alignSelf: "flex-start" }}>
                    Buy now
                  </Link>
                </DraggableTerminalCard>
              </div>
            </div>
          </div>
        </ScrollReveal>
      </section>

      <section className="mao-section mao-phantom-section mao-phantom-section--asym-2" id="showcase">
        <PhantomRail jp="展示" en="MEDIA" />
        <div className="mao-phantom-container">
          <ScrollReveal>
            <header className="mao-phantom-block">
              <h2 className="mao-phantom-display">
                <span className="mao-display-zh" aria-hidden="true">
                  展示
                </span>
                Showcase
              </h2>
              <p className="mao-phantom-sub">Videos, Pictures and Showcases about the Client.</p>
            </header>
          </ScrollReveal>
          <ScrollReveal className="mao-reveal--block" stagger={1}>
            <div className="mao-showcase-hero mao-showcase-hero--solo mao-xerox-hover">
              <span className="mao-showcase-hero__label">UI Showcase</span>
            </div>
          </ScrollReveal>
        </div>
      </section>

      <section className="mao-section mao-phantom-section mao-phantom-section--asym-3" id="features">
        <PhantomRail jp="機能" en="SPECS" />
        <div className="mao-phantom-container mao-phantom-container--wide">
          <ScrollReveal>
            <header className="mao-phantom-block">
              <h2 className="mao-phantom-display">
                <span className="mao-display-zh" aria-hidden="true">
                  機能
                </span>
                Features
              </h2>
              <p className="mao-phantom-sub">
                Mao delivers the best Features possible, better then any Ghost Client.
              </p>
            </header>
          </ScrollReveal>
          <ScrollReveal className="mao-reveal--block" stagger={1}>
            <div className="mao-feature-grid mao-stagger">
              <DraggableTerminalCard
                className="mao-feature-card"
                refId="FEAT-INJ-01"
                hardwareLabel="NULLFILE INJECT"
                a11yTitle="Injects without any file"
                hud={{
                  tr: "PATH: ∅",
                  bl: "[SIG: OK]",
                  br: "MODE: GHOST",
                }}
              >
                <span className="mao-card-tag">MODULE · INJECT</span>
                <span className="mao-feature-glyph" aria-hidden="true">
                  [+]
                </span>
                <p>You dont need to download anything, it just works by magic :)</p>
              </DraggableTerminalCard>
              <DraggableTerminalCard
                className="mao-feature-card"
                refId="FEAT-UI-02"
                hardwareLabel="DEEP SURFACE"
                a11yTitle="Feature Rich"
                hud={{
                  tr: "UI: RICH",
                  bl: "[ENT: MAX]",
                  br: "DEPTH: HI",
                }}
              >
                <span className="mao-card-tag">MODULE · SURFACE</span>
                <span className="mao-feature-glyph" aria-hidden="true">
                  [#]
                </span>
                <p>Lots of features have never been done by any other client.</p>
              </DraggableTerminalCard>
            </div>
          </ScrollReveal>
        </div>
      </section>
    </div>
  );
}
