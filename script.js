const STORAGE_KEYS = {
  search: "busgo_search",
  trips: "busgo_trips",
  trip: "busgo_trip",
  booking: "busgo_booking",
  user: "busgo_user",
  tickets: "busgo_tickets",
  adminTrips: "busgo_admin_trips",
  redirectAfterLogin: "busgo_redirect_after_login",
  passengersDraft: "busgo_passengers_draft",
  token: "busgo_token",
  chatbot: "busgo_chatbot",
};

const CITY_OPTIONS = ["Istanbul", "Ankara", "Izmir", "Bursa", "Antalya", "Eskisehir"];
const GENDER_ICONS = { male: "♂", female: "♀" };
const SERVICE_ICONS = ["📶 WiFi", "🔌 USB", "📺 TV"];
const ROWS = 10;
const COLS = 4;
const VIP_ROWS = [1];
const VIP_SURCHARGE = 0.4;
const OVERWEIGHT_THRESHOLD = 30;
const OVERWEIGHT_FEE_PER_KG = 2;
const TC_REGEX = /^\d{11}$/;
const API_BASE = `${window.location.protocol}//${window.location.hostname}:8080/api`;
const CHATBOT_DISABLED_PAGES = new Set(["admin", "admin-login"]);
const CHATBOT_MAX_MESSAGES = 40;
const CHATBOT_MONTHS = {
  ocak: 0,
  subat: 1,
  mart: 2,
  nisan: 3,
  mayis: 4,
  haziran: 5,
  temmuz: 6,
  agustos: 7,
  eylul: 8,
  ekim: 9,
  kasim: 10,
  aralik: 11,
};

function sanitizeTc(value) {
  return String(value || "").replace(/\D/g, "").slice(0, 11);
}

document.addEventListener("DOMContentLoaded", () => {
  initApp().catch((error) => {
    console.error(error);
    showToast("App initialization failed. Please refresh.", "error");
  });
});

async function initApp() {
  const page = document.body.dataset.page;
  await syncCurrentUser();
  renderAuthNav();
  initScrollReveal();

  if (page === "search") {
    await initSearchPage();
    initLandingEnhancements();
  } else if (page === "trips") {
    await initTripsPage();
  } else if (page === "passenger") {
    initPassengerPage();
  } else if (page === "seats") {
    await initSeatsPage();
  } else if (page === "cart") {
    initCartPage();
  } else if (page === "payment") {
    await initPaymentPage();
  } else if (page === "ticket") {
    await initTicketPage();
  } else if (page === "login" || page === "customer-login") {
    initCustomerLoginPage();
  } else if (page === "admin-login") {
    initAdminLoginPage();
  } else if (page === "register") {
    initRegisterPage();
  } else if (page === "profile") {
    await initProfilePage();
  } else if (page === "admin") {
    await initAdminPage();
  }

  await initChatbot(page);
}

async function initChatbot(page) {
  if (CHATBOT_DISABLED_PAGES.has(page)) return;

  const state = normalizeChatbotState(readJson(STORAGE_KEYS.chatbot));
  const cityIndex = buildChatbotCityIndex(await loadCities());
  document.body.classList.add("has-chatbot");

  const shell = document.createElement("section");
  shell.className = "chatbot-shell";
  shell.innerHTML = `
    <button
      type="button"
      class="chatbot-fab"
      data-chatbot-toggle
      aria-controls="chatbotPanel"
      aria-expanded="false"
    >
      Trip Bot
    </button>
    <section class="chatbot-panel" id="chatbotPanel" data-chatbot-panel aria-hidden="true">
      <header class="chatbot-header">
        <div>
          <strong>BusGo Assistant</strong>
          <p>Sefer, tarih ve rota sorabilirsin.</p>
        </div>
        <div class="chatbot-header-actions">
          <button type="button" class="chatbot-text-btn" data-chatbot-reset>Yeni</button>
          <button type="button" class="chatbot-close-btn" data-chatbot-close aria-label="Sohbeti kapat">×</button>
        </div>
      </header>
      <div class="chatbot-messages" data-chatbot-messages aria-live="polite"></div>
      <form class="chatbot-form" data-chatbot-form>
        <input
          type="text"
          class="chatbot-input"
          data-chatbot-input
          placeholder="Orn: yarin Istanbul'dan Ankara'ya sefer var mi?"
          autocomplete="off"
        />
        <button type="submit" class="btn btn-primary chatbot-send" data-chatbot-send>Gonder</button>
      </form>
    </section>
  `;
  document.body.appendChild(shell);

  const toggle = shell.querySelector("[data-chatbot-toggle]");
  const panel = shell.querySelector("[data-chatbot-panel]");
  const closeBtn = shell.querySelector("[data-chatbot-close]");
  const resetBtn = shell.querySelector("[data-chatbot-reset]");
  const messagesEl = shell.querySelector("[data-chatbot-messages]");
  const form = shell.querySelector("[data-chatbot-form]");
  const input = shell.querySelector("[data-chatbot-input]");
  const sendBtn = shell.querySelector("[data-chatbot-send]");
  if (!toggle || !panel || !closeBtn || !resetBtn || !messagesEl || !form || !input || !sendBtn) return;

  const persist = () => writeJson(STORAGE_KEYS.chatbot, state);
  const render = () => renderChatbotMessages(messagesEl, state);
  const sync = () => {
    shell.classList.toggle("open", state.open);
    toggle.setAttribute("aria-expanded", String(state.open));
    panel.setAttribute("aria-hidden", String(!state.open));
    if (state.open) {
      scrollChatbotToBottom(messagesEl);
      window.requestAnimationFrame(() => input.focus());
    }
  };

  render();
  sync();

  toggle.addEventListener("click", () => {
    state.open = !state.open;
    persist();
    sync();
  });

  closeBtn.addEventListener("click", () => {
    state.open = false;
    persist();
    sync();
  });

  resetBtn.addEventListener("click", () => {
    resetChatbotState(state);
    state.open = true;
    persist();
    render();
    sync();
  });

  messagesEl.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const action = target.closest("[data-chatbot-search]");
    if (!action) return;
    const from = String(action.getAttribute("data-from") || "");
    const to = String(action.getAttribute("data-to") || "");
    const date = String(action.getAttribute("data-date") || "");
    if (!from || !to || !date) return;
    writeJson(STORAGE_KEYS.search, { from, to, date, ticketCount: 1 });
    localStorage.removeItem(STORAGE_KEYS.trip);
    localStorage.removeItem(STORAGE_KEYS.passengersDraft);
    redirect("trips.html");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const raw = String(input.value || "").trim();
    if (!raw) return;

    input.value = "";
    input.disabled = true;
    sendBtn.disabled = true;
    state.open = true;
    appendChatbotMessage(state, { role: "user", text: raw });
    const loadingId = appendChatbotMessage(state, { role: "bot", loading: true });
    persist();
    render();
    sync();

    try {
      const reply = await buildChatbotReply(raw, state, cityIndex);
      replaceChatbotMessage(state, loadingId, reply);
    } catch (error) {
      console.error(error);
      replaceChatbotMessage(state, loadingId, {
        role: "bot",
        text: error?.message || "Su anda arama yapilamadi. Biraz sonra tekrar deneyin.",
      });
    } finally {
      input.disabled = false;
      sendBtn.disabled = false;
      persist();
      render();
      sync();
    }
  });
}

function normalizeChatbotState(value) {
  const initial = createInitialChatbotState();
  if (!value || typeof value !== "object") return initial;
  const messages = Array.isArray(value.messages)
    ? value.messages
        .filter((message) => message && typeof message === "object" && !message.loading)
        .map((message) => ({
          id: String(message.id || `chat-${crypto.randomUUID?.() || Date.now()}`),
          role: message.role === "user" ? "user" : "bot",
          text: String(message.text || ""),
          cards: Array.isArray(message.cards) ? message.cards : [],
          searchAction: message.searchAction || null,
        }))
        .slice(-CHATBOT_MAX_MESSAGES)
    : [];
  return {
    open: Boolean(value.open),
    messages: messages.length ? messages : initial.messages,
    pendingSearch:
      value.pendingSearch && typeof value.pendingSearch === "object"
        ? {
            from: value.pendingSearch.from || null,
            to: value.pendingSearch.to || null,
            date: value.pendingSearch.date || null,
            preferNearest: Boolean(value.pendingSearch.preferNearest),
          }
        : null,
    awaitingField:
      value.awaitingField === "from" || value.awaitingField === "to" || value.awaitingField === "date"
        ? value.awaitingField
        : null,
    lastSearch:
      value.lastSearch && typeof value.lastSearch === "object"
        ? {
            from: value.lastSearch.from || null,
            to: value.lastSearch.to || null,
            date: value.lastSearch.date || null,
            preferNearest: Boolean(value.lastSearch.preferNearest),
          }
        : null,
  };
}

function createInitialChatbotState() {
  return {
    open: false,
    messages: [
      {
        id: "chatbot-welcome",
        role: "bot",
        text: 'Merhaba. Sefer aramasi yapabilirim. Ornek: "yarin Istanbul\'dan Ankara\'ya sefer var mi?"',
      },
    ],
    pendingSearch: null,
    awaitingField: null,
    lastSearch: null,
  };
}

function resetChatbotState(state) {
  const initial = createInitialChatbotState();
  state.messages = initial.messages;
  state.pendingSearch = null;
  state.awaitingField = null;
  state.lastSearch = null;
}

