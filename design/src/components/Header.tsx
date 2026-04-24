import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { LINKS } from "../config";

const NAV = [
  { to: "/#plans", label: "PLANS", path: "/root/pricing" },
  { to: "/#showcase", label: "MEDIA", path: "/vault/showcase" },
  { to: "/#features", label: "SPECS", path: "/sys/modules" },
] as const;

const SOLID_PATHS = new Set([
  "/checkout",
  "/login",
  "/register",
  "/dashboard",
  "/admin",
  "/complete-setup",
]);

export function Header() {
  const [open, setOpen] = useState(false);
  const [solid, setSolid] = useState(false);
  const location = useLocation();

  useEffect(() => {
    const onScroll = () => {
      if (SOLID_PATHS.has(location.pathname)) setSolid(true);
      else setSolid(window.scrollY > 32);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [location.pathname]);

  return (
    <header className={`mao-header${solid ? " mao-header--solid" : ""}`}>
      <div className="mao-header__inner">
        <div className="mao-header__left">
          <Link className="mao-logo" to="/" onClick={() => setOpen(false)} title="INDEX">
            <span className="mao-logo__mark">M</span>
            <span className="mao-logo__text">
              <span className="mao-logo__jp" aria-hidden="true">
                接続
              </span>
              <span className="mao-logo__en">MAO CLIENT</span>
            </span>
          </Link>
          <nav id="mao-site-nav" className={`mao-nav mao-nav--primary${open ? " mao-nav--open" : ""}`}>
            <div className="mao-nav__core">
              {NAV.map((item) => (
                <Link key={item.to} to={item.to} onClick={() => setOpen(false)} className="mao-nav__row">
                  <span className="mao-nav__label">{item.label}</span>
                  <span className="mao-nav__path">{item.path}</span>
                </Link>
              ))}
            </div>
            <div className="mao-nav__drawer-extra">
              <a href={LINKS.forum} onClick={() => setOpen(false)} className="mao-nav__row">
                <span className="mao-nav__label">FORUM</span>
                <span className="mao-nav__path">/net/forum</span>
              </a>
              <Link to="/login" onClick={() => setOpen(false)} className="mao-nav__row">
                <span className="mao-nav__label">SESSION</span>
                <span className="mao-nav__path">/auth/login</span>
              </Link>
              <Link className="mao-nav__drawer-buy" to="/checkout?plan=lifetime" onClick={() => setOpen(false)}>
                ACQUIRE
              </Link>
            </div>
          </nav>
        </div>
        <div className="mao-header__end">
          <a href={LINKS.forum} className="mao-nav__row mao-nav__row--compact">
            <span className="mao-nav__label">FORUM</span>
            <span className="mao-nav__path">/net/forum</span>
          </a>
          <Link to="/login" className="mao-nav__row mao-nav__row--compact">
            <span className="mao-nav__label">SESSION</span>
            <span className="mao-nav__path">/auth/login</span>
          </Link>
          <Link className="mao-header__buy" to="/checkout?plan=lifetime">
            ACQUIRE
          </Link>
        </div>
        <button
          type="button"
          className="mao-nav-toggle"
          aria-expanded={open}
          aria-controls="mao-site-nav"
          onClick={() => setOpen((v) => !v)}
        >
          <span />
          <span />
          <span />
        </button>
      </div>
    </header>
  );
}
