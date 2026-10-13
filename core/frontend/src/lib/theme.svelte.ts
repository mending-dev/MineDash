const STORAGE_KEY = 'minedash.theme';

class ThemeState {
    // theme-init.js has already applied the class before the app starts
    dark = $state(document.documentElement.classList.contains('dark'));

    toggle(): void {
        this.set(!this.dark);
    }

    set(dark: boolean): void {
        this.dark = dark;
        document.documentElement.classList.toggle('dark', dark);
        try {
            localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
        } catch {
            // Storage is blocked, the choice just is not remembered
        }
    }
}

export const theme = new ThemeState();