function appendChatbotMessage(state, message) {
  const entry = {
    id: message.id || `chat-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role: message.role === "user" ? "user" : "bot",
    text: message.text || "",
    cards: Array.isArray(message.cards) ? message.cards : [],
    searchAction: message.searchAction || null,
    loading: Boolean(message.loading),
  };
  state.messages = [...state.messages, entry].slice(-CHATBOT_MAX_MESSAGES);
  return entry.id;
}

function replaceChatbotMessage(state, id, replacement) {
  state.messages = state.messages.map((message) =>
    message.id === id
      ? {
          id,
          role: replacement.role === "user" ? "user" : "bot",
          text: replacement.text || "",
          cards: Array.isArray(replacement.cards) ? replacement.cards : [],
          searchAction: replacement.searchAction || null,
        }
      : message
  );
}

function renderChatbotMessages(container, state) {
  container.innerHTML = state.messages.map(renderChatbotMessage).join("");
  scrollChatbotToBottom(container);
}

function renderChatbotMessage(message) {
  const bubble =
    message.loading
      ? `
        <div class="chatbot-bubble chatbot-loading" aria-label="Yukleniyor">
          <span></span><span></span><span></span>
        </div>
      `
      : `
        <div class="chatbot-bubble">
          <div class="chatbot-text">${renderChatbotText(message.text)}</div>
          ${renderChatbotTrips(message.cards)}
          ${renderChatbotAction(message.searchAction)}
        </div>
      `;
  return `<article class="chatbot-message ${message.role === "user" ? "user" : "bot"}">${bubble}</article>`;
}

function renderChatbotText(text) {
  return escapeHtml(String(text || "")).replaceAll("\n", "<br />");
}

function renderChatbotTrips(cards) {
  if (!Array.isArray(cards) || !cards.length) return "";
  return `
    <div class="chatbot-trip-list">
      ${cards
        .map(
          (trip) => `
            <article class="chatbot-trip-card">
              <div class="chatbot-trip-route">${escapeHtml(trip.from)} → ${escapeHtml(trip.to)}</div>
              <div class="chatbot-trip-meta">${escapeHtml(formatChatbotDateLabel(trip.date))} • ${escapeHtml(
                trip.departureTime
              )}</div>
              <div class="chatbot-trip-footer">
                <span>${escapeHtml(trip.company)}</span>
                <strong>${escapeHtml(formatCurrency(trip.basePrice))}</strong>
              </div>
            </article>
          `
        )
        .join("")}
    </div>
  `;
}

function renderChatbotAction(action) {
  if (!action) return "";
  return `
    <button
      type="button"
      class="chatbot-action-btn"
      data-chatbot-search
      data-from="${escapeHtml(action.from)}"
      data-to="${escapeHtml(action.to)}"
      data-date="${escapeHtml(action.date)}"
    >
      ${escapeHtml(action.label || "Seferleri Ac")}
    </button>
  `;
}

function scrollChatbotToBottom(container) {
  container.scrollTop = container.scrollHeight;
}

async function buildChatbotReply(rawMessage, state, cityIndex) {
  const normalized = normalizeChatbotText(rawMessage);
  if (!normalized) {
    return { role: "bot", text: "Mesajini goremedim. Rota ve tarih yazarak tekrar deneyebilirsin." };
  }

  if (/^(selam|merhaba|hey)\b/.test(normalized) && !state.awaitingField) {
    return {
      role: "bot",
      text: 'Hazirim. Ornek: "bugun Bursa\'dan Izmir\'e sefer var mi?" ya da "5 mayis Ankara Antalya".',
    };
  }

  if (/^(temizle|sifirla|yeniden basla|reset)$/.test(normalized)) {
    state.pendingSearch = null;
    state.awaitingField = null;
    state.lastSearch = null;
    return {
      role: "bot",
      text: 'Tamam, yeni aramaya baslayabiliriz. Ornek: "yarin Istanbul\'dan Ankara\'ya sefer var mi?"',
    };
  }

  if (wantsTicketCancellationHelp(normalized)) {
    state.pendingSearch = null;
    state.awaitingField = null;
    return {
      role: "bot",
      text:
        "Bilet iadesi icin Profile sayfasina girip My Tickets bolumundeki ilgili bilette Cancel Ticket butonunu kullanmalisin.\n" +
        "Iade sadece sefer saatine 15 gunden fazla varsa acik olur.\n" +
        "Odeme iyzico ile yapildiysa iade iyzico sandbox uzerinden gonderilir; diger durumda tutar demo bakiyene geri eklenir.\n" +
        "Buton gorunmuyorsa o bilette iade suresi kapanmistir.",
    };
  }

  const parsed = parseChatbotTripMessage(rawMessage, cityIndex, state.awaitingField);
  if (!parsed.hasTripIntent && !state.awaitingField) {
    return {
      role: "bot",
      text: "Benden sefer sorabilirsin. Kalkis, varis ve tarih yazman yeterli.",
    };
  }

  const criteria = mergeChatbotCriteria(parsed, state);
  if (
    criteria.from &&
    criteria.to &&
    normalizeChatbotText(criteria.from) === normalizeChatbotText(criteria.to)
  ) {
    state.pendingSearch = { from: criteria.from, to: null, date: criteria.date, preferNearest: criteria.preferNearest };
    state.awaitingField = "to";
    return {
      role: "bot",
      text: "Kalkis ve varis ayni olamaz. Nereye gitmek istiyorsun?",
    };
  }

  const missingField = nextMissingChatbotField(criteria);
  if (missingField) {
    state.pendingSearch = criteria;
    state.awaitingField = missingField;
    const retryPrefix =
      state.awaitingField === missingField && parsed.mentionedCities.length === 0 && !parsed.date
        ? "Seni tam anlayamadim. "
        : "";
    return {
      role: "bot",
      text: `${retryPrefix}${buildChatbotQuestion(missingField)}`,
    };
  }

  state.pendingSearch = null;
  state.awaitingField = null;
  state.lastSearch = {
    from: criteria.from,
    to: criteria.to,
    date: criteria.date,
    preferNearest: criteria.preferNearest,
  };

  const response = await apiFetch("/trips/chat-search", {
    method: "POST",
    body: JSON.stringify(criteria),
  });
  const trips = Array.isArray(response?.trips) ? response.trips : [];
  const effectiveDate = response?.requestedDate || criteria.date;

  if (!trips.length) {
    if (criteria.preferNearest && !criteria.date) {
      return {
        role: "bot",
        text: `${criteria.from} → ${criteria.to} rotasinda bugunden sonraki en yakin seferi bulamadim.`,
      };
    }
    return {
      role: "bot",
      text: `${formatChatbotDateLabel(criteria.date)} icin ${criteria.from} → ${criteria.to} rotasinda sefer bulamadim. Farkli bir tarih deneyebilirsin.`,
    };
  }

  let text = "";
  if (response.matchType === "route-nearest") {
    text = `${criteria.from} → ${criteria.to} rotasindaki en yakin sefer tarihi ${formatChatbotDateLabel(effectiveDate)}. O gun icin ${trips.length} sefer getirdim.`;
  } else if (response.matchType === "exact") {
    text = `${formatChatbotDateLabel(criteria.date)} icin ${criteria.from} → ${criteria.to} rotasinda ${trips.length} sefer buldum.`;
  } else {
    text = `${formatChatbotDateLabel(criteria.date)} icin direkt sefer bulamadim. En yakin ${trips.length} seferi getirdim.`;
  }

  return {
    role: "bot",
    text,
    cards: trips,
    searchAction: buildChatbotSearchAction({ ...criteria, date: effectiveDate }, trips),
  };
}

function mergeChatbotCriteria(parsed, state) {
  const pending = state.pendingSearch || {};
  const criteria = {
    from: parsed.from || pending.from || null,
    to: parsed.to || pending.to || null,
    date: parsed.date || pending.date || null,
    preferNearest: Boolean(parsed.preferNearest || pending.preferNearest),
  };

  if (state.awaitingField === "from" && !criteria.from) {
    criteria.from = parsed.mentionedCities.find((city) => city !== criteria.to) || null;
  }
  if (state.awaitingField === "to" && !criteria.to) {
    criteria.to = parsed.mentionedCities.find((city) => city !== criteria.from) || null;
  }

  return criteria;
}

function nextMissingChatbotField(criteria) {
  if (!criteria.from) return "from";
  if (!criteria.to) return "to";
  if (criteria.preferNearest && !criteria.date) return null;
  if (!criteria.date) return "date";
  return null;
}

function buildChatbotQuestion(field) {
  if (field === "from") return "Hangi sehirden kalkis yapmak istiyorsun?";
  if (field === "to") return "Hangi sehre gitmek istiyorsun?";
  return 'Hangi tarihte gitmek istiyorsun? Ornek: "bugun", "yarin" ya da "5 mayis".';
}

function buildChatbotSearchAction(criteria, trips) {
  if (!criteria || !Array.isArray(trips) || !trips.length) return null;
  const uniqueDates = [...new Set(trips.map((trip) => trip.date).filter(Boolean))];
  if (uniqueDates.length !== 1) return null;
  return {
    label: "Seferleri Ac",
    from: criteria.from,
    to: criteria.to,
    date: uniqueDates[0],
  };
}

function buildChatbotCityIndex(cities) {
  return [...new Set((Array.isArray(cities) && cities.length ? cities : CITY_OPTIONS).map((city) => String(city || "").trim()).filter(Boolean))]
    .map((city) => ({ canonical: city, normalized: normalizeChatbotToken(city) }))
    .sort((a, b) => b.normalized.length - a.normalized.length);
}

function parseChatbotTripMessage(rawMessage, cityIndex, awaitingField) {
  const normalized = normalizeChatbotText(rawMessage);
  const mentions = extractChatbotCityMentions(normalized, cityIndex);
  const parsedDate = extractChatbotDate(normalized);
  const preferNearest = wantsNearestChatbotTrip(normalized);
  const mentionedCities = [];
  mentions.forEach((mention) => {
    if (!mentionedCities.includes(mention.canonical)) mentionedCities.push(mention.canonical);
  });

  let from = null;
  let to = null;

  mentions.forEach((mention) => {
    if (!from && ["dan", "den", "tan", "ten"].includes(mention.suffix)) from = mention.canonical;
    if (!to && ["a", "e", "ya", "ye"].includes(mention.suffix)) to = mention.canonical;
  });

  if (!from) {
    const byKeyword = cityIndex.find((city) =>
      new RegExp(`\\b(?:kalkis|cikis|nereden|from)\\s+${escapeRegex(city.normalized)}\\b`).test(normalized)
    );
    if (byKeyword) from = byKeyword.canonical;
  }

  if (!to) {
    const byKeyword = cityIndex.find((city) =>
      new RegExp(`\\b(?:varis|hedef|nereye|to)\\s+${escapeRegex(city.normalized)}\\b`).test(normalized)
    );
    if (byKeyword) to = byKeyword.canonical;
  }

  const freeCities = mentionedCities.filter((city) => city !== from && city !== to);
  if (!from && !to && freeCities.length >= 2) {
    [from, to] = freeCities;
  }

  if (!to && freeCities.length && (awaitingField === "to" || /\b(sefer|git|gitmek|gidecegim|gider|bilet|yolculuk|varmi|var)\b/.test(normalized))) {
    to = freeCities.find((city) => city !== from) || null;
  }

  if (!from && freeCities.length && awaitingField === "from") {
    from = freeCities.find((city) => city !== to) || null;
  }

  return {
    normalized,
    from,
    to,
    date: parsedDate,
    preferNearest,
    mentionedCities,
    hasTripIntent:
      Boolean(awaitingField) ||
      Boolean(mentionedCities.length) ||
      Boolean(parsedDate) ||
      preferNearest ||
      /\b(sefer|bilet|git|otobus|yolculuk|rota|kalkis|varis|nereden|nereye)\b/.test(normalized),
  };
}

function wantsNearestChatbotTrip(normalizedMessage) {
  return /\b(en yakin|yakindaki|ilk sefer|siradaki|sonraki|en erken)\b/.test(normalizedMessage);
}

function wantsTicketCancellationHelp(normalizedMessage) {
  const hasTicketWord = /\b(bilet|rezervasyon|ticket)\b/.test(normalizedMessage);
  const hasRefundWord = /\b(iade|iptal|refund|cancel)\b/.test(normalizedMessage);
  const hasHowWord = /\b(nasil|nereden|yapilir|edebilirim|alabilirim)\b/.test(normalizedMessage);
  return (
    /\b(bilet iadesi|bilet iptali|ticket refund|ticket cancel)\b/.test(normalizedMessage) ||
    (hasTicketWord && hasRefundWord) ||
    (hasRefundWord && hasHowWord)
  );
}

function extractChatbotDate(normalizedMessage) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (/\bbugun\b/.test(normalizedMessage)) return formatDate(today);
  if (/\byarin\b/.test(normalizedMessage)) {
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    return formatDate(tomorrow);
  }
  if (/\b(obur gun|ertesi gun)\b/.test(normalizedMessage)) {
    const next = new Date(today);
    next.setDate(next.getDate() + 2);
    return formatDate(next);
  }

  const numericMatch = normalizedMessage.match(/\b(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?\b/);
  if (numericMatch) {
    const day = Number(numericMatch[1]);
    const month = Number(numericMatch[2]) - 1;
    const year = numericMatch[3] ? normalizeChatbotYear(Number(numericMatch[3])) : today.getFullYear();
    const resolved = resolveChatbotDate(year, month, day, !numericMatch[3]);
    if (resolved) return resolved;
  }

  const textMatch = normalizedMessage.match(
    /\b(\d{1,2})\s+(ocak|subat|mart|nisan|mayis|haziran|temmuz|agustos|eylul|ekim|kasim|aralik)\b/
  );
  if (textMatch) {
    const day = Number(textMatch[1]);
    const month = CHATBOT_MONTHS[textMatch[2]];
    const resolved = resolveChatbotDate(today.getFullYear(), month, day, true);
    if (resolved) return resolved;
  }

  return null;
}

function normalizeChatbotYear(year) {
  if (year < 100) return 2000 + year;
  return year;
}

function resolveChatbotDate(year, month, day, allowNextYear) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  let candidate = new Date(year, month, day);
  candidate.setHours(0, 0, 0, 0);
  if (
    Number.isNaN(candidate.getTime()) ||
    candidate.getFullYear() !== year ||
    candidate.getMonth() !== month ||
    candidate.getDate() !== day
  ) {
    return null;
  }
  if (allowNextYear && candidate < today) {
    candidate = new Date(year + 1, month, day);
    candidate.setHours(0, 0, 0, 0);
  }
  return formatDate(candidate);
}

function extractChatbotCityMentions(normalizedMessage, cityIndex) {
  const mentions = [];
  cityIndex.forEach((city) => {
    const pattern = new RegExp(`(^|\\s)${escapeRegex(city.normalized)}([a-z]{0,5})?(?=\\s|$)`, "g");
    let match = pattern.exec(normalizedMessage);
    while (match) {
      mentions.push({
        canonical: city.canonical,
        normalized: city.normalized,
        suffix: match[2] || "",
        position: match.index,
      });
      match = pattern.exec(normalizedMessage);
    }
  });
  return mentions.sort((a, b) => a.position - b.position);
}

function normalizeChatbotText(value) {
  return String(value || "")
    .toLocaleLowerCase("tr-TR")
    .replaceAll("ı", "i")
    .replaceAll("ğ", "g")
    .replaceAll("ü", "u")
    .replaceAll("ş", "s")
    .replaceAll("ö", "o")
    .replaceAll("ç", "c")
    .replaceAll(/['’`]/g, "")
    .replaceAll(/[^a-z0-9.\-/\s]/g, " ")
    .replaceAll(/\s+/g, " ")
    .trim();
}

