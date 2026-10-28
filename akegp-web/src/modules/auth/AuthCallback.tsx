import { Navigate } from "react-router-dom";
import { useAuth } from "../../shared/auth/useAuth";
export function AuthCallback(){const {user,loading,error}=useAuth();if(loading)return <main className="account-page">Completing sign-in…</main>;if(error)return <main className="account-page" role="alert">{error}</main>;if(!user)return <Navigate to="/login?error=oauth" replace/>;
 const target=sessionStorage.getItem("akegp.returnTo");sessionStorage.removeItem("akegp.returnTo");return <Navigate to={target?.startsWith("/")&&!target.startsWith("//")&&!target.startsWith("/auth/")&&!target.startsWith("/login")?target:"/workspaces"} replace/>;}
