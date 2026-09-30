import { useState } from "react";
import Head from "next/head";
import Link from "next/link";
import { useRouter } from "next/router";
import { useSession } from "@/context/SessionContext";
import styles from "@/styles/Auth.module.css";

export default function Login() {
  const { login } = useSession();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const justRegistered = router.query.registered === "1";

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await login(email, password);
      router.push("/");
    } catch (err) {
      setError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <Head>
        <title>Iniciar sesión — ¿Quién Juega?</title>
      </Head>
      <div className={styles.wrapper}>
        <div className={styles.card}>
          <h1 className={styles.title}>¿Quién Juega?</h1>
          <p className={styles.subtitle}>Alertas deportivas en tiempo real</p>

          {justRegistered && (
            <p className={styles.success}>Cuenta creada. Iniciá sesión para continuar.</p>
          )}
          {error && <p className={styles.error}>{error}</p>}

          <form onSubmit={handleSubmit}>
            <div className={styles.field}>
              <label htmlFor="email">Correo electrónico</label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
              />
            </div>
            <div className={styles.field}>
              <label htmlFor="password">Contraseña</label>
              <input
                id="password"
                type="password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
              />
            </div>
            <button type="submit" className={styles.submit} disabled={isSubmitting}>
              {isSubmitting ? "Ingresando…" : "Iniciar sesión"}
            </button>
          </form>

          <p className={styles.footer}>
            ¿No tenés una cuenta? <Link href="/register">Registrarse</Link>
          </p>
        </div>
      </div>
    </>
  );
}
