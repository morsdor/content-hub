import { render, screen } from '@testing-library/react';
import { MediaUpload } from './index';

describe('MediaUpload', () => {
  it('renders the heading', () => {
    render(<MediaUpload />);
    expect(screen.getByText('Media Upload')).toBeInTheDocument();
  });
});
