const STORAGE_KEYS = {
  search: "busgo_search",
  trips: "busgo_trips",
  trip: "busgo_trip",
  booking: "busgo_booking",
  users: "busgo_users",
  user: "busgo_user",
  tickets: "busgo_tickets",
  adminTrips: "busgo_admin_trips",
  redirectAfterLogin: "busgo_redirect_after_login",
  passengersDraft: "busgo_passengers_draft",
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

document.addEventListener("DOMContentLoaded", initApp);

function initApp() {
  const page = document.body.dataset.page;
  ensureDefaultUsers();
  renderAuthNav();
  initScrollReveal();

  if (page === "search") {
    initSearchPage();
    initLandingEnhancements();
  } else if (page === "trips") {
    initTripsPage();
  } else if (page === "passenger") {
    initPassengerPage();
  } else if (page === "seats") {
    initSeatsPage();
  } else if (page === "cart") {
    initCartPage();
  } else if (page === "payment") {
    initPaymentPage();
  } else if (page === "ticket") {
    initTicketPage();
  } else if (page === "login" || page === "customer-login") {
    initCustomerLoginPage();
  } else if (page === "admin-login") {
    initAdminLoginPage();
  } else if (page === "register") {
    initRegisterPage();
  } else if (page === "profile") {
    initProfilePage();
  } else if (page === "admin") {
    initAdminPage();
  }
}

function initSearchPage() {
  const fromEl = document.getElementById("fromCity");
  const toEl = document.getElementById("toCity");
  const dateEl = document.getElementById("travelDate");
  const form = document.getElementById("searchForm");
  if (!fromEl || !toEl || !dateEl || !form) return;

  const saved = readJson(STORAGE_KEYS.search) || {};
  const minDate = formatDate(new Date());
  cityOptions(fromEl);
  cityOptions(toEl);
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
    writeJson(STORAGE_KEYS.trips, getAvailableTrips(search));
    window.location.href = "trips.html";
  });
}