function normalizeChatbotToken(value) {
  return normalizeChatbotText(value);
}

function escapeRegex(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function formatChatbotDateLabel(value) {
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium" }).format(date);
}

async function apiFetch(path, options = {}) {
  const token = localStorage.getItem(STORAGE_KEYS.token);
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  const raw = await response.text();
  let data = null;
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = raw;
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem(STORAGE_KEYS.user);
      localStorage.removeItem(STORAGE_KEYS.token);
    }
    const message =
      data && typeof data === "object"
        ? data.message || data.error || raw || `Request failed (${response.status})`
        : raw || `Request failed (${response.status})`;
    throw new Error(message);
  }
  return data;
}

async function syncCurrentUser() {
  const token = localStorage.getItem(STORAGE_KEYS.token);
  if (!token) return;
  try {
    const me = await apiFetch("/auth/me");
    if (me) writeJson(STORAGE_KEYS.user, me);
  } catch {
    localStorage.removeItem(STORAGE_KEYS.user);
    localStorage.removeItem(STORAGE_KEYS.token);
  }
}

async function loadCities() {
  try {
    const cities = await apiFetch("/cities");
    if (Array.isArray(cities) && cities.length) {
      return cities.map((c) => c.name);
    }
  } catch (error) {
    console.error(error);
  }
  return CITY_OPTIONS;
}

function populateCitySelect(select, cities) {
  if (!select) return;
  const placeholder = select.querySelector('option[value=""]');
  select.innerHTML = "";
  if (placeholder) select.appendChild(placeholder);
  cities.forEach((city) => {
    const option = document.createElement("option");
    option.value = city;
    option.textContent = city;
    select.appendChild(option);
  });
}

function populateCompanySelect(select, companies) {
  if (!select) return;
  const placeholder = select.querySelector('option[value=""]');
  select.innerHTML = "";
  if (placeholder) select.appendChild(placeholder);
  companies.forEach((company) => {
    const option = document.createElement("option");
    option.value = company;
    option.textContent = company;
    select.appendChild(option);
  });
}

async function initSearchPage() {
  const fromEl = document.getElementById("fromCity");
  const toEl = document.getElementById("toCity");
  const dateEl = document.getElementById("travelDate");
  const form = document.getElementById("searchForm");
  if (!fromEl || !toEl || !dateEl || !form) return;

  const saved = readJson(STORAGE_KEYS.search) || {};
  const minDate = formatDate(new Date());
  const cities = await loadCities();
  populateCitySelect(fromEl, cities);
  populateCitySelect(toEl, cities);
  dateEl.min = minDate;
  dateEl.value = saved.date || minDate;
  fromEl.value = saved.from || "";
  toEl.value = saved.to || "";

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (fromEl.value === toEl.value) return showToast("Origin and destination cannot be the same.", "error");
    const search = { from: fromEl.value, to: toEl.value, date: dateEl.value, ticketCount: 1 };
    writeJson(STORAGE_KEYS.search, search);
    localStorage.removeItem(STORAGE_KEYS.passengersDraft);
    window.location.href = "trips.html";
  });
}

async function fetchTripsByDate(from, to, date) {
  const params = new URLSearchParams({ from, to, date });
  try {
    const data = await apiFetch(`/trips?${params.toString()}`);
    return Array.isArray(data) ? data : [];
  } catch (error) {
    console.error(error);
    return [];
  }
}

async function findNearestTrips(search, maxDays = 7) {
  const params = new URLSearchParams({ from: search.from, to: search.to, date: search.date });
  try {
    const data = await apiFetch(`/trips/nearest?${params.toString()}`);
    if (data && Array.isArray(data.trips) && data.trips.length) {
      return { date: data.date, trips: data.trips };
    }
    if (data && Array.isArray(data.trips)) return null;
  } catch (error) {
    console.error(error);
  }
  return findNearestTripsClient(search, maxDays);
}

async function findNearestTripsClient(search, maxDays = 7) {
  const base = new Date(`${search.date}T00:00:00`);
  if (Number.isNaN(base.getTime())) return null;
  for (let offset = 1; offset <= maxDays; offset += 1) {
    const futureDate = new Date(base);
    futureDate.setDate(base.getDate() + offset);

    const futureStr = formatDate(futureDate);

    const futureTrips = await fetchTripsByDate(search.from, search.to, futureStr);
    if (futureTrips.length) return { date: futureStr, trips: futureTrips };
  }
  return null;
}

async function initTripsPage() {
  const search = readJson(STORAGE_KEYS.search);
  if (!search) return redirect("index.html");
  let trips = await fetchTripsByDate(search.from, search.to, search.date);

  const routeEl = document.getElementById("tripRouteSummary");
  const list = document.getElementById("tripList");
  if (!routeEl || !list) return;
  routeEl.textContent = `${search.from} → ${search.to} • ${search.date}`;
  let visibleTrips = trips;
  let effectiveSearch = search;
  if (!trips.length) {
    list.innerHTML = `<p class="subtle">Aradığınız tarihte seferimiz yoktur.</p>`;
    const suggestion = await findNearestTrips(search, 7);
    if (suggestion) {
      const hint = document.createElement("p");
      hint.className = "subtle";
      hint.textContent = `En yakın seferler: ${suggestion.date}`;
      list.appendChild(hint);
      visibleTrips = suggestion.trips;
      effectiveSearch = { ...search, date: suggestion.date };
      renderTrips(visibleTrips, list, { append: true });
    } else {
      const hint = document.createElement("p");
      hint.className = "subtle";
      hint.textContent = "Yakın tarihlerde sefer bulunamadı.";
      list.appendChild(hint);
    }
  } else {
    renderTrips(trips, list);
  }

  list.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest("button[data-id]");
    if (!btn) return;
    const trip = visibleTrips.find((item) => item.id === btn.dataset.id);
    if (!trip) return;
    writeJson(STORAGE_KEYS.search, effectiveSearch);
    writeJson(STORAGE_KEYS.trip, trip);
    window.location.href = "passenger.html";
  });
}

