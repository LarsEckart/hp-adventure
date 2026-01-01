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

  if (app.ports && app.ports.clearState) {
    app.ports.clearState.subscribe(() => {
      window.localStorage.removeItem(STORAGE_KEY);
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

  if (app.ports && app.ports.startStoryStream && app.ports.storyStream) {
    let activeController = null;

    const sendEvent = (event, data) => {
      app.ports.storyStream.send({ event, data });
    };

    const sendError = (message, code = "STREAM_CLIENT_ERROR") => {
      sendEvent("error", { error: { code, message, requestId: null } });
    };

    const parseEventData = (data) => {
      if (!data) {
        return data;
      }
      try {
        return JSON.parse(data);
      } catch (_) {
        return data;
      }
    };

    const handleSseChunk = (chunk) => {
      if (!chunk) {
        return;
      }

      let event = "message";
      const dataLines = [];
      const lines = chunk.split("\n");
      for (const line of lines) {
        if (line.startsWith("event:")) {
          event = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
          dataLines.push(line.slice(5).trim());
        }
      }

      const data = dataLines.join("\n");
      if (!data) {
        return;
      }

      sendEvent(event, parseEventData(data));
    };

    const streamResponse = async (response) => {
      if (!response.ok) {
        let errorPayload = null;
        try {
          errorPayload = await response.json();
        } catch (_) {
          errorPayload = { error: { code: "STREAM_HTTP_ERROR", message: "Streaming fehlgeschlagen." } };
        }
        sendEvent("error", errorPayload);
        return false;
      }

      if (!response.body || !window.ReadableStream) {
        return false;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split("\n\n");
        buffer = parts.pop() || "";
        for (const part of parts) {
          handleSseChunk(part.trim());
        }
      }

      if (buffer.trim()) {
        handleSseChunk(buffer.trim());
      }

      return true;
    };

    const fallbackToJson = async (payload) => {
      try {
        const response = await fetch("/api/story", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        const data = await response.json();
        if (!response.ok) {
          sendEvent("error", data);
          return;
        }
        sendEvent("final", data);
      } catch (error) {
        sendError("Netzwerkfehler beim Laden der Geschichte.");
      }
    };

    app.ports.startStoryStream.subscribe(async (payload) => {
      if (activeController) {
        activeController.abort();
      }

      const controller = new AbortController();
      activeController = controller;

      try {
        const response = await fetch("/api/story/stream", {
          method: "POST",
          headers: {
            Accept: "text/event-stream",
            "Content-Type": "application/json"
          },
          body: JSON.stringify(payload),
          signal: controller.signal
        });

        const streamed = await streamResponse(response);
        if (!streamed) {
          await fallbackToJson(payload);
        }
      } catch (error) {
        if (error && error.name === "AbortError") {
          return;
        }
        await fallbackToJson(payload);
      } finally {
        if (activeController === controller) {
          activeController = null;
        }
      }
    });
  }

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("/sw.js").catch((error) => {
        console.warn("Service worker registration failed", error);
      });
    });
  }
})();
