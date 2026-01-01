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
})();
