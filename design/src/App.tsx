import { Route, Routes } from "react-router-dom";
import { CursorTelemetry } from "./components/CursorTelemetry";
import { GrainOverlay } from "./components/GrainOverlay";
import { Header } from "./components/Header";
import { WiredBackdrop } from "./components/WiredBackdrop";
import { PageLoader } from "./components/PageLoader";
import { ScreenEffect } from "./components/ScreenEffect";
import { StressSignals } from "./components/StressSignals";
import { Footer } from "./components/Footer";
import { RequireAdmin, RequireAuth } from "./components/RouteGuards";
import { AdminPage } from "./pages/AdminPage";
import { CheckoutPage } from "./pages/CheckoutPage";
import { CompleteSetupPage } from "./pages/CompleteSetupPage";
import { DashboardPage } from "./pages/DashboardPage";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";

export default function App() {
  return (
    <>
      <PageLoader />
      <GrainOverlay />
      <WiredBackdrop />
      <Header />
      <div className="mao-app-body">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/complete-setup" element={<CompleteSetupPage />} />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <DashboardPage />
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <AdminPage />
              </RequireAdmin>
            }
          />
        </Routes>
        <Footer />
      </div>
      <ScreenEffect />
      <StressSignals />
      <CursorTelemetry />
    </>
  );
}
