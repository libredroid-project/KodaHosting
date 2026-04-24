import { Link } from "react-router-dom";
import { LINKS } from "../config";

export function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="mao-footer mao-footer--phantom">
      <div className="mao-footer__phantom-grid">
        <div className="mao-footer__phantom-brand">
          <Link className="mao-logo" to="/" onClick={() => window.scrollTo(0, 0)}>
            <span className="mao-logo__mark">M</span>
            <span>MAO CLIENT</span>
          </Link>
          <p className="mao-footer__phantom-tagline">
            The new generation of Minecraft Ghost Clients has come. It injects without any injector or file on your pc.
          </p>
        </div>
        <div className="mao-footer__phantom-col">
          <h4>Company</h4>
          <ul>
            <li>
              <a href="#">Terms of Service</a>
            </li>
            <li>
              <a href="#">Privacy Policy</a>
            </li>
            <li>
              <a href="#">Imprint</a>
            </li>
          </ul>
        </div>
        <div className="mao-footer__phantom-col">
          <h4>Product</h4>
          <ul>
            <li>
              <Link to="/#plans">Plans</Link>
            </li>
            <li>
              <Link to="/#showcase">Showcase</Link>
            </li>
            <li>
              <Link to="/#features">Features</Link>
            </li>
          </ul>
        </div>
        <div className="mao-footer__phantom-col">
          <h4>Connect</h4>
          <ul>
            <li>
              <a href={LINKS.youtube}>YouTube</a>
            </li>
            <li>
              <a href={LINKS.forum}>Forum</a>
            </li>
            <li>
              <Link to="/login">Log in</Link>
            </li>
          </ul>
        </div>
      </div>
      <p className="mao-footer__phantom-copy">Copyright {year}, Mao Client. All Rights Reserved.</p>
    </footer>
  );
}
