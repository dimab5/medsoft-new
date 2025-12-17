import { defineConfig } from 'vite';

export default defineConfig({
	server: {
		port: 5173,
		proxy: {
			'/api': {
				target: 'http://localhost:8081',
				changeOrigin: true
			},
			'/ws': {
				target: 'ws://localhost:8081',
				ws: true
			}
		}
	}
});
