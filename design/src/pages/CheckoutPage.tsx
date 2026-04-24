import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { BillingToggle } from "../components/BillingToggle";
import { EXTERNAL_PRICES, type BillingPlan, isPlaceholderStripeUrl } from "../config";

function planFromParam(raw: string | null): BillingPlan {
  return raw === "monthly" ? "monthly" : "lifetime";
}

export function CheckoutPage() {
  const [params, setParams] = useSearchParams();
  const [plan, setPlan] = useState<BillingPlan>(() => planFromParam(params.get("plan")));

  useEffect(() => {
    setPlan(planFromParam(params.get("plan")));
  }, [params]);

  const setPlanAndSyncUrl = (next: BillingPlan) => {
    setPlan(next);
    setParams({ plan: next }, { replace: true });
  };

  const tier = EXTERNAL_PRICES[plan];
  const stripeBlocked = isPlaceholderStripeUrl(tier.stripeCheckoutUrl);

  const goToStripe = () => {
    if (stripeBlocked) return;
    window.location.assign(tier.stripeCheckoutUrl);
  };

  return (
    <main className="mao-main mao-checkout">
      <section className="mao-section mao-checkout__hero">
        <div className="mao-container mao-checkout__inner">
          <p className="mao-kicker">Checkout</p>
          <h1 className="mao-checkout__title">Mao External</h1>
          <p className="mao-lead mao-checkout__lead">
            Pick a billing period, then continue to Stripe to complete payment. Mao Miao stays free — head home and use
            the download link there when it&apos;s available.
          </p>

          <div className="mao-checkout__panel">
            <h2 className="mao-checkout__h2">Select plan</h2>
            <BillingToggle value={plan} onChange={setPlanAndSyncUrl} />

            <div className="mao-checkout__summary">
              <div className="mao-checkout__price-block">
                <span className="mao-checkout__amount">{tier.tag}</span>
                <span className="mao-checkout__period">{tier.sub}</span>
              </div>
              <p className="mao-checkout__blurb">{tier.blurb}</p>
            </div>

            <div className="mao-checkout__actions">
              <button
                type="button"
                className="mao-btn mao-btn--solid"
                onClick={goToStripe}
                disabled={stripeBlocked}
              >
                Continue to Stripe
              </button>
              <Link className="mao-btn mao-btn--outline" to="/">
                Back to site
              </Link>
            </div>

            {stripeBlocked ? (
              <p className="mao-checkout__hint">
                Set real Stripe Payment Link URLs in <code>src/config.ts</code> (<code>EXTERNAL_PRICES.monthly</code> /{" "}
                <code>lifetime.stripeCheckoutUrl</code>).
              </p>
            ) : null}
          </div>

          <p className="mao-checkout__fine">
            You will leave this site and complete checkout on Stripe. Taxes and final price are shown on Stripe before
            you pay. Configure the Payment Link confirmation redirect to{" "}
            <code>
              /complete-setup?session_id={"{"}CHECKOUT_SESSION_ID{"}"}
            </code>{" "}
            and set metadata{" "}
            <code>plan</code> to <code>external_monthly</code> or <code>external_lifetime</code> so the API can grant
            the correct term. Run the Mao API (see <code>mao/web/server</code>) with <code>STRIPE_SECRET_KEY</code> for
            session verification.
          </p>
        </div>
      </section>
    </main>
  );
}
