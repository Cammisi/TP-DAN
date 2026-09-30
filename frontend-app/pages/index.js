import { useEffect, useState } from "react";
import Head from "next/head";
import Link from "next/link";
import RequireAuth from "@/components/RequireAuth";
import AppLayout from "@/components/AppLayout";
import { useSession } from "@/context/SessionContext";
import { getSubscriptions, getNotifications } from "@/lib/api";
import styles from "@/styles/Home.module.css";

function HomeContent() {
  const { session } = useSession();
  const [subscriptionCount, setSubscriptionCount] = useState(null);
  const [notificationCount, setNotificationCount] = useState(null);

  useEffect(() => {
    getSubscriptions().then((subs) => setSubscriptionCount(subs.length)).catch(() => {});
    getNotifications().then((n) => setNotificationCount(n.length)).catch(() => {});
  }, []);

  return (
    <AppLayout title="Panel Principal">
      <p className={styles.greeting}>Hola, {session?.email}.</p>
      <div className={styles.cards}>
        <Link href="/subscriptions" className={styles.card}>
          <span className={styles.cardNumber}>{subscriptionCount ?? "…"}</span>
          <span>equipos seguidos</span>
        </Link>
        <Link href="/alerts" className={styles.card}>
          <span className={styles.cardNumber}>{notificationCount ?? "…"}</span>
          <span>alertas recibidas</span>
        </Link>
      </div>
    </AppLayout>
  );
}

export default function Home() {
  return (
    <RequireAuth>
      <Head>
        <title>¿Quién Juega?</title>
        <meta name="description" content="Alertas deportivas en tiempo real" />
      </Head>
      <HomeContent />
    </RequireAuth>
  );
}
