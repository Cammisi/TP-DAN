import { useEffect } from "react";
import { useRouter } from "next/router";
import { useSession } from "@/context/SessionContext";

// Envuelve una página privada: sin sesión redirige a /login, y con adminOnly
// también exige rol ADMIN. Mientras isLoading no renderiza nada (evita el
// parpadeo de mostrar contenido protegido antes de saber si hay sesión).
export default function RequireAuth({ children, adminOnly = false }) {
  const { session, isLoading } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;
    if (!session) {
      router.replace("/login");
      return;
    }
    if (adminOnly && session.role !== "ADMIN") {
      router.replace("/");
    }
  }, [isLoading, session, adminOnly, router]);

  if (isLoading || !session || (adminOnly && session.role !== "ADMIN")) {
    return null;
  }

  return children;
}