function initPassengerPage() {
  const search = readJson(STORAGE_KEYS.search);
  const trip = readJson(STORAGE_KEYS.trip);
  if (!search || !trip) return redirect("index.html");

  const form = document.getElementById("passengerForm");
  const list = document.getElementById("passengerCards");
  const addBtn = document.getElementById("addPassengerBtn");
  const continueBtn = document.getElementById("continueToSeatsBtn");
  const heading = document.getElementById("passengerTripMeta");
  if (!form || !list || !addBtn || !continueBtn || !heading) return;
  heading.textContent = `${search.from} → ${search.to} • ${trip.departureTime} • ${search.date}`;

  const existing = readJson(STORAGE_KEYS.passengersDraft);
  const passengers =
    Array.isArray(existing) && existing.length
      ? existing
      : Array.from({ length: Math.max(1, Number(search.ticketCount || 1)) }, (_, idx) => makePassengerDraft(idx + 1));

  const render = (errors = {}) => {
    list.innerHTML = passengers
      .map((p, idx) => renderPassengerCard(p, idx, errors[idx] || {}))
      .join("");
  };

  const collect = () => {
    const cards = Array.from(list.querySelectorAll("[data-passenger-index]"));
    return cards.map((card, idx) => ({
      id: passengers[idx]?.id || idx + 1,
      firstName: String(card.querySelector(".first-name")?.value || "").trim(),
      lastName: String(card.querySelector(".last-name")?.value || "").trim(),
      tc: sanitizeTc(card.querySelector(".tc-no")?.value || ""),
      age: Number(card.querySelector(".age")?.value || 0),
      email: String(card.querySelector(".email")?.value || "").trim(),
      phone: String(card.querySelector(".phone")?.value || "").trim(),
      gender: String(card.querySelector(".gender")?.value || "male"),
      baggage: Number(card.querySelector(".baggage")?.value || 15),
    }));
  };

  addBtn.addEventListener("click", () => {
    passengers.push(makePassengerDraft(passengers.length + 1));
    render();
  });

  list.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest("[data-remove-passenger]");
    if (!btn) return;
    const index = Number(btn.getAttribute("data-remove-passenger"));
    if (passengers.length <= 1) {
      showToast("At least one passenger is required.", "warning");
      return;
    }
    passengers.splice(index, 1);
    render();
  });

  list.addEventListener("input", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    if (!target.classList.contains("tc-no")) return;
    target.value = sanitizeTc(target.value);
  });

  list.addEventListener("beforeinput", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    if (!target.classList.contains("tc-no")) return;

    const inputType = event.inputType || "";
    if (!inputType.startsWith("insert")) return;

    const current = sanitizeTc(target.value);
    const selectionStart = target.selectionStart ?? current.length;
    const selectionEnd = target.selectionEnd ?? current.length;
    const selectionLength = Math.max(0, selectionEnd - selectionStart);
    const incoming = sanitizeTc(event.data || "");
    const nextLength = current.length - selectionLength + incoming.length;

    if (nextLength > 11) {
      event.preventDefault();
    }
  });

  list.addEventListener("keydown", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    if (!target.classList.contains("tc-no")) return;
    const key = event.key;
    if (key.length !== 1 || !/\d/.test(key)) return;

    const current = sanitizeTc(target.value);
    const selectionStart = target.selectionStart ?? current.length;
    const selectionEnd = target.selectionEnd ?? current.length;
    const selectionLength = Math.max(0, selectionEnd - selectionStart);
    if (current.length - selectionLength >= 11) {
      event.preventDefault();
    }
  });

  list.addEventListener("paste", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    if (!target.classList.contains("tc-no")) return;

    const clipboard = event.clipboardData?.getData("text") || "";
    const pasted = sanitizeTc(clipboard);
    const current = sanitizeTc(target.value);
    const selectionStart = target.selectionStart ?? current.length;
    const selectionEnd = target.selectionEnd ?? current.length;
    const before = current.slice(0, selectionStart);
    const after = current.slice(selectionEnd);
    target.value = sanitizeTc(before + pasted + after);
    event.preventDefault();
  });

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const collected = collect();
    const validation = validatePassengerForm(collected);
    // Always render with feedback (test-friendly).
    passengers.splice(0, passengers.length, ...collected);
    render(validation.errors);
    if (!validation.ok) {
      showToast(validation.message, "error");
      return;
    }

    const normalized = collected.map((p, idx) => ({
      id: idx + 1,
      firstName: p.firstName,
      lastName: p.lastName,
      name: `${p.firstName} ${p.lastName}`.trim(),
      tc: p.tc,
      age: p.age,
      email: p.email,
      phone: p.phone,
      gender: p.gender,
      baggage: p.baggage || 15,
      seatNumber: null,
    }));
    writeJson(STORAGE_KEYS.passengersDraft, normalized);
    const updatedSearch = { ...search, ticketCount: normalized.length };
    writeJson(STORAGE_KEYS.search, updatedSearch);
    showToast("Passenger information saved.", "success");
    setTimeout(() => redirect("seats.html"), 500);
  });

  continueBtn.addEventListener("click", () => form.requestSubmit());
  render();
}

function renderTrips(trips, list, options = {}) {
  const { append = false } = options;
  if (!append) list.innerHTML = "";
  trips.forEach((trip) => {
    const card = document.createElement("article");
    card.className = "trip-card panel";
    card.innerHTML = `
      <div class="trip-media">
        <img class="trip-thumb" src="${trip.image || "https://images.unsplash.com/photo-1464219789935-c2d9d9aba644?auto=format&fit=crop&w=900&q=80"}" alt="Bus exterior" />
        <div class="trip-logo">${escapeHtml(trip.company.slice(0, 2).toUpperCase())}</div>
      </div>
      <div class="trip-left">
        <h3>${escapeHtml(trip.company)}</h3>
        <p><strong>Departure:</strong> ${escapeHtml(trip.departureTime)}</p>
        <p><strong>Duration:</strong> ${escapeHtml(trip.duration)}</p>
        <p class="services">${SERVICE_ICONS.join(" • ")}</p>
      </div>
      <div class="trip-right">
        <p class="price">$${Number(trip.basePrice).toFixed(2)}</p>
        <button class="btn btn-primary" data-id="${escapeHtml(trip.id)}">Select</button>
      </div>
    `;
    list.appendChild(card);
  });
}

async function initSeatsPage() {
  const search = readJson(STORAGE_KEYS.search);
  const trip = readJson(STORAGE_KEYS.trip);
  if (!search || !trip) return redirect("index.html");

  const seatMeta = document.getElementById("seatTripMeta");
  const seatGrid = document.getElementById("seatGrid");
  const passengerList = document.getElementById("passengerList");
  const continueBtn = document.getElementById("continueBtn");
  if (!seatMeta || !seatGrid || !passengerList || !continueBtn) return;

  if (!canBookTrip(trip.departureDateTime)) showToast("Booking is disabled. Departure is less than 5 minutes away.", "error");

  let seats = [];
  try {
    seats = await createSeatMap(trip);
  } catch (error) {
    console.error(error);
    showToast("Seat map could not be loaded.", "error");
  }

  const state = {
    search,
    trip,
    seats,
    passengers: getSeatPassengers(search),
    activePassenger: 0,
    selectedSeats: [],
    warningsByPassenger: {},
    disabledBooking: !canBookTrip(trip.departureDateTime),
  };
  seatMeta.textContent = `${search.from} → ${search.to} • ${trip.departureTime} • ${trip.duration} • ${trip.company}`;
  continueBtn.disabled = state.disabledBooking;
  initSeatsCarousel();

  syncSelectedSeats(state);
  applySeatWarnings(state);
  renderPassengers(state, passengerList);
  renderSeats(state, seatGrid);
  updateSummary(state);

  passengerList.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest("button[data-activate]");
    if (!btn) return;
    state.activePassenger = Number(btn.getAttribute("data-activate"));
    renderPassengers(state, passengerList);
    renderSeats(state, seatGrid);
  });

  seatGrid.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const seatEl = target.closest("button[data-seat]");
    if (!seatEl) return;
    const seatNumber = Number(seatEl.dataset.seat);
    const result = selectSeat(seatNumber, state);
    renderPassengers(state, passengerList);
    renderSeats(state, seatGrid);
    updateSummary(state);
    if (!result.ok) pulseSeatInvalid(seatNumber);
    else animateSeatChange(seatNumber);
    if (result.warning) showToast(result.warning, "warning");
  });

  continueBtn.addEventListener("click", () => {
    if (!ensureLoggedIn("Please login to continue", "seats.html")) return;
    const validation = validateBooking(state);
    if (!validation.ok) return showToast(validation.message, "error");
    const total = calculateTotal(state);
    writeJson(STORAGE_KEYS.booking, { trip: state.trip, search: state.search, passengers: state.passengers, total });
    window.location.href = "cart.html";
  });
}

function getSeatPassengers(search) {
  const draft = readJson(STORAGE_KEYS.passengersDraft);
  if (Array.isArray(draft) && draft.length) {
    return draft.map((p, idx) => ({
      id: idx + 1,
      firstName: p.firstName || "",
      lastName: p.lastName || "",
      name: p.name || `${p.firstName || ""} ${p.lastName || ""}`.trim() || `Passenger ${idx + 1}`,
      tc: p.tc || "",
      age: Number(p.age || 0),
      email: p.email || "",
      phone: p.phone || "",
      gender: p.gender || "male",
      baggage: Number(p.baggage || 15),
      seatNumber: p.seatNumber || null,
    }));
  }
  return createPassengers(search.ticketCount);
}

function initSeatsCarousel() {
  const root = document.getElementById("seatCarousel");
  if (!root) return;
  const slides = Array.from(root.querySelectorAll(".carousel-slide"));
  const prev = root.querySelector("[data-carousel-prev]");
  const next = root.querySelector("[data-carousel-next]");
  if (!slides.length || !prev || !next) return;

  let index = 0;
  let timer = null;
  const render = () => slides.forEach((slide, i) => slide.classList.toggle("active", i === index));
  const goNext = () => {
    index = (index + 1) % slides.length;
    render();
  };
  const goPrev = () => {
    index = (index - 1 + slides.length) % slides.length;
    render();
  };
  const stopAuto = () => {
    if (!timer) return;
    window.clearInterval(timer);
    timer = null;
  };
  const startAuto = () => {
    stopAuto();
    timer = window.setInterval(goNext, 3800);
  };
  prev.addEventListener("click", () => {
    goPrev();
    startAuto();
  });
  next.addEventListener("click", () => {
    goNext();
    startAuto();
  });
  root.addEventListener("mouseenter", stopAuto);
  root.addEventListener("mouseleave", startAuto);
  render();
  startAuto();
}

async function initTicketPage() {
  const booking = readJson(STORAGE_KEYS.booking);
  if (!booking) return redirect("index.html");

  const meta = document.getElementById("ticketMeta");
  const list = document.getElementById("ticketPassengers");
  const total = document.getElementById("ticketTotal");
  const confirmBtn = document.getElementById("confirmBtn");
  if (!meta || !list || !total || !confirmBtn) return;

  meta.innerHTML = `
    <p><strong>Route:</strong> ${escapeHtml(booking.search.from)} → ${escapeHtml(booking.search.to)}</p>
    <p><strong>Date:</strong> ${escapeHtml(booking.search.date)}</p>
    <p><strong>Trip:</strong> ${escapeHtml(booking.trip.company)} at ${escapeHtml(booking.trip.departureTime)}</p>
  `;
  list.innerHTML = booking.passengers
    .map(
      (p) => `
      <div class="ticket-passenger-row">
        <span>${GENDER_ICONS[p.gender]} ${escapeHtml(p.name)} (${p.age})</span>
        <span>Seat ${p.seatNumber}</span>
        <span>${p.baggage}kg</span>
      </div>
    `
    )
    .join("");
  total.innerHTML = `<h2>Total: $${Number(booking.total).toFixed(2)}</h2>`;

  confirmBtn.addEventListener("click", async () => {
    if (!ensureLoggedIn("Please login to continue", "ticket.html")) return;
    const user = getCurrentUser();
    if (!user) return;
    try {
      await saveTicket(booking, user);
      localStorage.removeItem(STORAGE_KEYS.booking);
      showToast("Booking confirmed. Ticket saved to your profile.", "success");
      setTimeout(() => redirect("profile.html"), 1200);
    } catch (error) {
      console.error(error);
      showToast(error.message || "Booking failed.", "error");
    }
  });
}

