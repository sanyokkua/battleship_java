import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { createDeferredPwaGateway } from './pwa/deferredGateway';
import { createInstallGateway } from './pwa/installGateway';
import { registerPwa } from './pwa/registerPwa';
import { stripInvitationSecret } from './session/inviteSecret';
import './styles/tokens.css';
import './styles/base.css';
import './styles/layout.css';

const root = document.getElementById('root');

if (!root) throw new Error('Missing app root element');
stripInvitationSecret();

const pwaGateway = createDeferredPwaGateway(window, document, registerPwa);
const installGateway = createInstallGateway(window);

createRoot(root).render(
    <React.StrictMode>
        <App pwaGateway={pwaGateway} installGateway={installGateway} />
    </React.StrictMode>,
);
