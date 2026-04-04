(function () {
  const androidBridge = window.AndroidBridge;

  function log(message) {
    console.log(`[Crimehunter] ${message}`);
    if (androidBridge?.log) {
      androidBridge.log(String(message));
    }
  }

  function getLanguage() {
    const language = (navigator.language || "en").split("-")[0];
    return language || "en";
  }

  function getUrlParam(name) {
    try {
      return new URLSearchParams(window.location.search).get(name) || "";
    } catch (error) {
      console.warn("Could not parse URL parameter", name, error);
      return "";
    }
  }

  window.commercialBreak = function commercialBreak() {
    log("commercialBreak requested; no ad provider attached in Android build.");
    return Promise.resolve(false);
  };

  window.rewardedBreak = function rewardedBreak() {
    log("rewardedBreak requested; rewarded ads are disabled in Android build.");
    return Promise.resolve(false);
  };

  window.shareableURL = function shareableURL(payload) {
    if (androidBridge?.shareText) {
      androidBridge.shareText(
        payload?.title || "Crimehunter",
        payload?.text || "",
        payload?.url || window.location.href,
      );
      return Promise.resolve(payload?.url || window.location.href);
    }

    return Promise.resolve(window.location.href);
  };

  window.initPokiBridge = function initPokiBridge(name) {
    log(`Initializing Poki bridge as ${name}`);
    if (name) {
      window[name] = window.PokiSDK;
    }
  };

  window.PokiSDK = {
    init() {
      log("PokiSDK.init()");
      return Promise.resolve();
    },
    setDebug() {},
    setLogging() {},
    enableEventTracking() {},
    gameLoadingStart() {
      log("PokiSDK.gameLoadingStart()");
    },
    gameLoadingFinished() {
      log("PokiSDK.gameLoadingFinished()");
    },
    gameplayStart() {},
    gameplayStop() {},
    commercialBreak: window.commercialBreak,
    rewardedBreak: window.rewardedBreak,
    displayAd(container) {
      if (container instanceof HTMLElement) {
        container.style.display = "none";
      }
    },
    destroyAd(container) {
      if (container instanceof HTMLElement) {
        container.remove();
      }
    },
    customEvent(noun, verb, data) {
      log(`customEvent ${noun}:${verb} ${JSON.stringify(data || {})}`);
    },
    getLanguage,
    getURLParam: getUrlParam,
    isAdBlocked() {
      return true;
    },
    logError(error) {
      console.error("[Crimehunter][PokiSDK]", error);
    },
    shareableURL: window.shareableURL,
    measure() {},
    sendHighscore() {},
    setPlayerAge() {},
  };
})();