function initCartPage() {
  const booking = readJson(STORAGE_KEYS.booking);
  if (!booking) return redirect("index.html");
  const meta = document.getElementById("cartMeta");
  const list = document.getElementById("cartPassengers");
  const total = document.getElementById("cartTotal");
  const proceed = document.getElementById("proceedPaymentBtn");
  if (!meta || !list || !total || !proceed) return;
  meta.innerHTML = `
    <p><strong>Route:</strong> ${escapeHtml(booking.search.from)} → ${escapeHtml(booking.search.to)}</p>
    <p><strong>Date:</strong> ${escapeHtml(booking.search.date)}</p>
    <p><strong>Trip:</strong> ${escapeHtml(booking.trip.company)} • ${escapeHtml(booking.trip.departureTime)}</p>
  `;
  list.innerHTML = booking.passengers
    .map((p) => `<div class="ticket-passenger-row"><span>${escapeHtml(p.name)}</span><span>Seat ${p.seatNumber}</span><span>$${getPassengerPrice(booking, p).toFixed(2)}</span></div>`)
    .join("");
  total.innerHTML = `<h2>Total: $${Number(booking.total).toFixed(2)}</h2>`;
  proceed.addEventListener("click", () => redirect("payment.html"));
}

async function initPaymentPage() {
  const booking = readJson(STORAGE_KEYS.booking);
  if (!booking) return redirect("index.html");
  if (!ensureLoggedIn("Please login to continue", "payment.html")) return;
  const user = getCurrentUser();
  if (!user) return;

  const summary = document.getElementById("paymentSummary");
  const form = document.getElementById("paymentForm");
  if (!summary || !form) return;
  const total = Number(booking.total || 0);
  const currentBalance = Number(user.demoBalance || 0);
  summary.innerHTML = `
    <p><strong>Route:</strong> ${escapeHtml(booking.search.from)} → ${escapeHtml(booking.search.to)}</p>
    <p><strong>Passengers:</strong> ${booking.passengers.length}</p>
    <p><strong>Total:</strong> ${escapeHtml(formatCurrency(total))}</p>
    <p><strong>Demo Balance:</strong> ${escapeHtml(formatCurrency(currentBalance))}</p>
    <div class="payment-testcards">
      <p class="small"><strong>Sandbox test cards</strong></p>
      <p class="small subtle">Success: <code>5528790000000008</code></p>
      <p class="small subtle">Insufficient funds: <code>4111111111111129</code></p>
      <p class="small subtle">Invalid CVC: <code>4124111111111116</code></p>
    </div>
  `;

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const cardNumber = String(document.getElementById("cardNumber")?.value || "").replace(/\s+/g, "");
    const cardName = String(document.getElementById("cardName")?.value || "").trim();
    const expiry = String(document.getElementById("cardExpiry")?.value || "").trim();
    const cvv = String(document.getElementById("cardCvv")?.value || "").trim();
    if (!/^\d{16}$/.test(cardNumber)) return showToast("Card number must be 16 digits.", "error");
    if (!cardName) return showToast("Card holder name is required.", "error");
    if (!/^\d{2}\/\d{2}$/.test(expiry)) return showToast("Expiry must be MM/YY format.", "error");
    if (!/^\d{3,4}$/.test(cvv)) return showToast("Invalid CVV.", "error");
    const items = booking.passengers.map((passenger) => ({
      seatNumber: Number(passenger.seatNumber || 0),
      amount: Number(getPassengerPrice(booking, passenger).toFixed(2)),
      passengerName: passenger.name || passenger.firstName || "Passenger",
    }));

    try {
      const payment = await apiFetch("/payments/iyzico", {
        method: "POST",
        body: JSON.stringify({
          cardNumber,
          cardHolderName: cardName,
          expiry,
          cvv,
          amount: total,
          currency: "TRY",
          items,
        }),
      });

      updateStoredUserBalance(payment?.balanceAfter);
      const payload = {
        ...booking,
        paymentProvider: "iyzico",
        paymentRef: payment?.transactionId || "",
        paymentId: payment?.paymentId || "",
        paymentItems: payment?.items || [],
      };

      await apiFetch("/bookings", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      localStorage.removeItem(STORAGE_KEYS.booking);
      showToast(`iyzico sandbox payment successful. Balance: ${formatCurrency(payment?.balanceAfter ?? currentBalance)}`, "success");
      setTimeout(() => redirect("profile.html"), 900);
    } catch (error) {
      console.error(error);
      showToast(error.message || "Payment failed.", "error");
    }
  });
}

function initCustomerLoginPage() {
  const form = document.getElementById("loginForm");
  if (!form) return;
  initForgotPasswordFlow({
    toggleId: "loginForgotToggle",
    formId: "loginForgotForm",
    emailId: "loginForgotEmail",
    resultId: "loginForgotResult",
    role: "user",
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = String(document.getElementById("loginEmail")?.value || "").trim().toLowerCase();
    const password = String(document.getElementById("loginPassword")?.value || "");
    if (!email || !password) return showToast("Please enter email and password.", "error");
    try {
      const result = await apiFetch("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password, role: "user" }),
      });
      localStorage.setItem(STORAGE_KEYS.token, result.token);
      writeJson(STORAGE_KEYS.user, result.user);
      showToast("Login successful.", "success");
      const go = localStorage.getItem(STORAGE_KEYS.redirectAfterLogin) || "index.html";
      localStorage.removeItem(STORAGE_KEYS.redirectAfterLogin);
      setTimeout(() => redirect(go), 800);
    } catch (error) {
      console.error(error);
      showToast(error.message || "Login failed.", "error");
    }
  });
}

function initAdminLoginPage() {
  const form = document.getElementById("adminLoginForm");
  if (!form) return;
  initForgotPasswordFlow({
    toggleId: "adminForgotToggle",
    formId: "adminForgotForm",
    emailId: "adminForgotEmail",
    resultId: "adminForgotResult",
    role: "admin",
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = String(document.getElementById("adminLoginEmail")?.value || "").trim().toLowerCase();
    const password = String(document.getElementById("adminLoginPassword")?.value || "");
    if (!email || !password) return showToast("Please enter email and password.", "error");
    try {
      const result = await apiFetch("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password, role: "admin" }),
      });
      localStorage.setItem(STORAGE_KEYS.token, result.token);
      writeJson(STORAGE_KEYS.user, result.user);
      showToast("Admin login successful.", "success");
      setTimeout(() => redirect("admin.html"), 700);
    } catch (error) {
      console.error(error);
      showToast(error.message || "Invalid admin credentials.", "error");
    }
  });
}

function initRegisterPage() {
  const form = document.getElementById("registerForm");
  if (!form) return;
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const name = String(document.getElementById("registerName")?.value || "").trim();
    const email = String(document.getElementById("registerEmail")?.value || "").trim().toLowerCase();
    const password = String(document.getElementById("registerPassword")?.value || "");
    if (!name || !email || !password) return showToast("Please complete all fields.", "error");
    if (password.length < 6) return showToast("Password must be at least 6 characters.", "error");
    try {
      const result = await apiFetch("/auth/register", {
        method: "POST",
        body: JSON.stringify({ username: name, email, password }),
      });
      localStorage.setItem(STORAGE_KEYS.token, result.token);
      writeJson(STORAGE_KEYS.user, result.user);
      showToast("Account created. Welcome!", "success");
      setTimeout(() => redirect("index.html"), 800);
    } catch (error) {
      console.error(error);
      showToast(error.message || "Registration failed.", "error");
    }
  });
}

function initForgotPasswordFlow({ toggleId, formId, emailId, resultId, role }) {
  const toggle = document.getElementById(toggleId);
  const form = document.getElementById(formId);
  const emailInput = document.getElementById(emailId);
  const result = document.getElementById(resultId);
  if (!form || !emailInput || !result) return;

  toggle?.addEventListener("click", () => {
    const shouldShow = form.hidden;
    form.hidden = !shouldShow;
    if (shouldShow) {
      emailInput.focus();
    } else {
      result.hidden = true;
      result.innerHTML = "";
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = String(emailInput.value || "").trim().toLowerCase();
    if (!email) return showToast("Please enter your email.", "error");
    try {
      const reset = await apiFetch("/auth/forgot-password", {
        method: "POST",
        body: JSON.stringify({ email, role }),
      });
      result.hidden = false;
      result.textContent = reset.message || "Temporary password sent to your email.";
      showToast("Temporary password sent to your email.", "success");
      form.reset();
    } catch (error) {
      console.error(error);
      showToast(error.message || "Password reset failed.", "error");
    }
  });
}

async function initProfilePage() {
  const user = getCurrentUser();
  if (!user) return requireLoginAndRedirect("profile.html");
  const scopedCompanyName = String(user.companyName || "").trim();

  const info = document.getElementById("profileInfo");
  const list = document.getElementById("myTickets");
  const ticketsSection = document.getElementById("profileTicketsSection");
  const adminSection = document.getElementById("profileAdminSection");
  const adminCreateForm = document.getElementById("profileAdminCreateForm");
  const adminCreateResult = document.getElementById("profileAdminCreateResult");
  const adminCompanyInput = document.getElementById("profileAdminCompanyName");
  const logout = document.getElementById("profileLogoutBtn");
  if (!info || !logout) return;

  const renderProfileInfo = () => {
    const currentUser = getCurrentUser() || user;
    info.innerHTML = `
      <p><strong>Name:</strong> ${escapeHtml(currentUser.username)}</p>
      <p><strong>Email:</strong> ${escapeHtml(currentUser.email)}</p>
      <p><strong>Role:</strong> ${escapeHtml(currentUser.role || "user")}</p>
      ${
        currentUser.companyName
          ? `<p><strong>Company:</strong> ${escapeHtml(currentUser.companyName)}</p>`
          : ""
      }
      <p><strong>Demo Balance:</strong> ${escapeHtml(formatCurrency(currentUser.demoBalance || 0))}</p>
    `;
  };

  renderProfileInfo();
  logout.addEventListener("click", () => logoutUser("index.html"));

  if ((user.role || "").toLowerCase() === "admin") {
    if (ticketsSection) ticketsSection.hidden = true;
    if (adminSection) adminSection.hidden = false;
    if (adminCompanyInput && scopedCompanyName) {
      adminCompanyInput.value = scopedCompanyName;
      adminCompanyInput.readOnly = true;
    }

    const renderCreateResult = (createdAdmin = null) => {
      if (!adminCreateResult) return;
      adminCreateResult.innerHTML = createdAdmin
        ? `
          <article class="admin-item">
            <p><strong>${escapeHtml(createdAdmin.username)}</strong></p>
            <p class="subtle small">${escapeHtml(createdAdmin.companyName)} • ${escapeHtml(createdAdmin.email)}</p>
            <p class="ticket-meta">Role: ${escapeHtml(createdAdmin.role || "admin")}</p>
          </article>`
        : `<p class="subtle">Created admins will appear here after you add them.</p>`;
    };

    renderCreateResult();

    adminCreateForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const firstName = String(document.getElementById("profileAdminFirstName")?.value || "").trim();
      const lastName = String(document.getElementById("profileAdminLastName")?.value || "").trim();
      const companyName = scopedCompanyName || String(document.getElementById("profileAdminCompanyName")?.value || "").trim();
      const email = String(document.getElementById("profileAdminEmail")?.value || "").trim().toLowerCase();
      const password = String(document.getElementById("profileAdminPassword")?.value || "");
      if (!firstName || !lastName || !companyName || !email || !password) {
        return showToast("Complete all admin fields.", "error");
      }
      if (password.length < 6) return showToast("Admin password must be at least 6 characters.", "error");
      try {
        const createdAdmin = await apiFetch("/admin/users/admin", {
          method: "POST",
          body: JSON.stringify({ firstName, lastName, companyName, email, password }),
        });
        adminCreateForm.reset();
        if (adminCompanyInput && scopedCompanyName) adminCompanyInput.value = scopedCompanyName;
        renderCreateResult(createdAdmin);
        showToast("Admin added.", "success");
      } catch (error) {
        console.error(error);
        showToast(error.message || "Admin create failed.", "error");
      }
    });
    return;
  }

  if (!list) return;

  let tickets = [];

  const formatDeparture = (ticket) => {
    const raw = ticket.departureDateTime || ticket.date;
    const value = new Date(raw);
    if (Number.isNaN(value.getTime())) return ticket.date || "";
    return new Intl.DateTimeFormat("tr-TR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(value);
  };

  const renderTickets = () => {
    list.innerHTML = tickets.length
      ? tickets
          .map(
            (ticket) => `
        <article class="ticket-item">
          <div class="ticket-details">
            <p><strong>${escapeHtml(ticket.from)} → ${escapeHtml(ticket.to)}</strong></p>
            <p class="ticket-meta">${escapeHtml(formatDeparture(ticket))} • ${escapeHtml(ticket.company)} • Seat ${escapeHtml(ticket.seatNumber)}</p>
            <p class="ticket-meta">Passenger: ${escapeHtml(ticket.passengerName || "Passenger")} • $${Number(ticket.price).toFixed(2)}</p>
            <p class="ticket-note ${ticket.cancellable ? "ticket-note-ok" : "ticket-note-blocked"}">
              ${escapeHtml(
                ticket.cancellable
                  ? "Cancellation is available because departure is more than 15 days away."
                  : ticket.cancellationMessage || "Cancellation is closed for this ticket."
              )}
            </p>
          </div>
          <div class="ticket-actions">
            ${
              ticket.cancellable
                ? `<button type="button" class="btn ghost" data-ticket-cancel="${escapeHtml(ticket.id)}">Cancel Ticket</button>`
                : `<span class="ticket-status">Cancellation closed</span>`
            }
          </div>
        </article>`
          )
          .join("")
      : `<p class="subtle">No tickets yet.</p>`;
  };

  const loadTickets = async () => {
    try {
      tickets = (await apiFetch("/tickets/me")) || [];
    } catch (error) {
      console.error(error);
      showToast("Tickets could not be loaded.", "error");
      tickets = [];
    }
    renderTickets();
  };

  await loadTickets();

  list.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-ticket-cancel]");
    if (!button) return;

    const ticketId = button.dataset.ticketCancel;
    const ticket = tickets.find((item) => item.id === ticketId);
    if (!ticket) return;

    const confirmed = window.confirm(
      `${ticket.from} → ${ticket.to} seferindeki koltuk ${ticket.seatNumber} iptal edilsin mi?`
    );
    if (!confirmed) return;

    button.disabled = true;
    try {
      const result = await apiFetch(`/tickets/${ticketId}`, { method: "DELETE" });
      updateStoredUserBalance(result?.balanceAfter);
      renderProfileInfo();
      const refundAmount =
        result?.refundedAmount != null ? ` Refund: ${formatCurrency(result.refundedAmount)}.` : "";
      const refundReference = result?.refundReference ? ` Ref: ${result.refundReference}` : "";
      showToast(
        (result.message || "Ticket cancelled.") + refundAmount + refundReference,
        "success"
      );
      await loadTickets();
    } catch (error) {
      console.error(error);
      showToast(error.message || "Ticket cancellation failed.", "error");
      button.disabled = false;
    }
  });
}

