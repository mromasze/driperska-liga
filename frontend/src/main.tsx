import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { initConsentMode } from './store/consent';
import './index.css';

// Denies every advertising signal before any component mounts. The AdSense loader is only ever
// injected by a mounted slot, so this always lands first.
initConsentMode();

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element #root not found');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
