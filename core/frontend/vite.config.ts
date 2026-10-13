import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
    plugins: [tailwindcss(), svelte()],
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2020'
    },
    server: {
        // "npm run dev" forwards API and WebSocket calls to a running Minecraft server
        proxy: {
            '/api': { target: 'http://localhost:8765', ws: true }
        }
    }
});