async function initAdminPage() {
  const user = getCurrentUser();
  if (!user) return requireLoginAndRedirect("admin.html", "admin-login.html");
  if (user.role !== "admin") {
    showToast("Admin access required.", "error");
    return redirect("index.html");
  }
  const scopedCompanyName = String(user.companyName || "").trim();
  const isCompanyScopedAdmin = Boolean(scopedCompanyName);
  const normalizedAdminEmail = String(user.email || "").trim().toLowerCase();
  const canManageCompanies = normalizedAdminEmail === "admin@busgo.com";

  const form = document.getElementById("adminTripForm");
  const list = document.getElementById("adminTripList");
  const bookings = document.getElementById("adminBookings");
  const cityForm = document.getElementById("adminCityForm");
  const cityList = document.getElementById("adminCityList");
  const companySection = document.getElementById("adminCompaniesSection");
  const companyForm = document.getElementById("adminCompanyForm");
  const companyList = document.getElementById("adminCompanyList");
  const adminFrom = document.getElementById("adminFrom");
  const adminTo = document.getElementById("adminTo");
  const adminCompany = document.getElementById("adminCompany");
  const adminCompanyNameInput = document.getElementById("adminCompanyName");
  const adminCompanyPhoneInput = document.getElementById("adminCompanyPhone");
  const adminCompanyEmailInput = document.getElementById("adminCompanyEmail");
  const adminCompanyLogoInput = document.getElementById("adminCompanyLogo");
  if (
    !form ||
    !list ||
    !bookings ||
    !cityForm ||
    !cityList ||
    !companyForm ||
    !companyList ||
    !adminFrom ||
    !adminTo ||
    !adminCompany
  ) {
    return;
  }
  if (companySection) companySection.hidden = !canManageCompanies;

  let adminTrips = [];
  let adminCities = [];
  let adminCompanies = [];
  let selectedCityId = null;
  let citySortOrder = null;

  const syncCompanyForm = () => {
    if (!canManageCompanies || !adminCompanyNameInput) return;
    adminCompanyNameInput.readOnly = false;
  };

  const renderCities = () => {
    const order = citySortOrder === "desc" ? -1 : 1;
    const sorted = [...adminCities].sort((a, b) => order * a.name.localeCompare(b.name, undefined, { sensitivity: "base" }));
    cityList.innerHTML = sorted.length
      ? sorted
          .map((c) => {
            const isActive = selectedCityId && selectedCityId === c.id;
            return `
        <div class="admin-list-item ${isActive ? "active" : ""}" data-city-id="${escapeHtml(c.id)}">
          <button type="button" class="admin-list-name" data-city-sort="${escapeHtml(c.id)}">
            ${escapeHtml(c.name)}${c.countryCode ? ` (${escapeHtml(c.countryCode)})` : ""}
          </button>
          <div class="admin-list-actions">
            <button class="btn ghost" data-city-delete="${escapeHtml(c.id)}">Delete</button>
          </div>
        </div>`;
          })
          .join("")
      : `<p class="subtle">No cities yet.</p>`;
  };

  const renderCompanies = () => {
    if (!canManageCompanies) {
      companyList.innerHTML = "";
      return;
    }
    companyList.innerHTML = adminCompanies.length
      ? adminCompanies
          .map(
            (c) => `
        <article class="admin-item">
          <p><strong>${escapeHtml(c.name)}</strong></p>
          <p class="subtle small">${escapeHtml(c.email || "")}</p>
          <div class="admin-actions">
            <button class="btn ghost" data-company-delete="${escapeHtml(c.id)}">Delete</button>
          </div>
        </article>`
          )
          .join("")
      : `<p class="subtle">No companies yet.</p>`;
  };

  const refreshCatalog = async () => {
    try {
      adminCities = (await apiFetch("/admin/cities")) || [];
    } catch (error) {
      console.error(error);
      adminCities = [];
    }
    if (canManageCompanies || !isCompanyScopedAdmin) {
      try {
        adminCompanies = (await apiFetch("/admin/companies")) || [];
      } catch (error) {
        console.error(error);
        adminCompanies = [];
      }
    } else {
      adminCompanies = [];
    }

    if (selectedCityId && !adminCities.some((c) => c.id === selectedCityId)) {
      selectedCityId = null;
    }
    renderCities();
    renderCompanies();
    syncCompanyForm();

    const cityNames = adminCities.length ? adminCities.map((c) => c.name) : CITY_OPTIONS;
    const fromVal = adminFrom.value;
    const toVal = adminTo.value;
    populateCitySelect(adminFrom, cityNames);
    populateCitySelect(adminTo, cityNames);
    if (fromVal) adminFrom.value = fromVal;
    if (toVal) adminTo.value = toVal;

    const companyNames = isCompanyScopedAdmin
      ? [scopedCompanyName]
      : adminCompanies.length
        ? adminCompanies.map((c) => c.name)
        : ["Admin Bus"];
    const companyVal = adminCompany.value;
    populateCompanySelect(adminCompany, companyNames);
    adminCompany.disabled = isCompanyScopedAdmin;
    if (isCompanyScopedAdmin) {
      adminCompany.value = scopedCompanyName;
    } else if (companyVal) {
      adminCompany.value = companyVal;
    }
  };

  const renderAdmin = async () => {
    await refreshCatalog();
    try {
      adminTrips = (await apiFetch("/admin/trips")) || [];
    } catch (error) {
      console.error(error);
      adminTrips = [];
      showToast("Admin trips could not be loaded.", "error");
    }
    list.innerHTML = adminTrips.length
      ? adminTrips
          .map(
            (t) => `
        <article class="admin-item admin-jitem">
          <p><strong>${escapeHtml(t.from)} → ${escapeHtml(t.to)}</strong> (${escapeHtml(t.company)})</p>
          <p>${escapeHtml(t.date)} ${escapeHtml(t.departureTime)} • $${Number(t.basePrice).toFixed(2)}</p>
          <div class="admin-actions">
            <button class="btn ghost" data-edit="${escapeHtml(t.id)}">Edit</button>
            <button class="btn ghost" data-delete="${escapeHtml(t.id)}">Delete</button>
          </div>
        </article>`
          )
          .join("")
      : `<p class="subtle">No admin trips created yet.</p>`;

    let allTickets = [];
    try {
      allTickets = (await apiFetch("/admin/tickets")) || [];
    } catch (error) {
      console.error(error);
      allTickets = [];
    }
    bookings.innerHTML = allTickets.length
      ? allTickets
          .map(
            (b) => `
        <article class="admin-item admin-jitem">
          <p><strong>${escapeHtml(b.userName)}</strong> (${escapeHtml(b.userEmail)})</p>
          <p>${escapeHtml(b.from)} → ${escapeHtml(b.to)} • ${escapeHtml(b.date)} • Seat ${b.seatNumber}</p>
        </article>`
          )
          .join("")
      : `<p class="subtle">No bookings yet.</p>`;
  };
  await renderAdmin();

  const createCity = async (name, countryCode) => {
    if (!name) return showToast("City name is required.", "error");
    await apiFetch("/admin/cities", {
      method: "POST",
      body: JSON.stringify({ name, countryCode }),
    });
  };

  const createCompany = async (payload) => {
    if (!canManageCompanies) {
      throw new Error("Only admin@busgo.com can manage companies.");
    }
    const name = String(payload?.name || "").trim();
    if (!name) return showToast("Company name is required.", "error");
    await apiFetch("/admin/companies", {
      method: "POST",
      body: JSON.stringify({
        name,
        phone: payload?.phone || "",
        email: payload?.email || "",
        logoUrl: payload?.logoUrl || "",
      }),
    });
  };

  cityForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const name = String(document.getElementById("adminCityName")?.value || "").trim();
    const countryCode = String(document.getElementById("adminCityCountry")?.value || "").trim();
    if (!name) return showToast("City name is required.", "error");
    try {
      await createCity(name, countryCode);
      cityForm.reset();
      showToast("City added.", "success");
      await renderAdmin();
    } catch (error) {
      console.error(error);
      showToast(error.message || "City add failed.", "error");
    }
  });

  companyForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!canManageCompanies) {
      return showToast("Only admin@busgo.com can manage companies.", "error");
    }
    const name = String(document.getElementById("adminCompanyName")?.value || "").trim();
    const phone = String(document.getElementById("adminCompanyPhone")?.value || "").trim();
    const email = String(document.getElementById("adminCompanyEmail")?.value || "").trim();
    const logoUrl = String(document.getElementById("adminCompanyLogo")?.value || "").trim();
    if (!name) return showToast("Company name is required.", "error");
    try {
      await createCompany({ name, phone, email, logoUrl });
      companyForm.reset();
      showToast("Company added.", "success");
      await renderAdmin();
    } catch (error) {
      console.error(error);
      showToast(error.message || "Company add failed.", "error");
    }
  });


  cityList.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const deleteId = target.getAttribute("data-city-delete");
    if (deleteId) {
      try {
        await apiFetch(`/admin/cities/${encodeURIComponent(deleteId)}`, { method: "DELETE" });
        showToast("City deleted.", "success");
        await renderAdmin();
      } catch (error) {
        console.error(error);
        showToast(error.message || "City delete failed.", "error");
      }
      return;
    }

    const row = target.closest("[data-city-id]");
    const cityId = row?.getAttribute("data-city-id");
    const sortId = target.getAttribute("data-city-sort");
    if (!cityId) return;

    selectedCityId = cityId;
    if (sortId) {
      citySortOrder = citySortOrder === "asc" ? "desc" : "asc";
    }
    renderCities();
  });

  companyList.addEventListener("click", async (event) => {
    if (!canManageCompanies) return;
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const deleteId = target.getAttribute("data-company-delete");
    if (!deleteId) return;
    try {
      await apiFetch(`/admin/companies/${encodeURIComponent(deleteId)}`, { method: "DELETE" });
      showToast("Company deleted.", "success");
      await renderAdmin();
    } catch (error) {
      console.error(error);
      showToast(error.message || "Company delete failed.", "error");
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const editingId = form.getAttribute("data-edit-id");
    const trip = {
      from: String(document.getElementById("adminFrom")?.value || ""),
      to: String(document.getElementById("adminTo")?.value || ""),
      date: String(document.getElementById("adminDate")?.value || ""),
      departureTime: String(document.getElementById("adminTime")?.value || ""),
      basePrice: Number(document.getElementById("adminPrice")?.value || 0),
      company: isCompanyScopedAdmin
        ? scopedCompanyName
        : String(document.getElementById("adminCompany")?.value || "Admin Bus"),
    };
    if (!trip.from || !trip.to || !trip.date || !trip.departureTime || !trip.basePrice || !trip.company) {
      return showToast("Complete all trip fields.", "error");
    }
    try {
      if (editingId) {
        await apiFetch(`/admin/trips/${encodeURIComponent(editingId)}`, {
          method: "PUT",
          body: JSON.stringify(trip),
        });
      } else {
        await apiFetch("/admin/trips", { method: "POST", body: JSON.stringify(trip) });
      }
      form.removeAttribute("data-edit-id");
      form.reset();
      showToast("Trip saved.", "success");
      await renderAdmin();
    } catch (error) {
      console.error(error);
      showToast(error.message || "Trip save failed.", "error");
    }
  });

  list.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const editId = target.getAttribute("data-edit");
    const deleteId = target.getAttribute("data-delete");
    if (deleteId) {
      try {
        await apiFetch(`/admin/trips/${encodeURIComponent(deleteId)}`, { method: "DELETE" });
        showToast("Trip deleted.", "success");
        await renderAdmin();
      } catch (error) {
        console.error(error);
        showToast(error.message || "Trip delete failed.", "error");
      }
      return;
    }
    if (editId) {
      const t = adminTrips.find((item) => item.id === editId);
      if (!t) return;
      form.setAttribute("data-edit-id", t.id);
      setInput("adminFrom", t.from);
      setInput("adminTo", t.to);
      setInput("adminDate", t.date);
      setInput("adminTime", t.departureTime);
      setInput("adminPrice", String(t.basePrice));
      setInput("adminCompany", isCompanyScopedAdmin ? scopedCompanyName : t.company);
      showToast("Trip loaded for edit.", "info");
    }
  });
}

