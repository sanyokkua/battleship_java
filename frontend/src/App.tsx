import { useMemo } from 'react';
import { HttpGameAdapter } from './adapters/HttpGameAdapter';
import { GameAdapterProvider } from './adapters/GameAdapterContext';
import { ToastProvider } from './widgets/feedback/ToastContext';
import { ToastStack } from './widgets/feedback/ToastStack';
import { AppBar } from './widgets/layout/AppBar';
import { AppRoutes } from './routing/AppRoutes';

function App() {
  const adapter = useMemo(() => new HttpGameAdapter(), []);

  return (
    <GameAdapterProvider adapter={adapter}>
      <ToastProvider>
        <AppBar />
        <AppRoutes />
        <ToastStack />
      </ToastProvider>
    </GameAdapterProvider>
  );
}

export default App;
