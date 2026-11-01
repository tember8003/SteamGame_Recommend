"use client";
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';

export function useCooldown() {
  const [deadline, setDeadline] = useState(0);
  const [remaining, setRemaining] = useState(0);
  useEffect(() => {
    if (!deadline) return;
    const timer = setInterval(() => {
      const seconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setRemaining(seconds);
      if (!seconds) clearInterval(timer);
    }, 500);
    return () => clearInterval(timer);
  }, [deadline]);
  function fromError(error: unknown) {
    if (error instanceof ApiError && error.status === 429) {
      const seconds = Math.max(1, error.retryAfterSeconds ?? 60);
      setRemaining(seconds); setDeadline(Date.now() + seconds * 1000);
    }
  }
  return { remaining, fromError };
}
