// Install once before rendering so existing feature clients also send cookies and CSRF tokens.
const nativeFetch = window.fetch.bind(window);
window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = new URL(input instanceof Request ? input.url : String(input), window.location.href);
  if (url.origin !== window.location.origin || !url.pathname.startsWith("/api/")) return nativeFetch(input, init);
  const method = (init?.method ?? (input instanceof Request ? input.method : "GET")).toUpperCase();
  const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const tokenResponse = await nativeFetch("/api/users/csrf", { credentials: "include", cache: "no-store" });
    if (!tokenResponse.ok) throw new Error("Could not secure the request. Please reload and try again.");
    const csrf = await tokenResponse.json();
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await nativeFetch(input, { ...init, headers, credentials: "include" });
  if (response.status === 401 && !url.pathname.startsWith("/api/users/") && window.location.pathname !== "/login") {
    sessionStorage.setItem("akegp.returnTo", window.location.pathname + window.location.search);
    window.location.assign("/login");
  }
  return response;
};
