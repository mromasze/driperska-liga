import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { initConsentMode } from './store/consent';
import { CMP_MODE } from './lib/ads';
import './index.css';

// Denies every advertising signal before any component mounts. Only on the own-CMP path: when
// Google's CMP is in charge it owns consent end to end, and a second system writing the same
// signals could only muddy the picture.
if (CMP_MODE === 'own') initConsentMode();

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element #root not found');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
