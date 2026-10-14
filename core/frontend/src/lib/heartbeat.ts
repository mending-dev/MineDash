import { api } from './api';

const INTERVAL_MS = 5 * 60_000;
const MIN_GAP_MS = 30_000;

/**
 * Keeps the session alive while the panel is visible and notices an expired session as soon
 * as the tab comes back. A 401 answer logs the user out (handled in api.ts).
 * Hidden tabs send nothing, so a forgotten tab runs into the session timeout.
 */
export function startHeartbeat(): () => void {
    let last = Date.now();

    const beat = () => {
        if (document.hidden || Date.now() - last < MIN_GAP_MS) return;
        last = Date.now();
        api.get('/api/auth/me').catch(() => {
            // Network problems are ignored, a 401 is handled in api.ts
        });
    };

    const timer = window.setInterval(beat, INTERVAL_MS);
    document.addEventListener('visibilitychange', beat);

    return () => {
        window.clearInterval(timer);
        document.removeEventListener('visibilitychange', beat);
    };
}