function selectSeat(seatNumber, state) {
  if (state.disabledBooking) return { ok: false };
  const seat = state.seats.find((s) => s.number === seatNumber);
  if (!seat) return { ok: false };
  if (seat.status === "occupied") return { ok: false };

  const passenger = state.passengers[state.activePassenger];
  if (!passenger) return { ok: false };

  if (passenger.seatNumber === seatNumber) {
    passenger.seatNumber = null;
    syncSelectedSeats(state);
    applySeatWarnings(state);
    return { ok: true };
  }

  const occupiedByOther = state.passengers.some((p, idx) => idx !== state.activePassenger && p.seatNumber === seatNumber);
  if (occupiedByOther) return { ok: false };

  passenger.seatNumber = seatNumber;
  syncSelectedSeats(state);
  applySeatWarnings(state);
  const warning = state.warningsByPassenger[state.activePassenger]?.[0];
  return { ok: true, warning };
}

function validateSeat(passenger, state) {
  const warnings = [];
  const seat = state.seats.find((s) => s.number === passenger.seatNumber);
  if (!seat) return warnings;
  const adjacent = adjacentSeats(seat.number, state.seats);
  const sameGroup = state.selectedSeats.length > 1;
  adjacent.forEach((adj) => {
    if (adj.status === "occupied" && adj.gender && passenger.gender !== adj.gender && !sameGroup) {
      warnings.push("You cannot sit next to opposite gender unless you are traveling together");
    }
  });
  if (passenger.age < 18) {
    const hasAdult = adjacent.some((adj) => {
      const idx = state.passengers.findIndex((p) => p.seatNumber === adj.number);
      return idx >= 0 && state.passengers[idx].age >= 18;
    });
    if (!hasAdult) warnings.push("Child passenger must be seated next to an adult.");
  }
  return [...new Set(warnings)];
}

function applySeatWarnings(state) {
  state.warningsByPassenger = {};
  state.passengers.forEach((p, idx) => {
    if (!p.seatNumber) return;
    const warnings = validateSeat(p, state);
    if (warnings.length) state.warningsByPassenger[idx] = warnings;
  });
}

function renderSeats(state, container) {
  container.innerHTML = "";
  const active = state.passengers[state.activePassenger];
  const warningSeats = new Set(
    Object.keys(state.warningsByPassenger).map((idx) => state.passengers[Number(idx)]?.seatNumber).filter(Boolean)
  );

  for (let row = 1; row <= ROWS; row += 1) {
    for (let col = 1; col <= COLS; col += 1) {
      const num = (row - 1) * COLS + col;
      const seat = state.seats.find((s) => s.number === num);
      if (!seat) continue;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.dataset.seat = String(num);

      const selectedBy = state.passengers.find((p) => p.seatNumber === num);
      const status = seat.status === "occupied" ? "occupied" : selectedBy ? "selected" : "available";
      btn.className = `seat ${status} ${seat.isVip ? "vip-seat" : ""} ${warningSeats.has(num) ? "warning-seat" : ""}`;
      if (active && active.seatNumber === num) btn.classList.add("focus-seat");

      btn.innerHTML = `<span>${num}</span>${seat.isVip ? "<small>VIP</small>" : ""}`;
      btn.title = warningSeats.has(num) ? "Seat selected with warning. Review before checkout." : "";
      container.appendChild(btn);
      if (col === 2) {
        const aisle = document.createElement("div");
        aisle.className = "aisle";
        container.appendChild(aisle);
      }
    }
  }
}

function renderPassengers(state, container) {
  container.innerHTML = state.passengers
    .map((p, idx) => {
      const isActive = idx === state.activePassenger ? "active" : "";
      const warning = state.warningsByPassenger[idx]?.[0];
      return `
        <article class="passenger-card ${isActive}" data-passenger-index="${idx}">
          <div class="passenger-head">
            <strong>${idx + 1}. ${escapeHtml(p.name)}</strong>
            <button type="button" class="btn ghost" data-activate="${idx}">Select</button>
          </div>
          <p class="small subtle">Seat: ${p.seatNumber || "not assigned"}</p>
          ${warning ? `<p class="warn">${escapeHtml(warning)}</p>` : ""}
        </article>
      `;
    })
    .join("");
}

function updateSummary(state) {
  const box = document.getElementById("summaryBox");
  if (!box) return;
  const selected = state.passengers.filter((p) => p.seatNumber);
  const warningCount = Object.keys(state.warningsByPassenger).length;
  const total = calculateTotal(state);
  box.classList.add("pulse");
  box.innerHTML = `
    <p><strong>Selected:</strong> ${selected.map((s) => s.seatNumber).join(", ") || "-"}</p>
    <p><strong>Warnings:</strong> ${warningCount}</p>
    <p><strong>Total:</strong> $${total.toFixed(2)}</p>
  `;
  setTimeout(() => box.classList.remove("pulse"), 250);
}

function validateBooking(state) {
  if (state.search.ticketCount > 5) return { ok: false, message: "Maximum 5 tickets are allowed." };
  if (state.disabledBooking) return { ok: false, message: "Booking not allowed less than 5 minutes before departure." };
  const missing = state.passengers.some((p) => !p.seatNumber || p.age <= 0 || !p.name);
  if (missing) return { ok: false, message: "All passengers must have name, age, and assigned seat." };

  const warningEntry = Object.values(state.warningsByPassenger).find((warnings) => warnings.length);
  if (warningEntry) return { ok: false, message: warningEntry[0] };
  return { ok: true };
}

function calculateTotal(state) {
  return state.passengers.reduce((sum, p) => {
    const seat = state.seats.find((s) => s.number === p.seatNumber);
    if (!seat) return sum;
    let item = state.trip.basePrice;
    if (seat.isVip) item += state.trip.basePrice * VIP_SURCHARGE;
    return sum + item;
  }, 0);
}

function createPassengers(count) {
  return Array.from({ length: count }, (_, idx) => ({
    id: idx + 1,
    firstName: `Passenger`,
    lastName: `${idx + 1}`,
    name: `Passenger ${idx + 1}`,
    tc: "",
    age: 18,
    email: "",
    phone: "",
    gender: idx % 2 === 0 ? "male" : "female",
    baggage: 15,
    seatNumber: null,
  }));
}

function makePassengerDraft(index) {
  return {
    id: index,
    firstName: "",
    lastName: "",
    tc: "",
    age: 18,
    email: "",
    phone: "",
    gender: index % 2 === 0 ? "male" : "female",
    baggage: 15,
  };
}

