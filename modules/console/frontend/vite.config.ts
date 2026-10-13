import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// Builds ONE self-contained file: dist/console.js (Svelte runtime and styles included)
export default defineConfig({
    plugins: [svelte({ compilerOptions: { css: 'injected' } })],
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2020',
        minify: 'esbuild',
        lib: {
            entry: 'src/main.ts',
            formats: ['es'],
            fileName: () => 'console.js'
        }
    }
});