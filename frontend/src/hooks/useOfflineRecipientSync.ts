import { useCallback, useEffect, useState } from "react";
import { recipientsApi } from "../api";
import {
  enqueueRecipient,
  flushQueue,
  getQueuedRecipients,
  queueCount,
  type QueuedRecipient,
} from "../utils/offlineQueue";

/**
 * Tracks browser connectivity and keeps the local recipient-enrollment queue
 * in sync: flushes automatically when the connection comes back, and exposes
 * a manual sync trigger + live queue count for the UI.
 */
export function useOfflineRecipientSync(onSynced?: () => void) {
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [pending, setPending] = useState<QueuedRecipient[]>(getQueuedRecipients());
  const [syncing, setSyncing] = useState(false);

  const sync = useCallback(async () => {
    if (queueCount() === 0) return;
    setSyncing(true);
    try {
      const result = await flushQueue((payload) => recipientsApi.create(payload));
      setPending(getQueuedRecipients());
      if (result.synced > 0) onSynced?.();
    } finally {
      setSyncing(false);
    }
  }, [onSynced]);

  useEffect(() => {
    const goOnline = () => {
      setIsOnline(true);
      sync();
    };
    const goOffline = () => setIsOnline(false);
    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    // Attempt a sync on mount in case items were queued in a previous session.
    if (navigator.onLine) sync();
    return () => {
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const queueForLater = useCallback(
    (entry: Omit<QueuedRecipient, "localId" | "queuedAt">) => {
      enqueueRecipient(entry);
      setPending(getQueuedRecipients());
    },
    [],
  );

  return { isOnline, pending, syncing, sync, queueForLater };
}
