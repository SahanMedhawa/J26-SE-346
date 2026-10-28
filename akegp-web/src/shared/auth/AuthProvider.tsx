import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { ApiClientError } from "../api/client";
import { User, usersApi } from "../api/usersApi";
const AuthContext = createContext<{user: User | null; loading: boolean; error: string; reload: () => Promise<void>; updateUser: (user: User) => void; logout: () => Promise<void> } | null>(null);
export function AuthProvider({children}: {children: ReactNode}) {
 const [user,setUser]=useState<User|null>(null);const [loading,setLoading]=useState(true);const [error,setError]=useState("");
 async function reload() {setLoading(true);setError("");try{setUser(await usersApi.me());}catch(e){setUser(null);if(!(e instanceof ApiClientError && e.status===401))setError(e instanceof Error?e.message:"Unable to connect to the server");}finally{setLoading(false);}}
 async function logout(){await usersApi.logout();setUser(null);localStorage.removeItem("akegp.workspace");window.location.assign("/login");}
 useEffect(()=>{void reload();},[]);
 return <AuthContext.Provider value={{user,loading,error,reload,updateUser:setUser,logout}}>{children}</AuthContext.Provider>;
}
export function useAuth(){const value=useContext(AuthContext);if(!value)throw new Error("AuthProvider is required");return value;}
