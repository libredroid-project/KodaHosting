/** Wire these to GitHub Releases, shop, Discord, docs. */
export const LINKS = {
  discord: "#",
  /** Footer / nav “Forum” — wire to your forum URL. */
  forum: "#",
  youtube: "#",
  docs: "#",
  github: "#",
  /** Mao Miao — Fabric mod (free) */
  downloadMiao: "#",
  /** Legacy — prefer /checkout for Mao External */
  purchaseExternal: "/checkout",
  changelog: "#",
} as const;

export const STATS = {
  modules: "120+",
  commands: "40+",
  lines: "100k+",
} as const;

/** Mao Miao — always free on the pricing card. */
export const PRICING = {
  miaoTag: "Free",
} as const;

export type BillingPlan = "monthly" | "lifetime";

/**
 * Mao External — monthly vs lifetime.
 * Paste Stripe Payment Links or Checkout session URLs from your Stripe Dashboard.
 * After payment, send customers to your site with the Checkout Session id, e.g.
 * `https://your-domain/complete-setup?session_id={CHECKOUT_SESSION_ID}`.
 * Add Payment Link metadata `plan` = `external_monthly` or `external_lifetime` so subscriptions match the tier.
 * @see https://docs.stripe.com/payment-links
 */
export const EXTERNAL_PRICES: Record<
  BillingPlan,
  { tag: string; sub: string; blurb: string; stripeCheckoutUrl: string }
> = {
  monthly: {
    tag: "$6.99",
    sub: "per month",
    blurb: "Cancel anytime · all premium modules",
    stripeCheckoutUrl: "https://buy.stripe.com/REPLACE_MONTHLY",
  },
  lifetime: {
    tag: "$29.99",
    sub: "lifetime",
    blurb: "One payment · updates included",
    stripeCheckoutUrl: "https://buy.stripe.com/REPLACE_LIFETIME",
  },
};

export function isPlaceholderStripeUrl(url: string): boolean {
  return url.includes("REPLACE_") || url === "#" || url.trim() === "";
}

/**
 * Optional full-viewport hero image (place file in `public/`, e.g. `hero-bg.jpg`).
 * Blurred behind copy — leave null for abstract cosmos only.
 */
export const HERO_BG_IMAGE: string | null = null;

/**
 * Lain-style silhouette for the hero “ghost” layer (place PNG in `public/`, e.g. `mao-lain-ghost.png`).
 * Silhouette / high-contrast art reads best behind the title.
 */
export const HERO_LAIN_GHOST: string | null = "/mao-lain-ghost.png";
