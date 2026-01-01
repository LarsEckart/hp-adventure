(() => {
  const STORAGE_KEY = "hpAdventure:v1";
  const node = document.getElementById("app");

  if (!node || !window.Elm || !window.Elm.Main) {
    return;
  }

  let flags = null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw) {
    try {
      flags = JSON.parse(raw);
    } catch (error) {
      console.warn("Failed to parse saved state", error);
    }
  }

  const app = window.Elm.Main.init({ node, flags });

  if (app.ports && app.ports.saveState) {
    app.ports.saveState.subscribe((state) => {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    });
  }

  if (app.ports && app.ports.onlineStatus) {
    const reportStatus = () => {
      const isOnline = navigator.onLine !== false;
      app.ports.onlineStatus.send(isOnline);
    };

    reportStatus();
    window.addEventListener("online", reportStatus);
    window.addEventListener("offline", reportStatus);
  }

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("/sw.js").catch((error) => {
        console.warn("Service worker registration failed", error);
      });
    });
  }
})();
