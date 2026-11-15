import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PriorityBadge, SlaBadge, StatusBadge } from './badges';

describe('badges', () => {
  it('shows priority code and label (never colour alone)', () => {
    render(<PriorityBadge priority="P1" showLabel />);
    expect(screen.getByText('P1')).toBeInTheDocument();
    expect(screen.getByText('Critical')).toBeInTheDocument();
  });

  it('renders a dash when there is no priority yet', () => {
    render(<PriorityBadge />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('humanizes status and SLA labels', () => {
    render(<><StatusBadge status="IN_PROGRESS" /><SlaBadge status="AT_RISK" /><SlaBadge status="NOT_APPLICABLE" /></>);
    expect(screen.getByText('In progress')).toBeInTheDocument();
    expect(screen.getByText(/At risk/)).toBeInTheDocument();
    expect(screen.getByText(/No SLA/)).toBeInTheDocument();
  });
});
