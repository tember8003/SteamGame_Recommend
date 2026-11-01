"use client";

import Image from 'next/image';
import { useState } from 'react';
import styles from './taste.module.css';

export default function TastePortrait({ code, name, priority = false }: {
  code: string; name: string; priority?: boolean;
}) {
  const [failed, setFailed] = useState(false);
  return <div className={styles.portrait}>
    {failed ? <span className={styles.missingPortrait} role="img" aria-label={name}>{name}</span> :
      <Image src={`/images/taste/${code}.png`} alt={name} width={1024} height={1024}
        sizes={priority ? '(max-width: 700px) 90vw, 480px' : '(max-width: 600px) 44vw, (max-width: 1000px) 30vw, 260px'}
        priority={priority} onError={() => setFailed(true)} />}
  </div>;
}
