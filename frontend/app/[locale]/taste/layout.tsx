import Header from '@/components/Header';
import styles from '@/components/taste/taste.module.css';
import './tokens.css';
import { TasteSessionProvider } from '@/lib/taste/session';

export default function TasteLayout({ children }: { children: React.ReactNode }) {
  return <div className={styles.page}>
    <div className={styles.headerShell}><Header /></div>
    <TasteSessionProvider>{children}</TasteSessionProvider>
  </div>;
}
