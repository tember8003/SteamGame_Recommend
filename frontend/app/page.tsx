import Header from "@/components/Header";
import Button from "@/components/ui/Button";
import Concierge from "@/components/mascot/Concierge";
import ConciergeBar from "@/components/landing/ConciergeBar";
import { siSteam } from "simple-icons";
import styles from "./page.module.css";

const MC_TAGS = ["Adventure", "Tech", "Magic", "Exploration", "Hardcore"];

export default function Home() {
  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.main}>
        <section className={styles.hero}>
          <h1 className={styles.title}>Find your next way to play.</h1>
          <p className={styles.subtitle}>
            Discover great Steam games or build the perfect Minecraft modpack,
            guided by your AI concierge.
          </p>
        </section>

        <section className={styles.modes}>
          <article className={`${styles.card} ${styles.steamCard}`}>
            <div className={styles.cardBody}>
              <span className={`${styles.mark} ${styles.steamMark}`}>
                <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d={siSteam.path} />
                </svg>
              </span>
              <h2 className={styles.cardTitle}>
                STEAM <span className={styles.steamAccent}>Picks</span>
              </h2>
              <p className={styles.cardDesc}>
                Personalized game recommendations just for you.
              </p>
            </div>
            <Button variant="steam" href="/steam" className={styles.cardCta}>
              Explore Steam Picks →
            </Button>
          </article>

          <article className={`${styles.card} ${styles.mcCard}`} aria-disabled="true">
            <span className={styles.comingSoon}>Coming soon</span>
            <div className={styles.cardBody}>
              <span className={`${styles.mark} ${styles.mcMark}`} aria-hidden="true">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="2" y="8" width="20" height="14" fill="#7a4e26" />
                  <rect x="2" y="3" width="20" height="6" fill="#6aa84f" />
                  <rect x="2" y="9" width="4" height="2" fill="#6aa84f" />
                  <rect x="9" y="9" width="4" height="2" fill="#6aa84f" />
                  <rect x="16" y="9" width="4" height="2" fill="#6aa84f" />
                  <rect x="6" y="13" width="2" height="2" fill="#5f3c1e" />
                  <rect x="10" y="16" width="2" height="2" fill="#5f3c1e" />
                  <rect x="15" y="18" width="2" height="2" fill="#5f3c1e" />
                </svg>
              </span>
              <h2 className={styles.cardTitle}>
                MINECRAFT <span className={styles.mcAccent}>Modpacks</span>
              </h2>
              <p className={styles.cardDesc}>
                Find or build the perfect modpack for your adventure.
              </p>
              <div className={styles.tags}>
                {MC_TAGS.map((t) => (
                  <span key={t} className={styles.tag}>
                    {t}
                  </span>
                ))}
              </div>
            </div>
            <Button variant="minecraft" disabled className={styles.cardCta}>
              Explore Minecraft Mods →
            </Button>
          </article>
        </section>

        <section className={styles.bottom}>
          <ConciergeBar />
          <aside className={styles.mascotPanel}>
            <div className={styles.mascotHead}>
              <span className={styles.spark}>✦</span> mascot
            </div>
            <div className={styles.mascotInner}>
              <div className={styles.bubble}>
                <p>Hi, I&apos;m your AI concierge.</p>
                <p>How can I help you today?</p>
              </div>
              <div className={styles.mascotArt}>
                <Concierge state="idle" />
              </div>
            </div>
          </aside>
        </section>

        <footer className={styles.footer}>
          Not affiliated with Valve or Mojang. PLAYLOOM is an independent project.
        </footer>
      </main>
    </div>
  );
}
