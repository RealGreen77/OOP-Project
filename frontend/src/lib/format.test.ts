import { describe, expect, it } from 'vitest';
import { toQuery } from '../api/endpoints';
import { fmtCountdown, fmtMinutes, humanize } from './format';

describe('format helpers', () => {
  it('humanizes enum constants', () => {
    expect(humanize('PENDING_INFO')).toBe('Pending info');
    expect(humanize(undefined)).toBe('');
  });

  it('formats SLA durations', () => {
    expect(fmtMinutes(30)).toBe('30 min');
    expect(fmtMinutes(120)).toBe('2 h');
    expect(fmtMinutes(1440)).toBe('1.0 d');
    expect(fmtMinutes(undefined)).toBe('—');
  });

  it('describes deadlines in the future and overdue ones', () => {
    const future = new Date(Date.now() + 25 * 60_000 + 30_000).toISOString();
    const past = new Date(Date.now() - 65 * 60_000 - 30_000).toISOString();
    expect(fmtCountdown(future)).toEqual({ label: 'in 25m', overdue: false });
    expect(fmtCountdown(past)).toEqual({ label: 'overdue 1h 5m', overdue: true });
  });
});

describe('toQuery', () => {
  it('drops empty values and repeats array parameters', () => {
    expect(toQuery({ q: 'vpn', status: ['NEW', 'ASSIGNED'], empty: '', missing: undefined, page: 0 }))
      .toBe('?q=vpn&status=NEW&status=ASSIGNED&page=0');
    expect(toQuery({})).toBe('');
  });
});
