(() => {
  const STORAGE_KEY = "hpAdventure:v1";
  const PASSWORD_KEY = "hpAdventure:password";
  const node = document.getElementById("app");

  if (!node || !window.Elm || !window.Elm.Main) {
    return;
  }

  // Load stored password and merge into flags
  const storedPassword = window.localStorage.getItem(PASSWORD_KEY) || "";

  let flags = null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (raw) {
    try {
      flags = JSON.parse(raw);
      // Inject stored password into flags
      flags.passwordInput = storedPassword;
    } catch (error) {
      console.warn("Failed to parse saved state", error);
    }
  }

  // If no flags but we have a password, create minimal flags
  if (!flags && storedPassword) {
    flags = { passwordInput: storedPassword };
  }

  const app = window.Elm.Main.init({ node, flags });

  // Get current password from storage (updated when savePassword is called)
  const getPassword = () => window.localStorage.getItem(PASSWORD_KEY) || "";

  if (app.ports && app.ports.saveState) {
    app.ports.saveState.subscribe((state) => {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    });
  }

  if (app.ports && app.ports.savePassword) {
    app.ports.savePassword.subscribe((password) => {
      if (password) {
        window.localStorage.setItem(PASSWORD_KEY, password);
      }
    });
  }

  if (app.ports && app.ports.clearState) {
    app.ports.clearState.subscribe(() => {
      window.localStorage.removeItem(STORAGE_KEY);
      window.localStorage.removeItem(PASSWORD_KEY);
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

  let ttsController = null;
  let ttsAudio = null;
  let ttsObjectUrl = null;
  let ttsRequestId = 0;

  const ensureAudio = () => {
    if (!ttsAudio) {
      ttsAudio = new Audio();
      ttsAudio.autoplay = true;
    }
    return ttsAudio;
  };

  const stopTts = () => {
    if (ttsController) {
      ttsController.abort();
      ttsController = null;
    }
    if (ttsAudio) {
      ttsAudio.pause();
      ttsAudio.src = "";
    }
    if (ttsObjectUrl) {
      URL.revokeObjectURL(ttsObjectUrl);
      ttsObjectUrl = null;
    }
  };

  const logTtsError = async (response) => {
    try {
      const payload = await response.json();
      console.warn("TTS failed", payload);
    } catch (_) {
      console.warn("TTS failed with status", response.status);
    }
  };

  const playTts = async (text) => {
    if (!text || !text.trim()) {
      return;
    }

    stopTts();
    ttsRequestId += 1;
    const requestId = ttsRequestId;
    const controller = new AbortController();
    ttsController = controller;

    let response = null;
    try {
      response = await fetch("/api/tts", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "audio/mpeg",
          "X-App-Password": getPassword()
        },
        body: JSON.stringify({ text }),
        signal: controller.signal
      });
    } catch (error) {
      if (error && error.name === "AbortError") {
        return;
      }
      console.warn("TTS request failed", error);
      return;
    }

    if (requestId !== ttsRequestId) {
      return;
    }

    if (!response.ok) {
      await logTtsError(response);
      return;
    }

    const audio = ensureAudio();
    const canStream =
      typeof MediaSource !== "undefined" && MediaSource.isTypeSupported && MediaSource.isTypeSupported("audio/mpeg");

    if (!response.body || !canStream) {
      try {
        const blob = await response.blob();
        if (requestId !== ttsRequestId) {
          return;
        }
        if (ttsObjectUrl) {
          URL.revokeObjectURL(ttsObjectUrl);
        }
        ttsObjectUrl = URL.createObjectURL(blob);
        audio.src = ttsObjectUrl;
        audio.currentTime = 0;
        audio.play().catch(() => {});
      } catch (error) {
        console.warn("TTS playback failed", error);
      }
      return;
    }

    const mediaSource = new MediaSource();
    ttsObjectUrl = URL.createObjectURL(mediaSource);
    audio.src = ttsObjectUrl;
    audio.currentTime = 0;
    audio.play().catch(() => {});

    const sourceOpen = new Promise((resolve, reject) => {
      mediaSource.addEventListener("sourceopen", resolve, { once: true });
      mediaSource.addEventListener("error", reject, { once: true });
    });

    try {
      await sourceOpen;
    } catch (error) {
      console.warn("TTS source failed", error);
      return;
    }

    if (requestId !== ttsRequestId) {
      return;
    }

    let sourceBuffer = null;
    try {
      sourceBuffer = mediaSource.addSourceBuffer("audio/mpeg");
    } catch (error) {
      console.warn("TTS codec not supported", error);
      return;
    }

    const queue = [];
    let done = false;

    const maybeFlush = () => {
      if (sourceBuffer.updating || queue.length > 0) {
        return;
      }
      if (done && mediaSource.readyState === "open") {
        try {
          mediaSource.endOfStream();
        } catch (_) {}
      }
    };

    sourceBuffer.addEventListener("updateend", () => {
      if (queue.length > 0 && !sourceBuffer.updating) {
        sourceBuffer.appendBuffer(queue.shift());
      } else {
        maybeFlush();
      }
    });

    const reader = response.body.getReader();
    try {
      while (true) {
        const { value, done: streamDone } = await reader.read();
        if (requestId !== ttsRequestId) {
          return;
        }
        if (streamDone) {
          done = true;
          break;
        }
        if (value && value.byteLength > 0) {
          if (sourceBuffer.updating || queue.length > 0) {
            queue.push(value);
          } else {
            sourceBuffer.appendBuffer(value);
          }
        }
      }
    } catch (error) {
      if (error && error.name === "AbortError") {
        return;
      }
      console.warn("TTS stream interrupted", error);
    } finally {
      done = true;
      maybeFlush();
    }
  };

  if (app.ports && app.ports.speakStory) {
    app.ports.speakStory.subscribe((text) => {
      playTts(text);
    });
  }

  if (app.ports && app.ports.startStoryStream && app.ports.storyStream) {
    let activeController = null;
    let receivedFinal = false;
    let receivedImage = false;

    const sendEvent = (event, data) => {
      // Deduplicate: only allow one final/final_text and one image per stream
      if ((event === "final_text" || event === "final") && receivedFinal) {
        console.warn("Duplicate final event skipped:", event);
        return;
      }
      if (event === "image" && receivedImage) {
        console.warn("Duplicate image event skipped");
        return;
      }

      if (event === "final_text" || event === "final") {
        receivedFinal = true;
      }
      if (event === "image") {
        receivedImage = true;
      }

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
          headers: {
            "Content-Type": "application/json",
            "X-App-Password": getPassword()
          },
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

      // Reset deduplication flags for new stream
      receivedFinal = false;
      receivedImage = false;

      stopTts();

      const controller = new AbortController();
      activeController = controller;

      try {
        const response = await fetch("/api/story/stream", {
          method: "POST",
          headers: {
            Accept: "text/event-stream",
            "Content-Type": "application/json",
            "X-App-Password": getPassword()
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
