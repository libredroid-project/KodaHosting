import { useLayoutEffect } from "react";
import { useLocation } from "react-router-dom";
import { HeroFuture } from "../components/HeroFuture";
import { LandingSections } from "../components/LandingSections";

export function HomePage() {
  const location = useLocation();

  useLayoutEffect(() => {
    if (!location.hash) return;
    const id = location.hash.slice(1);
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [location.hash, location.pathname]);

  return (
    <>
      <HeroFuture />
      <LandingSections />
    </>
  );
}