function initTripsPage() {
  const search = readJson(STORAGE_KEYS.search);
  const trips = readJson(STORAGE_KEYS.trips) || [];
  if (!search || !trips.length) return redirect("index.html");

  const routeEl = document.getElementById("tripRouteSummary");
  const list = document.getElementById("tripList");
  if (!routeEl || !list) return;
  routeEl.textContent = `${search.from} → ${search.to} • ${search.date}`;
  renderTrips(trips, list);

  list.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest("button[data-id]");
    if (!btn) return;
    const trip = trips.find((item) => item.id === btn.dataset.id);
    if (!trip) return;
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
      tc: String(card.querySelector(".tc-no")?.value || "").trim(),
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

function renderTrips(trips, list) {
  list.innerHTML = "";
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

function initSeatsPage() {
  const search = readJson(STORAGE_KEYS.search);
  const trip = readJson(STORAGE_KEYS.trip);
  if (!search || !trip) return redirect("index.html");

  const seatMeta = document.getElementById("seatTripMeta");
  const seatGrid = document.getElementById("seatGrid");
  const passengerList = document.getElementById("passengerList");
  const continueBtn = document.getElementById("continueBtn");
  if (!seatMeta || !seatGrid || !passengerList || !continueBtn) return;

  if (!canBookTrip(trip.departureDateTime)) showToast("Booking is disabled. Departure is less than 5 minutes away.", "error");

  const state = {
    search,
    trip,
    seats: createSeatMap(trip),
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

function initTicketPage() {
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

  confirmBtn.addEventListener("click", () => {
    if (!ensureLoggedIn("Please login to continue", "ticket.html")) return;
    const user = getCurrentUser();
    if (!user) return;
    saveTicket(booking, user);
    localStorage.removeItem(STORAGE_KEYS.booking);
    showToast("Booking confirmed. Ticket saved to your profile.", "success");
    setTimeout(() => redirect("profile.html"), 1200);
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

function initPaymentPage() {
  const booking = readJson(STORAGE_KEYS.booking);
  if (!booking) return redirect("index.html");
  if (!ensureLoggedIn("Please login to continue", "payment.html")) return;
  const user = getCurrentUser();
  if (!user) return;

  const summary = document.getElementById("paymentSummary");
  const form = document.getElementById("paymentForm");
  if (!summary || !form) return;
  summary.innerHTML = `
    <p><strong>Route:</strong> ${escapeHtml(booking.search.from)} → ${escapeHtml(booking.search.to)}</p>
    <p><strong>Passengers:</strong> ${booking.passengers.length}</p>
    <p><strong>Total:</strong> $${Number(booking.total).toFixed(2)}</p>
  `;

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const cardNumber = String(document.getElementById("cardNumber")?.value || "").replace(/\s+/g, "");
    const cardName = String(document.getElementById("cardName")?.value || "").trim();
    const expiry = String(document.getElementById("cardExpiry")?.value || "").trim();
    const cvv = String(document.getElementById("cardCvv")?.value || "").trim();
    if (!/^\d{16}$/.test(cardNumber)) return showToast("Card number must be 16 digits.", "error");
    if (!cardName) return showToast("Card holder name is required.", "error");
    if (!/^\d{2}\/\d{2}$/.test(expiry)) return showToast("Expiry must be MM/YY format.", "error");
    if (!/^\d{3,4}$/.test(cvv)) return showToast("Invalid CVV.", "error");

    saveTicket(booking, user);
    localStorage.removeItem(STORAGE_KEYS.booking);
    showToast("Payment successful. Ticket saved.", "success");
    setTimeout(() => redirect("profile.html"), 900);
  });
}

function initCustomerLoginPage() {
  const form = document.getElementById("loginForm");
  if (!form) return;
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const email = String(document.getElementById("loginEmail")?.value || "").trim().toLowerCase();
    const password = String(document.getElementById("loginPassword")?.value || "");
    if (!email || !password) return showToast("Please enter email and password.", "error");

    const users = readJson(STORAGE_KEYS.users) || [];
    const match = users.find((u) => String(u.email || "").toLowerCase() === email);
    if (!match || match.password !== password) return showToast("Invalid email or password.", "error");

    if ((match.role || "user") !== "user") {
      return showToast("Use Admin Login for admin accounts.", "warning");
    }

    writeJson(STORAGE_KEYS.user, { email: match.email, username: match.username, role: "user" });
    showToast("Login successful.", "success");
    const go = localStorage.getItem(STORAGE_KEYS.redirectAfterLogin) || "index.html";
    localStorage.removeItem(STORAGE_KEYS.redirectAfterLogin);
    setTimeout(() => redirect(go), 800);
  });
}

function initAdminLoginPage() {
  const form = document.getElementById("adminLoginForm");
  if (!form) return;
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const email = String(document.getElementById("adminLoginEmail")?.value || "").trim().toLowerCase();
    const password = String(document.getElementById("adminLoginPassword")?.value || "");
    if (!email || !password) return showToast("Please enter email and password.", "error");

    const users = readJson(STORAGE_KEYS.users) || [];
    const match = users.find((u) => String(u.email || "").toLowerCase() === email);
    if (!match || match.password !== password || (match.role || "user") !== "admin") {
      return showToast("Invalid admin credentials.", "error");
    }

    writeJson(STORAGE_KEYS.user, { email: match.email, username: match.username, role: "admin" });
    showToast("Admin login successful.", "success");
    setTimeout(() => redirect("admin.html"), 700);
  });
}

function initRegisterPage() {
  const form = document.getElementById("registerForm");
  if (!form) return;
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = String(document.getElementById("registerName")?.value || "").trim();
    const email = String(document.getElementById("registerEmail")?.value || "").trim().toLowerCase();
    const password = String(document.getElementById("registerPassword")?.value || "");
    const role = String(document.getElementById("registerRole")?.value || "user");
    if (!name || !email || !password) return showToast("Please complete all fields.", "error");
    if (password.length < 6) return showToast("Password must be at least 6 characters.", "error");

    const users = readJson(STORAGE_KEYS.users) || [];
    if (users.some((u) => String(u.email || "").toLowerCase() === email)) {
      return showToast("An account with this email already exists.", "error");
    }
    const user = { username: name, email, password, role: role === "admin" ? "admin" : "user", createdAt: Date.now() };
    users.push(user);
    writeJson(STORAGE_KEYS.users, users);
    writeJson(STORAGE_KEYS.user, { username: user.username, email: user.email, role: user.role });
    showToast("Account created. Welcome!", "success");
    setTimeout(() => redirect("index.html"), 800);
  });
}

