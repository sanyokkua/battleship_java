import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { stripInvitationSecret } from './session/inviteSecret';
import './styles/tokens.css';
import './styles/base.css';
import './styles/layout.css';

const root = document.getElementById('root');

if (!root) throw new Error('Missing app root element');
stripInvitationSecret();

createRoot(root).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
);
