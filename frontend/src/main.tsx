import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { setGlobalTheme } from '@atlaskit/tokens';
import { store } from './store';
import { CognitoAuthProvider } from './auth/CognitoAuthProvider';
import App from './App';
import './styles/theme.css';

setGlobalTheme({ colorMode: 'light', spacing: 'spacing' });

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <CognitoAuthProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </CognitoAuthProvider>
    </Provider>
  </React.StrictMode>,
);
