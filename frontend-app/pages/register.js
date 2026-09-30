import { useState } from "react";
import Head from "next/head";
import Link from "next/link";
import { useRouter } from "next/router";
import { register } from "@/lib/api";
import styles from "@/styles/Auth.module.css";

export default function Register() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await register(email, password);
      router.push("/login?registered=1");
    } catch (err) {
      setError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <Head>
        <title>Registrarse — ¿Quién Juega?</title>
      </Head>
      <div className={styles.wrapper}>
        <div className={styles.card}>
          <h1 className={styles.title}>¿Quién Juega?</h1>
          <p className={styles.subtitle}>Creá tu cuenta de fan</p>

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
                minLength={4}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="new-password"
              />
            </div>
            <button type="submit" className={styles.submit} disabled={isSubmitting}>
              {isSubmitting ? "Creando cuenta…" : "Registrarse"}
            </button>
          </form>

          <p className={styles.footer}>
            ¿Ya tenés cuenta? <Link href="/login">Iniciar sesión</Link>
          </p>
        </div>
      </div>
    </>
  );
}
