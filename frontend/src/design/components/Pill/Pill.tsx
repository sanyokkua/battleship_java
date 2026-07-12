import type { ReactNode } from 'react';
import './Pill.css';

export type PillVariant = 'warn' | 'ok' | 'turn';

export type PillProps = {
  variant: PillVariant;
  children: ReactNode;
};

export function Pill({ variant, children }: PillProps) {
  return <span className={`pill ${variant}`}>{children}</span>;
}
