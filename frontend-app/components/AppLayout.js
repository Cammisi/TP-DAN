import Link from "next/link";
import { useRouter } from "next/router";
import { useSession } from "@/context/SessionContext";
import styles from "@/styles/AppLayout.module.css";

const NAV_ITEMS = [
  { href: "/", label: "Panel Principal" },
  { href: "/subscriptions", label: "Suscripciones" },
  { href: "/alerts", label: "Alertas" },
];

// Shell común de las pantallas protegidas (T-63/T-64/T-65): sidebar + contenido.
export default function AppLayout({ title, children }) {
  const { session, logout } = useSession();
  const router = useRouter();

  const navItems = session?.role === "ADMIN"
    ? [...NAV_ITEMS, { href: "/admin", label: "Admin" }]
    : NAV_ITEMS;

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}>¿Quién Juega?</div>
        {session && <div className={styles.fan}>{session.email}</div>}
        <nav className={styles.nav}>
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={router.pathname === item.href ? styles.navItemActive : styles.navItem}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <button type="button" className={styles.logout} onClick={logout}>
          Cerrar sesión
        </button>
      </aside>
      <main className={styles.content}>
        {title && <h1 className={styles.pageTitle}>{title}</h1>}
        {children}
      </main>
    </div>
  );
}
