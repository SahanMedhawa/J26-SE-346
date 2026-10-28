import { ProviderIcon } from "../../shared/ui/ProviderIcon";
import { useEffect, useState } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../shared/auth/useAuth";
import { usersApi } from "../../shared/api/usersApi";
import "./LoginPage.css";


export function LoginPage() {
  const { user, loading } = useAuth();
  const [providers, setProviders] = useState<string[] | null>(null);
  const [error, setError] = useState("");
  const [signingIn, setSigningIn] = useState<string | null>(null);
  const [params] = useSearchParams();
  const signInErrors: Record<string, string> = {
    email_unverified: "Your provider must supply a verified email. On GitHub, verify your primary email in account settings, then try again.",
    link_required: "Sign in with your original provider first, then link this provider from Profile. We need that extra confirmation for this email address.",
    link_expired: "This account-link request expired. Sign in with your original provider and start a new link from Profile.",
    account_conflict: "This provider is already linked to another account. Sign in with that account or choose a different provider.",
    oauth: "We couldn't complete sign-in. Please try again, or use your other linked provider.",
  };

  async function loadProviders() {
    setError("");
    setProviders(null);
    try { setProviders(await usersApi.providers()); }
    catch (error) { setError(error instanceof Error ? error.message : "Unable to load sign-in options."); }
  }
  useEffect(() => { void loadProviders(); }, []);
  if (user && !loading) return <Navigate to="/workspaces" replace />;

  return <main className="login-screen">
    <section className="login-story" aria-labelledby="login-story-title">
      <div className="login-wordmark"><span aria-hidden="true">✣</span> AKEGP <span className="login-wordmark-tag">/ PLATFORM</span></div>
      <div className="login-story-content">
        <p className="login-eyebrow"><span /> BUILT FOR THE BIG PICTURE</p>
        <h1 id="login-story-title">Good architecture.<br />Shared <em>understanding.</em></h1>
        <p className="login-story-copy">Bring your systems, decisions, and team together. Keep the intent behind your architecture connected to the code.</p>
        <div className="login-system" aria-hidden="true">
          <div className="login-system-heading"><span>YOUR ARCHITECTURE, CONNECTED</span><span>↗</span></div>
          <div className="login-graph">
            <svg className="login-graph-lines" viewBox="0 0 460 210" preserveAspectRatio="none"><path d="M230 44V86M230 86H76V124M230 86V124M230 86H384V124M76 166V188H384V166M230 166V188"/></svg>
            <div className="login-node login-node-core"><span>✣</span> Workspace</div>
            <div className="login-node login-node-decisions"><span>01</span> Decisions</div>
            <div className="login-node login-node-rules"><span>02</span> Rules</div>
            <div className="login-node login-node-code"><span>03</span> Conformance</div>
            <span className="login-graph-caption">ONE TEAM. A CLEARER VIEW.</span>
          </div>
        </div>
      </div>
      <footer className="login-story-footer"><span>ARCHITECTURE KNOWLEDGE & GOVERNANCE</span><span>From intent to implementation.</span></footer>
    </section>
    <section className="login-panel" aria-labelledby="login-title">
      <span className="login-panel-label">YOUR NEXT CHAPTER STARTS HERE</span>
      <div className="login-form-content">
        <div className="login-entry-icon" aria-hidden="true">↗</div>
        <p className="login-eyebrow">WELCOME TO YOUR WORKSPACE</p>
        <h2 id="login-title">A place for <br />your big ideas.</h2>
        <p className="login-intro">Sign in to pick up where you left off,<br className="login-desktop-break" /> or start something new with your team.</p>
        {params.has("error") && <div className="login-alert" role="alert">{signInErrors[params.get("error") ?? "oauth"] ?? signInErrors.oauth}</div>}
        <div className="login-providers" aria-label="Sign-in options" aria-busy={providers === null && !error}>
          {providers === null && !error && <div className="login-provider-loading" role="status">Getting your sign-in options ready…</div>}
          {providers?.map(provider => <a className={`login-provider login-provider-${provider}`} href={`/oauth2/authorization/${provider}`} key={provider} aria-disabled={signingIn !== null} onClick={event => { if (signingIn) event.preventDefault(); else setSigningIn(provider); }}>
            <ProviderIcon provider={provider} /><span>{signingIn === provider ? "Connecting…" : `Continue with ${provider === "google" ? "Google" : "GitHub"}`}</span><span className="login-provider-arrow" aria-hidden="true">↗</span>
          </a>)}
          {error && <div className="login-alert" role="alert"><p>{error}</p><button type="button" onClick={() => void loadProviders()}>Try again ↗</button></div>}
          {providers?.length === 0 && <div className="login-alert" role="status">Sign-in isn't available yet. Contact your workspace administrator to get connected.</div>}
        </div>
        <div className="login-signin-note"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3"/></svg><p>Your trusted account. No extra password.<br /><span>New here? Your account is created when you sign in.</span></p></div>
      </div>
      <footer className="login-panel-footer"><span>Thoughtful systems start with shared knowledge.</span><span>✣</span></footer>
    </section>
  </main>;
}
