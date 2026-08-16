import type { BillingPlan } from "../config";

type BillingToggleProps = {
  value: BillingPlan;
  onChange: (plan: BillingPlan) => void;
  id?: string;
};

export function BillingToggle({ value, onChange, id }: BillingToggleProps) {
  return (
    <div id={id} className="mao-billing-switch" role="tablist" aria-label="Billing period">
      <button
        type="button"
        role="tab"
        aria-selected={value === "monthly"}
        className={value === "monthly" ? "is-active" : undefined}
        onClick={() => onChange("monthly")}
      >
        Monthly
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={value === "lifetime"}
        className={value === "lifetime" ? "is-active" : undefined}
        onClick={() => onChange("lifetime")}
      >
        Lifetime
      </button>
    </div>
  );
}
