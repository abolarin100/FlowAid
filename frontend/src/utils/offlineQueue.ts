/**
 * Offline-first submission queue for recipient enrollment.
 *
 * Program staff enrolling recipients in the field frequently have no
 * connectivity. Rather than losing the form data, we queue the submission
 * in localStorage and flush it automatically the next time the browser
 * comes back online (or on demand). Each queued item carries a locally
 * generated id so a flush that partially succeeds (e.g. tab closed mid-sync)
 * never double-submits the same enrollment twice.
 *
 * NOTE: this is plain localStorage, which is fine for a real deployed app
 * (this file only runs in the browser, not inside a Claude artifact sandbox
 * where localStorage is unavailable).
 */

const QUEUE_KEY = "flowaid_offline_recipient_queue";

export interface QueuedRecipient {
  localId: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  countryCode: string;
  region?: string;
  preferredPaymentMethod?: string;
  monthlyIncomeUsd?: number;
  householdSize?: number;
  queuedAt: string;
}

function readQueue(): QueuedRecipient[] {
  try {
    const raw = localStorage.getItem(QUEUE_KEY);
    return raw ? (JSON.parse(raw) as QueuedRecipient[]) : [];
  } catch {
    return [];
  }
}

function writeQueue(items: QueuedRecipient[]) {
  localStorage.setItem(QUEUE_KEY, JSON.stringify(items));
}

export function enqueueRecipient(
  entry: Omit<QueuedRecipient, "localId" | "queuedAt">,
): QueuedRecipient {
  const queued: QueuedRecipient = {
    ...entry,
    localId:
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `local-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    queuedAt: new Date().toISOString(),
  };
  const items = readQueue();
  items.push(queued);
  writeQueue(items);
  return queued;
}

export function getQueuedRecipients(): QueuedRecipient[] {
  return readQueue();
}

export function removeQueuedRecipient(localId: string) {
  writeQueue(readQueue().filter((i) => i.localId !== localId));
}

export function queueCount(): number {
  return readQueue().length;
}

/**
 * Attempts to submit every queued recipient via the provided create function.
 * Successfully submitted entries are removed from the queue; entries that
 * fail (e.g. still offline, or a real validation error) stay queued for the
 * next attempt. Returns counts so the UI can show a sync summary.
 */
export async function flushQueue(
  createFn: (entry: Omit<QueuedRecipient, "localId" | "queuedAt">) => Promise<unknown>,
): Promise<{ synced: number; remaining: number; failed: QueuedRecipient[] }> {
  const items = readQueue();
  if (items.length === 0) {
    return { synced: 0, remaining: 0, failed: [] };
  }

  let synced = 0;
  const failed: QueuedRecipient[] = [];

  for (const item of items) {
    try {
      const { localId, queuedAt, ...payload } = item;
      await createFn(payload);
      removeQueuedRecipient(localId);
      synced++;
    } catch (e) {
      // Leave it queued — could be offline again, or a duplicate-phone
      // conflict the caseworker needs to resolve manually.
      failed.push(item);
    }
  }

  return { synced, remaining: queueCount(), failed };
}
