import { render, screen } from '@testing-library/react';
import { AnalyticsDashboard } from './index';

describe('AnalyticsDashboard', () => {
  it('renders the heading', () => {
    render(<AnalyticsDashboard />);
    expect(screen.getByText('Analytics')).toBeInTheDocument();
  });
});