function initProfilePage() {
  const user = getCurrentUser();
  if (!user) return requireLoginAndRedirect("profile.html");

  const info = document.getElementById("profileInfo");
  const list = document.getElementById("myTickets");
  const logout = document.getElementById("profileLogoutBtn");
  if (!info || !list || !logout) return;

  info.innerHTML = `
    <p><strong>Name:</strong> ${escapeHtml(user.username)}</p>
    <p><strong>Email:</strong> ${escapeHtml(user.email)}</p>
    <p><strong>Role:</strong> ${escapeHtml(user.role || "user")}</p>
  `;

  const tickets = (readJson(STORAGE_KEYS.tickets) || []).filter((t) => t.userEmail === user.email);
  list.innerHTML = tickets.length
    ? tickets
        .map(
          (t) => `
      <article class="ticket-item">
        <p><strong>${escapeHtml(t.from)} → ${escapeHtml(t.to)}</strong></p>
        <p>${escapeHtml(t.date)} • Seat ${t.seatNumber} • $${Number(t.price).toFixed(2)}</p>
      </article>`
        )
        .join("")
    : `<p class="subtle">No tickets yet.</p>`;

  logout.addEventListener("click", () => logoutUser("index.html"));
}

function initAdminPage() {
  const user = getCurrentUser();
  if (!user) return requireLoginAndRedirect("admin.html", "admin-login.html");
  if (user.role !== "admin") {
    showToast("Admin access required.", "error");
    return redirect("index.html");
  }

  const form = document.getElementById("adminTripForm");
  const list = document.getElementById("adminTripList");
  const bookings = document.getElementById("adminBookings");
  if (!form || !list || !bookings) return;
  cityOptions(document.getElementById("adminFrom"));
  cityOptions(document.getElementById("adminTo"));

  const renderAdmin = () => {
    const trips = readJson(STORAGE_KEYS.adminTrips) || [];
    list.innerHTML = trips.length
      ? trips
          .map(
            (t) => `
        <article class="admin-item">
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

    const allTickets = readJson(STORAGE_KEYS.tickets) || [];
    bookings.innerHTML = allTickets.length
      ? allTickets
          .map(
            (b) => `
        <article class="admin-item">
          <p><strong>${escapeHtml(b.userName)}</strong> (${escapeHtml(b.userEmail)})</p>
          <p>${escapeHtml(b.from)} → ${escapeHtml(b.to)} • ${escapeHtml(b.date)} • Seat ${b.seatNumber}</p>
        </article>`
          )
          .join("")
      : `<p class="subtle">No bookings yet.</p>`;
  };
  renderAdmin();

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const editingId = form.getAttribute("data-edit-id");
    const trip = {
      id: editingId || `ADM-${Date.now()}`,
      from: String(document.getElementById("adminFrom")?.value || ""),
      to: String(document.getElementById("adminTo")?.value || ""),
      date: String(document.getElementById("adminDate")?.value || ""),
      departureTime: String(document.getElementById("adminTime")?.value || ""),
      basePrice: Number(document.getElementById("adminPrice")?.value || 0),
      company: String(document.getElementById("adminCompany")?.value || "Admin Bus"),
      duration: "5h 00m",
      image: "https://images.unsplash.com/photo-1464219789935-c2d9d9aba644?auto=format&fit=crop&w=900&q=80",
      departureDateTime: new Date(`${document.getElementById("adminDate")?.value}T${document.getElementById("adminTime")?.value}:00`).toISOString(),
    };
    if (!trip.from || !trip.to || !trip.date || !trip.departureTime || !trip.basePrice || !trip.company) {
      return showToast("Complete all trip fields.", "error");
    }

    const trips = readJson(STORAGE_KEYS.adminTrips) || [];
    const idx = trips.findIndex((t) => t.id === trip.id);
    if (idx >= 0) trips[idx] = trip;
    else trips.push(trip);
    writeJson(STORAGE_KEYS.adminTrips, trips);
    form.removeAttribute("data-edit-id");
    form.reset();
    showToast("Trip saved.", "success");
    renderAdmin();
  });

  list.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const editId = target.getAttribute("data-edit");
    const deleteId = target.getAttribute("data-delete");
    const trips = readJson(STORAGE_KEYS.adminTrips) || [];
    if (deleteId) {
      writeJson(
        STORAGE_KEYS.adminTrips,
        trips.filter((t) => t.id !== deleteId)
      );
      showToast("Trip deleted.", "success");
      renderAdmin();
      return;
    }
    if (editId) {
      const t = trips.find((item) => item.id === editId);
      if (!t) return;
      form.setAttribute("data-edit-id", t.id);
      setInput("adminFrom", t.from);
      setInput("adminTo", t.to);
      setInput("adminDate", t.date);
      setInput("adminTime", t.departureTime);
      setInput("adminPrice", String(t.basePrice));
      setInput("adminCompany", t.company);
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
        <label><span>TC ID</span><input class="tc-no ${invalid("tc")}" value="${escapeHtml(passenger.tc || "")}" placeholder="11 digit TC" />${fieldError("tc")}</label>
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

    if (p.tc && !/^\d{11}$/.test(p.tc)) errors[idx].tc = "TC must be 11 digits";
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

function createSeatMap(trip) {
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
  const tickets = readJson(STORAGE_KEYS.tickets) || [];
  const occupied = tickets.filter(
    (t) =>
      t.from === trip.from &&
      t.to === trip.to &&
      t.date === trip.date &&
      (t.company || "") === (trip.company || "") &&
      Number.isFinite(Number(t.seatNumber))
  );
  occupied.forEach((ticket) => {
    const seat = seats.find((s) => s.number === Number(ticket.seatNumber));
    if (!seat) return;
    seat.status = "occupied";
    seat.gender = null;
  });
  return seats;
}

function syncSelectedSeats(state) {
  state.selectedSeats = state.passengers.filter((p) => p.seatNumber).map((p) => p.seatNumber);
}

function getAvailableTrips(search) {
  const generated = createDefaultTrips(search);
  const admin = (readJson(STORAGE_KEYS.adminTrips) || []).filter(
    (t) => t.from === search.from && t.to === search.to && t.date === search.date
  );
  const merged = [...admin, ...generated];
  const seen = new Set();
  return merged.filter((trip) => {
    if (seen.has(trip.id)) return false;
    seen.add(trip.id);
    return true;
  });
}

function createDefaultTrips(search) {
  const companies = ["Metro Travel", "ExpressLine", "Anatolia Bus", "BlueRoad"];
  const list = [];
  const day = new Date(`${search.date}T08:00:00`);
  for (let i = 0; i < 6; i += 1) {
    const tripDate = new Date(day.getTime() + i * 90 * 60000);
    list.push({
      id: `TRIP-${i + 1}`,
      from: search.from,
      to: search.to,
      date: search.date,
      company: companies[i % companies.length],
      departureTime: `${String(tripDate.getHours()).padStart(2, "0")}:${String(tripDate.getMinutes()).padStart(2, "0")}`,
      departureDateTime: tripDate.toISOString(),
      duration: `${4 + (i % 3)}h ${15 + (i % 3) * 10}m`,
      basePrice: 18 + i * 4,
      image: "https://images.unsplash.com/photo-1464219789935-c2d9d9aba644?auto=format&fit=crop&w=900&q=80",
    });
  }
  return list;
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

function saveTicket(booking, user) {
  const tickets = readJson(STORAGE_KEYS.tickets) || [];
  booking.passengers.forEach((p) => {
    const price = getPassengerPrice(booking, p);
    tickets.push({
      id: `T-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      userEmail: user.email,
      userName: user.username,
      from: booking.search.from,
      to: booking.search.to,
      date: booking.search.date,
      seatNumber: p.seatNumber,
      passengerName: p.name,
      price,
      company: booking.trip.company,
      createdAt: Date.now(),
    });
  });
  writeJson(STORAGE_KEYS.tickets, tickets);
}

