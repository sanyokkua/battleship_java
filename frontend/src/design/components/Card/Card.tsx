import type { ReactNode } from 'react';
import './Card.css';

export type CardProps = {
  children?: ReactNode;
  className?: string;
};

export function Card({ children, className }: CardProps) {
  const classes = ['card', className].filter(Boolean).join(' ');

  return <div className={classes}>{children}</div>;
}
