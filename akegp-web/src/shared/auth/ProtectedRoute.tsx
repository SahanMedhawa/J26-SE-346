import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthProvider";
export function ProtectedRoute(){const {user,loading,error,reload}=useAuth();const location=useLocation();
 if(loading)return <main className="account-page"><p>Loading your account…</p></main>;
 if(error)return <main className="account-page"><p role="alert">{error}</p><button onClick={()=>void reload()}>Retry connection</button></main>;
 if(!user){sessionStorage.setItem("akegp.returnTo",location.pathname+location.search);return <Navigate to="/login" replace/>;}
 return <Outlet/>;
}