function getPassengerPrice(booking, passenger) {
  const seat = createSeatMap(booking.trip).find((s) => s.number === passenger.seatNumber);
  let item = booking.trip.basePrice;
  if (seat && seat.isVip) item += booking.trip.basePrice * VIP_SURCHARGE;
  return item;
}

function renderAuthNav() {
  const root = document.getElementById("authNav");
  if (!root) return;
  const user = getCurrentUser();
  if (user) {
    const adminLink = user.role === "admin" ? `<a href="admin.html">Admin</a><span class="auth-badge">ADMIN</span>` : "";
    root.innerHTML = `
      <a href="profile.html">Profile</a>
      ${adminLink}
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
  const users = readJson(STORAGE_KEYS.users) || [];
  const hasAdmin = users.some((u) => String(u.email || "").toLowerCase() === "admin@busgo.com");
  const hasUser = users.some((u) => String(u.email || "").toLowerCase() === "user@busgo.com");
  if (!hasUser) {
    users.push({
      username: "BusGo User",
      email: "user@busgo.com",
      password: "1234",
      role: "user",
      createdAt: Date.now(),
    });
  }
  if (!hasAdmin) {
    users.push({
      username: "BusGo Admin",
      email: "admin@busgo.com",
      password: "1234",
      role: "admin",
      createdAt: Date.now(),
    });
  }
  writeJson(STORAGE_KEYS.users, users);
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

function logoutUser(target) {
  localStorage.removeItem(STORAGE_KEYS.user);
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
