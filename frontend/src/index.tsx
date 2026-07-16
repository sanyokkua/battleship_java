import React from "react";
import ReactDOM from "react-dom/client";
import {BrowserRouter} from "react-router-dom";
// Side-effect-only imports: initializes i18next (en/uk locales) so translations
// are ready before any component renders.
import "./i18n";
import App from "./App";
// Side-effect-only imports: load the custom CSS design system (tokens, then base
// styles that consume them) ahead of any component-level CSS.
import "./design/tokens.css";
import "./design/base.css";

const root = ReactDOM.createRoot(document.getElementById("root") as HTMLElement);

/**
 * Mounts {@link App} into the `#root` element (see `index.html`), wrapped in
 * `React.StrictMode` (dev-only extra checks/double-invocation) and `BrowserRouter`
 * (history-API routing consumed by `routing/AppRoutes`).
 */
root.render(
    <React.StrictMode>
        <BrowserRouter>
            <App/>
        </BrowserRouter>
    </React.StrictMode>
);
