import { describe, it, expect } from 'vitest';
import { formatCurrency, formatPercent } from '../utils/format';

describe('formatCurrency', () => {
  it('formats small amounts as dollar values', () => {
    expect(formatCurrency(500)).toBe('$500');
  });

  it('formats thousands with K suffix', () => {
    expect(formatCurrency(12_500)).toBe('$12.5K');
  });

  it('formats millions with M suffix', () => {
    expect(formatCurrency(2_500_000)).toBe('$2.50M');
  });

  it('formats zero correctly', () => {
    expect(formatCurrency(0)).toBe('$0');
  });
});

describe('formatPercent', () => {
  it('formats to one decimal place', () => {
    expect(formatPercent(95.678)).toBe('95.7%');
  });

  it('handles 100%', () => {
    expect(formatPercent(100)).toBe('100.0%');
  });
});
