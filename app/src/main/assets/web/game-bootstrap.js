(function () {
  const canvas = document.getElementById("unity-canvas");
  const loadingOverlay = document.getElementById("loadingOverlay");
  const errorOverlay = document.getElementById("errorOverlay");
  const statusText = document.getElementById("statusText");
  const progressValue = document.getElementById("progressValue");
  const errorTitle = document.getElementById("errorTitle");
  const errorText = document.getElementById("errorText");
  const activeIntervals = new Map();
  let maxRunDependencies = 0;

  function detectOs() {
    const userAgent = navigator.userAgent || "";
    if (/Android/i.test(userAgent)) return "Android";
    if (/iPhone|iPad|iPod/i.test(userAgent)) return "iOS";
    if (/Windows/i.test(userAgent)) return "Windows";
    if (/Mac OS X/i.test(userAgent)) return "macOS";
    if (/Linux/i.test(userAgent)) return "Linux";
    return "Unknown";
  }

  function buildSystemInfo() {
    return {
      width: window.innerWidth,
      height: window.innerHeight,
      os: detectOs(),
      osVersion: navigator.userAgent || "Unknown",
      language: navigator.language || "en",
      gpu: "Unknown GPU",
      hasWebGL: true,
      hasCursorLock: "pointerLockElement" in document,
      hasFullscreen: !!document.fullscreenEnabled,
    };
  }

  function setStatus(message) {
    if (statusText) {
      statusText.textContent = message;
    }
  }

  function setProgress(value) {
    if (!progressValue) return;

    if (typeof value === "number" && Number.isFinite(value)) {
      progressValue.classList.remove("progress-indeterminate");
      progressValue.style.width = `${Math.max(0, Math.min(100, value))}%`;
      return;
    }

    progressValue.classList.add("progress-indeterminate");
    progressValue.style.width = "";
  }

  function showError(title, detail) {
    loadingOverlay?.classList.add("hidden");
    errorOverlay?.classList.remove("hidden");

    if (errorTitle) {
      errorTitle.textContent = title || "Game could not start";
    }

    if (errorText) {
      errorText.textContent = detail || "The game runtime failed to start.";
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

  function trackInterval(callback, delay) {
    const id = window.setInterval(callback, delay);
    activeIntervals.set(id, true);
    return id;
  }

  function clearTrackedInterval(id) {
    if (id == null) return;
    window.clearInterval(id);
    activeIntervals.delete(id);
  }

  function cleanupRuntime(deinitializers) {
    for (const id of activeIntervals.keys()) {
      window.clearInterval(id);
    }
    activeIntervals.clear();

    while (deinitializers.length) {
      const callback = deinitializers.pop();
      try {
        callback?.();
      } catch (error) {
        console.warn("Runtime cleanup failed", error);
      }
    }
  }

  async function fetchWithProgress(resource, init = {}) {
    const { onProgress, ...requestInit } = init;
    const response = await fetch(resource, requestInit);
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} while fetching ${resource}`);
    }

    if (!response.body) {
      const parsedBody = new Uint8Array(await response.arrayBuffer());
      return {
        headers: response.headers,
        parsedBody,
        status: response.status,
        url: response.url,
      };
    }

    const reader = response.body.getReader();
    const total = Number(response.headers.get("content-length")) || 0;
    const chunks = [];
    let loaded = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!value) continue;

      loaded += value.byteLength;
      chunks.push(value);

      if (onProgress) {
        onProgress({
          chunk: value,
          lengthComputable: total > 0,
          loaded,
          response,
          total,
        });
      }
    }

    const parsedBody = new Uint8Array(loaded);
    let offset = 0;
    for (const chunk of chunks) {
      parsedBody.set(chunk, offset);
      offset += chunk.byteLength;
    }

    return {
      headers: response.headers,
      parsedBody,
      status: response.status,
      url: response.url,
    };
  }

  function createModuleConfig() {
    const deinitializers = [];

    return {
      QuitCleanup: () => cleanupRuntime(deinitializers),
      SystemInfo: buildSystemInfo(),
      canvas,
      clearInterval: clearTrackedInterval,
      companyName: "Gowda Games",
      deinitializers,
      devicePixelRatio: window.devicePixelRatio || 1,
      fetchWithProgress,
      locateFile: (path) => path,
      matchWebGLToCanvasSize: true,
      monitorRunDependencies: (remaining) => {
        if (remaining > maxRunDependencies) {
          maxRunDependencies = remaining;
        }

        if (remaining > 0 && maxRunDependencies > 0) {
          const completed = maxRunDependencies - remaining;
          const percent = (completed / maxRunDependencies) * 100;
          setProgress(percent);
          setStatus(`Loading game data (${completed}/${maxRunDependencies})...`);
        } else {
          setProgress(100);
          setStatus("Finalizing game startup...");
        }
      },
      postRun: [],
      preRun: [],
      preloadPlugins: [],
      print: (message) => console.log(message),
      printErr: (message) => console.error(message),
      productName: "Crimehunter",
      productVersion: "1.0.0",
      setInterval: trackInterval,
      setStatus,
      streamingAssetsUrl: "StreamingAssets",
      webglContextAttributes: {
        alpha: false,
        antialias: true,
        depth: true,
        powerPreference: "high-performance",
        premultipliedAlpha: false,
        preserveDrawingBuffer: false,
        stencil: true,
      },
      onRuntimeInitialized: () => {
        setStatus("Finalizing game startup...");
        setProgress(100);
      },
    };
  }

  async function startGame() {
    if (typeof unityFramework !== "function") {
      showError("Game runtime missing", "The Unity runtime could not be loaded from the bundled game files.");
      return;
    }

    setStatus("Starting Unity runtime...");
    setProgress();
    configureCanvas();
    window.PokiSDK?.gameLoadingStart?.();

    try {
      const module = await unityFramework(createModuleConfig());

      window.gameModule = module;
      loadingOverlay?.classList.add("hidden");
      canvas?.focus({ preventScroll: true });
      window.PokiSDK?.gameLoadingFinished?.();
    } catch (error) {
      console.error("Game launch failed", error);
      showError("Game could not start", describeLaunchError(error));
    }
  }

  window.addEventListener("error", (event) => {
    console.error("Unhandled page error", event.error || event.message);
  });

  window.addEventListener("resize", () => {
    if (window.gameModule?.SystemInfo) {
      window.gameModule.SystemInfo.width = window.innerWidth;
      window.gameModule.SystemInfo.height = window.innerHeight;
    }
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      canvas?.focus({ preventScroll: true });
    }
  });

  startGame();
})();
