import TolgeeContextProvider from "@components/Tolgee/Tolgee";
import { IntercomProvider } from "@services/intercom";
import { useEffect } from "react";
import { Provider } from "react-redux";

import { CONFIG } from "./config";
import AppRoutes from "./routes/AppRoutes";
import { store } from "./store";
import { loadGoogleMapsScript } from "./utils/loadGoogleMapsScript";

export const App: React.FC = () => {
  useEffect(() => {
    loadGoogleMapsScript(CONFIG.GM_KEY ?? "");
  }, []);

  return (
    <Provider store={store}>
      <IntercomProvider>
        <TolgeeContextProvider>
          <AppRoutes />
        </TolgeeContextProvider>
      </IntercomProvider>
    </Provider>
  );
};

export default App;
