import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Account, User, usersApi } from "../../shared/api/usersApi";
import { useAuth } from "../../shared/auth/useAuth";
import { Icon } from "../../shared/ui/Icon";
import { UserAvatar } from "../../shared/ui/UserAvatar";
import { ProviderIcon } from "../../shared/ui/ProviderIcon";

type ProfileInput = Pick<User, "displayName" | "bio" | "avatarUrl">;
type Notice = { message: string; error?: boolean } | null;
const linkingErrors: Record<string, string> = {
  account_conflict: "This provider is linked to another account. Your connected accounts are unchanged.",
  email_unverified: "A verified primary email is required to connect this provider.",
  link_expired: "The link request expired. Start a new connection below.",
  oauth: "The provider could not be connected. Please try again.",
};
export function ProfilePage() {
  const { user, updateUser, logout } = useAuth();
  const [params] = useSearchParams();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [providers, setProviders] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  async function load() {
    setLoading(true); setError("");
    try { const [linked, enabled] = await Promise.all([usersApi.accounts(), usersApi.providers()]); setAccounts(linked); setProviders(enabled); }
    catch (error) { setError(error instanceof Error ? error.message : "Unable to load your connected accounts."); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  if (!user) return null;
  const initialNotice = params.has("linkError") ? { message: linkingErrors[params.get("linkError") ?? "oauth"] ?? linkingErrors.oauth, error: true } : params.has("linked") ? { message: "Account connected. You can now use it to sign in to this account." } : null;
  async function save(input: ProfileInput) { const saved = await usersApi.update(input); updateUser(saved); return saved; }
  async function providerAction(provider: string, linked: boolean) {
    if (linked) { await usersApi.unlink(provider); await load(); }
    else { const result = await usersApi.link(provider); window.location.assign(result.url); }
  }
  return <ProfileEditor user={user} accounts={accounts} providers={providers} loading={loading} error={error} onRetry={() => void load()} onSave={save} onProviderAction={providerAction} onLogout={logout} initialNotice={initialNotice}/>;
}
export function ProfileEditor({ user, accounts, providers, loading, error, onRetry, onSave, onProviderAction, onLogout, initialNotice = null }: {
  user: User; accounts: Account[]; providers: string[]; loading: boolean; error: string; onRetry: () => void;
  onSave: (input: ProfileInput) => Promise<User>; onProviderAction: (provider: string, linked: boolean) => Promise<void>;
  onLogout: () => Promise<void>; initialNotice?: Notice;
}) {
  const original = { displayName: user.displayName, bio: user.bio ?? "", avatarUrl: user.avatarUrl ?? "" };
  const [draft, setDraft] = useState(original);
  const [notice, setNotice] = useState<Notice>(initialNotice);
  const [saving, setSaving] = useState(false);
  const [providerBusy, setProviderBusy] = useState<string | null>(null);
  const [disconnect, setDisconnect] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  useEffect(() => { setDraft({ displayName: user.displayName, bio: user.bio ?? "", avatarUrl: user.avatarUrl ?? "" }); }, [user.id, user.displayName, user.bio, user.avatarUrl]);
  const dirty = draft.displayName !== original.displayName || draft.bio !== original.bio || draft.avatarUrl !== original.avatarUrl;
  const busy = saving || !!providerBusy || signingOut;
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setNotice(null);
    try {
      const saved = await onSave({ displayName: draft.displayName.trim(), bio: draft.bio.trim(), avatarUrl: draft.avatarUrl.trim() });
      setDraft({ displayName: saved.displayName, bio: saved.bio ?? "", avatarUrl: saved.avatarUrl ?? "" });
      setNotice({ message: "Your profile has been updated." });
    } catch (error) { setNotice({ message: error instanceof Error ? error.message : "Unable to save your profile.", error: true }); }
    finally { setSaving(false); }
  }
  async function changeProvider(provider: string, linked: boolean) {
    setProviderBusy(provider); setNotice(null);
    try { await onProviderAction(provider, linked); setDisconnect(null); if (linked) setNotice({ message: `${provider === "google" ? "Google" : "GitHub"} disconnected.` }); }
    catch (error) { setNotice({ message: error instanceof Error ? error.message : "Unable to update connected account.", error: true }); }
    finally { setProviderBusy(null); }
  }
  async function signOut() {
    setSigningOut(true);
    try { await onLogout(); } catch (error) { setNotice({ message: error instanceof Error ? error.message : "Unable to sign out.", error: true }); setSigningOut(false); }
  }
  return <main className="platform-page profile-page">
    <div className="platform-page-heading"><div><p className="platform-eyebrow">YOUR ACCOUNT</p><h1>Your profile<span>.</span></h1><p>Manage your personal details and how you connect to your workspaces.</p></div><span className="platform-badge platform-badge-success"><span className="platform-status-dot"/>Signed in</span></div>
    {notice && <div className={`platform-notice${notice.error ? " platform-notice-error" : ""}`} role={notice.error ? "alert" : "status"}><Icon name={notice.error ? "shield" : "check"}/><span>{notice.message}</span><button type="button" aria-label="Dismiss message" onClick={() => setNotice(null)}>×</button></div>}
    <div className="profile-layout">
      <aside className="profile-sidebar">
        <div className="profile-summary"><UserAvatar name={draft.displayName || user.displayName} url={draft.avatarUrl} className="user-avatar-large"/><h2>{user.displayName}</h2><p>{user.email}</p><span className="platform-badge">Personal account</span></div>
        <nav className="profile-section-nav" aria-label="Profile sections"><a href="#personal-details"><Icon name="user"/>Personal details<Icon name="arrow"/></a><a href="#connected-accounts"><Icon name="link"/>Connected accounts<Icon name="arrow"/></a><a href="#account-session"><Icon name="shield"/>Account session<Icon name="arrow"/></a></nav>
        <div className="profile-sidebar-note"><Icon name="shield"/><p>Your profile follows you across every workspace. Workspace roles are managed by your team.</p></div>
      </aside>
      <div className="profile-panels">
        <section className="platform-panel" id="personal-details" aria-labelledby="personal-title">
          <div className="platform-panel-heading"><span className="platform-panel-icon"><Icon name="user"/></span><div><h2 id="personal-title">Personal details</h2><p>How your team sees you across AKEGP.</p></div></div>
          <form onSubmit={event => void save(event)} className="profile-form">
            <div className="profile-photo-row"><UserAvatar name={draft.displayName || user.displayName} url={draft.avatarUrl} className="user-avatar-medium"/><div><strong>Profile photo</strong><p>Use an image URL, or keep your initials.</p></div></div>
            <div className="profile-field-grid"><label className="platform-field">Display name<input name="displayName" required maxLength={255} value={draft.displayName} onChange={event => setDraft({ ...draft, displayName: event.target.value })} autoComplete="name" placeholder="Your name"/></label><label className="platform-field">Email address<div className="platform-readonly"><Icon name="shield"/><input type="email" readOnly value={user.email} aria-describedby="profile-email-note"/></div><small id="profile-email-note">Managed by your sign-in provider.</small></label></div>
            <label className="platform-field">About you <span className="platform-optional">Optional</span><textarea name="bio" maxLength={1000} rows={3} value={draft.bio} onChange={event => setDraft({ ...draft, bio: event.target.value })} placeholder="Your role, interests, or what you're building…"/><small className="profile-character-count">{draft.bio.length} / 1000</small></label>
            <label className="platform-field">Profile photo URL <span className="platform-optional">Optional</span><div className="platform-input-icon"><Icon name="photo"/><input name="avatarUrl" type="url" pattern="https?://.*" maxLength={2048} value={draft.avatarUrl} onChange={event => setDraft({ ...draft, avatarUrl: event.target.value })} placeholder="https://example.com/your-photo.jpg"/></div><small>Use a public HTTP or HTTPS image URL.</small></label>
            <div className="profile-save-bar"><span>{dirty ? "You have unsaved changes" : "Your details are up to date"}</span><div>{dirty && <button type="button" className="platform-button platform-button-ghost" disabled={busy} onClick={() => { setDraft(original); setNotice(null); }}>Discard changes</button>}<button className="platform-button platform-button-primary" disabled={busy || !dirty || !draft.displayName.trim()} type="submit"><Icon name="check"/>{saving ? "Saving…" : "Save changes"}</button></div></div>
          </form>
        </section>
        <section className="platform-panel" id="connected-accounts" aria-labelledby="connected-title">
          <div className="platform-panel-heading"><span className="platform-panel-icon"><Icon name="link"/></span><div><h2 id="connected-title">Connected accounts</h2><p>Sign in your way. Every linked provider opens the same account.</p></div></div>
          {error && <div className="platform-notice platform-notice-error" role="alert"><span>{error}</span><button onClick={onRetry}>Try again</button></div>}
          {loading ? <div className="profile-loading" role="status">Loading connected accounts…</div> : ["google", "github"].map(provider => {
            const account = accounts.find(a => a.provider === provider);
            const available = !!account || providers.includes(provider);
            const name = provider === "google" ? "Google" : "GitHub";
            return <div className="provider-account" key={provider}><span className={`provider-account-icon provider-account-icon-${provider}`}><ProviderIcon provider={provider}/></span><div className="provider-account-info"><strong>{name}{account && <span className="provider-connected"><Icon name="check"/>Connected</span>}</strong><span>{account ? account.providerEmail : `Connect your ${name} account`}</span></div>{disconnect === provider ? <div className="provider-disconnect-confirm"><span>Disconnect {name}?</span><button className="platform-button platform-button-danger" disabled={busy} onClick={() => void changeProvider(provider, true)}>Disconnect</button><button className="platform-button platform-button-ghost" disabled={busy} onClick={() => setDisconnect(null)}>Cancel</button></div> : <button className={`platform-button ${account ? "platform-button-ghost" : "platform-button-secondary"}`} disabled={busy || loading || !!error || !available || (!!account && accounts.length <= 1)} onClick={() => account ? setDisconnect(provider) : void changeProvider(provider, false)}>{providerBusy === provider ? "Connecting…" : !available ? "Unavailable" : account ? "Disconnect" : "Connect"}{!account && available && <Icon name="arrow"/>}</button>}</div>;
          })}
          <div className="profile-provider-note"><Icon name="shield"/><p>Keep at least one account connected so you can always sign in. Use the same verified email to link automatically, or connect another email here.</p></div>
        </section>
        <section className="platform-panel profile-session" id="account-session" aria-labelledby="session-title"><div><h2 id="session-title">Account session</h2><p>Signed in as <strong>{user.email}</strong>. Sign out on shared devices.</p></div><button className="platform-button platform-button-secondary" disabled={busy} onClick={() => void signOut()}><Icon name="logout"/>{signingOut ? "Signing out…" : "Sign out"}</button></section>
      </div>
    </div>
  </main>;
}