function renderPassengerCard(passenger, idx, errors) {
  const fieldError = (key) => (errors[key] ? `<p class="field-error">${escapeHtml(errors[key])}</p>` : "");
  const invalid = (key) => (errors[key] ? "invalid-field" : "");
  return `
    <article class="passenger-input-card" data-passenger-index="${idx}">
      <div class="passenger-head">
        <strong>Passenger ${idx + 1}</strong>
        <button type="button" class="btn ghost" data-remove-passenger="${idx}">Remove</button>
      </div>
      <div class="passenger-form-grid">
        <label><span>First Name</span><input class="first-name ${invalid("firstName")}" value="${escapeHtml(passenger.firstName || "")}" placeholder="First name" />${fieldError("firstName")}</label>
        <label><span>Last Name</span><input class="last-name ${invalid("lastName")}" value="${escapeHtml(passenger.lastName || "")}" placeholder="Last name" />${fieldError("lastName")}</label>
        <label><span>TC ID</span><input class="tc-no ${invalid("tc")}" value="${escapeHtml(sanitizeTc(passenger.tc || ""))}" placeholder="11 digit TC" maxlength="11" inputmode="numeric" pattern="\\d{11}" />${fieldError("tc")}</label>
        <label><span>Age</span><input class="age ${invalid("age")}" type="number" min="0" max="100" value="${Number(passenger.age || 0)}" />${fieldError("age")}</label>
        <label><span>Email</span><input class="email ${invalid("email")}" type="email" value="${escapeHtml(passenger.email || "")}" placeholder="name@example.com" />${fieldError("email")}</label>
        <label><span>Phone</span><input class="phone ${invalid("phone")}" value="${escapeHtml(passenger.phone || "")}" placeholder="+90 5xx xxx xx xx" />${fieldError("phone")}</label>
        <label><span>Gender</span><select class="gender"><option value="male" ${passenger.gender === "male" ? "selected" : ""}>Male</option><option value="female" ${passenger.gender === "female" ? "selected" : ""}>Female</option></select></label>
        <label><span>Baggage (kg)</span><input class="baggage" type="number" min="0" max="80" value="${Number(passenger.baggage || 15)}" /></label>
      </div>
    </article>
  `;
}

function validatePassengerForm(passengers) {
  const errors = {};
  let hasError = false;
  const tcSeen = new Map();
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const phoneRegex = /^\+?[0-9\s\-()]{10,20}$/;

  passengers.forEach((p, idx) => {
    errors[idx] = {};
    if (!p.firstName) errors[idx].firstName = "First name is required";
    if (!p.lastName) errors[idx].lastName = "Last name is required";
    if (!p.tc) errors[idx].tc = "TC is required";
    if (!p.age) errors[idx].age = "Age is required";
    if (!p.email) errors[idx].email = "Email is required";
    if (!p.phone) errors[idx].phone = "Phone is required";

    if (p.tc && !TC_REGEX.test(p.tc)) errors[idx].tc = "TC must be 11 digits";
    if (p.email && !emailRegex.test(p.email)) errors[idx].email = "Invalid email format";
    if (p.phone && !phoneRegex.test(p.phone)) errors[idx].phone = "Invalid phone format";
    if (p.age < 0 || p.age > 100) errors[idx].age = "Age must be between 0 and 100";

    if (p.tc) {
      if (tcSeen.has(p.tc)) {
        errors[idx].tc = "TC must be unique";
        errors[tcSeen.get(p.tc)].tc = "TC must be unique";
      } else {
        tcSeen.set(p.tc, idx);
      }
    }
    if (Object.keys(errors[idx]).length > 0) hasError = true;
  });

  const minors = passengers.filter((p) => p.age < 18);
  const adults = passengers.filter((p) => p.age >= 18);
  if (passengers.length === 1 && minors.length === 1) {
    errors[0].age = "Passengers under 18 cannot travel alone";
    hasError = true;
  } else if (minors.length > 0 && adults.length === 0) {
    minors.forEach((m) => {
      const idx = passengers.findIndex((p) => p.tc === m.tc);
      if (idx >= 0) errors[idx].age = "At least one adult passenger is required";
    });
    hasError = true;
  }

  return {
    ok: !hasError,
    errors,
    message: hasError ? "Please fix passenger form errors before continuing." : "",
  };
}

async function createSeatMap(trip) {
  const seats = [];
  for (let row = 1; row <= ROWS; row += 1) {
    for (let col = 1; col <= COLS; col += 1) {
      seats.push({
        number: (row - 1) * COLS + col,
        row,
        col,
        isVip: VIP_ROWS.includes(row),
        status: "available",
        gender: null,
      });
    }
  }
  let occupiedSeats = [];
  try {
    if (trip?.id) {
      occupiedSeats = (await apiFetch(`/trips/${encodeURIComponent(trip.id)}/seats`)) || [];
    }
  } catch (error) {
    console.error(error);
    occupiedSeats = [];
  }

  occupiedSeats.forEach((seatNumber) => {
    const seat = seats.find((s) => s.number === Number(seatNumber));
    if (!seat) return;
    seat.status = "occupied";
    seat.gender = null;
  });
  return seats;
}

function syncSelectedSeats(state) {
  state.selectedSeats = state.passengers.filter((p) => p.seatNumber).map((p) => p.seatNumber);
}

function adjacentSeats(seatNumber, seats) {
  const seat = seats.find((s) => s.number === seatNumber);
  if (!seat) return [];
  return seats.filter((s) => s.row === seat.row && s.number !== seat.number && ((seat.col <= 2 && s.col <= 2) || (seat.col >= 3 && s.col >= 3)));
}

function cityOptions(select) {
  if (!select) return;
  CITY_OPTIONS.forEach((city) => {
    const option = document.createElement("option");
    option.value = city;
    option.textContent = city;
    select.appendChild(option);
  });
}

function canBookTrip(departureIso) {
  return new Date(departureIso).getTime() - Date.now() >= 5 * 60 * 1000;
}

async function saveTicket(booking, user) {
  if (!booking || !user) throw new Error("Missing booking or user.");
  const response = await apiFetch("/bookings", {
    method: "POST",
    body: JSON.stringify(booking),
  });
  return response;
}

function getPassengerPrice(booking, passenger) {
  const seatNumber = Number(passenger.seatNumber || 0);
  const row = seatNumber ? Math.floor((seatNumber - 1) / COLS) + 1 : 0;
  let item = booking.trip.basePrice;
  if (row && VIP_ROWS.includes(row)) item += booking.trip.basePrice * VIP_SURCHARGE;
  return item;
}

function renderAuthNav() {
  const root = document.getElementById("authNav");
  if (!root) return;
  const user = getCurrentUser();
  if (user) {
    const adminLink = user.role === "admin" ? `<a href="admin.html">Admin</a><span class="auth-badge">ADMIN</span>` : "";
    const balance =
      user.demoBalance !== undefined && user.demoBalance !== null
        ? `<span class="auth-balance">${escapeHtml(formatCurrency(user.demoBalance))}</span>`
        : "";
    root.innerHTML = `
      <a href="profile.html">Profile</a>
      ${adminLink}
      ${balance}
      <span class="auth-user">${escapeHtml(user.email)}</span>
      <button type="button" id="logoutBtn" class="btn ghost">Logout</button>
    `;
    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) logoutBtn.addEventListener("click", () => logoutUser("index.html"));
    return;
  }
  root.innerHTML = `
    <a href="customer-login.html">Customer Login</a>
    <a href="admin-login.html">Admin Login</a>
    <a href="register.html">Register</a>
  `;
}

function getCurrentUser() {
  return readJson(STORAGE_KEYS.user);
}

function ensureDefaultUsers() {
  // Backend seeds default users; no-op on frontend.
}

function ensureLoggedIn(message, returnTo, loginPage = "customer-login.html") {
  if (getCurrentUser()) return true;
  showToast(message, "warning");
  localStorage.setItem(STORAGE_KEYS.redirectAfterLogin, returnTo);
  setTimeout(() => redirect(loginPage), 700);
  return false;
}

function requireLoginAndRedirect(returnTo, loginPage = "customer-login.html") {
  ensureLoggedIn("Please login to continue", returnTo, loginPage);
}

async function logoutUser(target) {
  try {
    await apiFetch("/auth/logout", { method: "POST" });
  } catch (error) {
    console.error(error);
  }
  localStorage.removeItem(STORAGE_KEYS.user);
  localStorage.removeItem(STORAGE_KEYS.token);
  showToast("Logged out.", "success");
  setTimeout(() => redirect(target), 700);
}

function setInput(id, value) {
  const el = document.getElementById(id);
  if (!el) return;
  el.value = value;
}

function redirect(url) {
  window.location.href = url;
}

function showToast(message, type = "info") {
  const root = document.getElementById("toastContainer");
  if (!root) return;
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;
  root.appendChild(toast);
  setTimeout(() => {
    toast.classList.add("fade");
    setTimeout(() => toast.remove(), 250);
  }, 2600);
}

function readJson(key) {
  const raw = localStorage.getItem(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function writeJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function updateStoredUserBalance(balance) {
  if (balance === undefined || balance === null) return;
  const user = getCurrentUser();
  const numeric = Number(balance);
  if (!user || Number.isNaN(numeric)) return;
  user.demoBalance = numeric;
  writeJson(STORAGE_KEYS.user, user);
  renderAuthNav();
}

function formatCurrency(value, currency = "TRY") {
  const numeric = Number(value || 0);
  return new Intl.NumberFormat("tr-TR", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number.isNaN(numeric) ? 0 : numeric);
}

function formatDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function initLandingEnhancements() {
  const cta = document.getElementById("ctaSearch");
  const target = document.getElementById("searchSection");
  if (!cta || !target) return;
  cta.addEventListener("click", () => target.scrollIntoView({ behavior: "smooth", block: "start" }));
}

function initScrollReveal() {
  const prefersReduced = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const elements = Array.from(document.querySelectorAll(".reveal"));
  if (!elements.length) return;
  if (prefersReduced || !("IntersectionObserver" in window)) return elements.forEach((el) => el.classList.add("is-visible"));
  const observer = new IntersectionObserver(
    (entries) => entries.forEach((entry) => entry.isIntersecting && (entry.target.classList.add("is-visible"), observer.unobserve(entry.target))),
    { threshold: 0.12 }
  );
  elements.forEach((el) => observer.observe(el));
}

function animateSeatChange(seatNumber) {
  const btn = document.querySelector(`button[data-seat="${seatNumber}"]`);
  if (!btn || typeof btn.animate !== "function") return;
  btn.animate([{ transform: "scale(1)" }, { transform: "scale(1.09)" }, { transform: "scale(1)" }], {
    duration: 240,
    easing: "ease-out",
  });
}

function pulseSeatInvalid(seatNumber) {
  const btn = document.querySelector(`button[data-seat="${seatNumber}"]`);
  if (!btn) return;
  btn.classList.remove("invalid");
  void btn.offsetWidth;
  btn.classList.add("invalid");
  setTimeout(() => btn.classList.remove("invalid"), 450);
}
