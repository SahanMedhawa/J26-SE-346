import { useEffect, useState } from "react";
export function UserAvatar({ name, url, className = "" }: { name: string; url?: string | null; className?: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [url]);
  const initials = name.trim().split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase() || "U";
  const safeUrl = url && /^https?:\/\//i.test(url) ? url : null;
  return <span className={`user-avatar ${className}`} aria-hidden="true">{safeUrl && !failed ? <img src={safeUrl} alt="" referrerPolicy="no-referrer" onError={() => setFailed(true)}/> : initials}</span>;
}
