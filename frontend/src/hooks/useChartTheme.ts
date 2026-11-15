import { useEffect, useState } from 'react';
import { SERIES_DARK, SERIES_LIGHT } from '../lib/format';

/** Chart colours selected per theme (dark steps are their own validated values, not an automatic flip). */
export function useChartTheme() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));
  useEffect(() => {
    const observer = new MutationObserver(() => setDark(document.documentElement.classList.contains('dark')));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    return () => observer.disconnect();
  }, []);
  return {
    series: dark ? SERIES_DARK : SERIES_LIGHT,
    grid: dark ? '#26324a' : '#e8edf3',
    axis: dark ? '#94a3b8' : '#64748b',
    surface: dark ? '#0f172a' : '#ffffff',
    text: dark ? '#e2e8f0' : '#0f172a',
  };
}
