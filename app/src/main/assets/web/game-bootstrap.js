(function () {
  const canvas = document.getElementById("unity-canvas");
  const loadingOverlay = document.getElementById("loadingOverlay");
  const errorOverlay = document.getElementById("errorOverlay");
  const statusText = document.getElementById("statusText");
  const errorText = document.getElementById("errorText");

  function setStatus(message) {
    if (statusText) {
      statusText.textContent = message;
    }
  }

  function showError(message, detail) {
    loadingOverlay?.classList.add("hidden");
    errorOverlay?.classList.remove("hidden");
    if (errorText) {
      const parts = [message, detail].filter(Boolean);
      errorText.textContent = parts.join(" ");
    }
  }

  function normalizeError(error) {
    if (!error) return "";
    if (typeof error === "string") return error;
    if (error.message) return error.message;
    try {
      return JSON.stringify(error);
    } catch (_) {
      return String(error);
    }
  }

  function describeLaunchError(rawMessage) {
    const message = normalizeError(rawMessage);
    if (message.includes("build.wasm")) {
      return "Missing build.wasm. Add the original Unity WebAssembly file to app/src/main/assets/web/build.wasm and rebuild the app.";
    }
    return message || "The game runtime failed to start.";
  }

  function configureCanvas() {
    canvas?.focus({ preventScroll: true });
  }

  async function startGame() {
    if (typeof unityFramework !== "function") {
      showError("Unity runtime was not extracted correctly.", "The unityFramework function is unavailable.");
      return;
    }

    setStatus("Starting Unity runtime…");
    configureCanvas();

    try {
      const module = await unityFramework({
        canvas,
        companyName: "Gowda Games",
        productName: "Crimehunter",
        productVersion: "1.0.0",
        streamingAssetsUrl: "StreamingAssets",
        locateFile: (path) => path,
        setStatus,
        print: (message) => console.log(message),
        printErr: (message) => console.error(message),
        onRuntimeInitialized: () => {
          setStatus("Finalizing game startup…");
        },
      });

      window.gameModule = module;
      loadingOverlay?.classList.add("hidden");
      canvas?.focus({ preventScroll: true });
    } catch (error) {
      console.error("Game launch failed", error);
      showError("Unable to launch the game.", describeLaunchError(error));
    }
  }

  window.addEventListener("error", (event) => {
    console.error("Unhandled page error", event.error || event.message);
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      canvas?.focus({ preventScroll: true });
    }
  });

  startGame();
})();

