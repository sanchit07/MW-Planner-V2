import { InfoIcon } from "lucide-react";
import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { ToastContainer } from "react-toastify";

import App from "./App";
import { ErrorBoundary } from "./components/ErrorBoundary";

import "./styles/tailwind.css";
import "./styles/notification.css";
import "./styles/global.sass";

export function bootstrap(): void {
  const rootElement = document.getElementById("root");

  if (!rootElement) {
    throw new Error('Root element with id "root" not found');
  }

  const root = createRoot(rootElement);

  root.render(
    <React.StrictMode>
      <ErrorBoundary>
        <BrowserRouter basename={import.meta.env.BASE_URL}>
          <App />
          <ToastContainer
            position="top-right"
            autoClose={3000}
            hideProgressBar={true}
            newestOnTop={false}
            closeOnClick
            rtl={false}
            pauseOnFocusLoss
            draggable
            pauseOnHover
            theme="light"
            toastClassName="mb-2"
            icon={<InfoIcon className="w-5 h-5" />}
          />
        </BrowserRouter>
      </ErrorBoundary>
    </React.StrictMode>,
  );
}

if (!import.meta.env.VITEST) {
  bootstrap();
}
