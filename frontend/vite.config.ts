import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  optimizeDeps: {
    include: [
      '@atlaskit/tokens',
      '@atlaskit/button',
      '@atlaskit/textfield',
      '@atlaskit/form',
      '@atlaskit/heading',
      '@atlaskit/spinner',
      '@atlaskit/lozenge',
      '@atlaskit/empty-state',
      '@atlaskit/page-layout',
      '@atlaskit/side-navigation',
      '@atlaskit/flag',
    ],
  },
});
