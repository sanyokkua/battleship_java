import type {ReactNode} from 'react';
import {LoadingBar} from '../../design/components/LoadingBar/LoadingBar';
import './LoadingView.css';

export type LoadingViewProps = {
    title: string;
    subtitle: string;
    icon?: ReactNode; // defaults to a sensible icon (e.g. ⚓) if not provided
};

/**
 * Shared "fetch in flight" widget — not a routed screen. Screens render this
 * conditionally while their initial data fetch is pending, e.g.
 * `if (loading) return <LoadingView title={...} subtitle={...} />;`.
 *
 * Ported from MOCKUP.html's `loading:()=>...` screen-render function: a
 * top LoadingBar, then a centered bouncing anchor icon, title and subtitle.
 * All text is supplied by the caller (already localized) — this component
 * never calls useTranslation() itself.
 */
export function LoadingView({title, subtitle, icon}: LoadingViewProps) {
    return (
        <div className="loading-view">
            <LoadingBar label={title}/>
            <div className="screen">
                <div className="loading-view-body">
                    <div className="hero-illust loading-view-icon">{icon ?? '⚓'}</div>
                    <h2 className="title">{title}</h2>
                    <p className="sub">{subtitle}</p>
                </div>
            </div>
        </div>
    );
}
