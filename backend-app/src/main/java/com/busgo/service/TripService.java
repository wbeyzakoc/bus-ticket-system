package com.busgo.service;

import com.busgo.dto.TripDtos.AdminTripRequest;
import com.busgo.dto.TripDtos.NearestTripsResponse;
import com.busgo.dto.TripDtos.TripDto;
import com.busgo.model.Bus;
import com.busgo.model.BusCompany;
import com.busgo.model.City;
import com.busgo.model.Seat;
import com.busgo.model.Trip;
import com.busgo.model.TripStatus;
import com.busgo.repo.BusCompanyRepository;
import com.busgo.repo.BusRepository;
import com.busgo.repo.CityRepository;
import com.busgo.repo.SeatRepository;
import com.busgo.repo.TicketRepository;
import com.busgo.repo.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TripService {
  private static final String DEFAULT_IMAGE =
      "https://images.unsplash.com/photo-1464219789935-c2d9d9aba644?auto=format&fit=crop&w=900&q=80";
  private static final int DEFAULT_ROWS = 10;
  private static final int DEFAULT_COLS = 4;
  private static final Set<Integer> VIP_ROWS = Set.of(1);

  private final CityRepository cityRepository;
  private final BusCompanyRepository companyRepository;
  private final BusRepository busRepository;
  private final SeatRepository seatRepository;
  private final TripRepository tripRepository;
  private final TicketRepository ticketRepository;

  public TripService(
      CityRepository cityRepository,
      BusCompanyRepository companyRepository,
      BusRepository busRepository,
      SeatRepository seatRepository,
      TripRepository tripRepository,
      TicketRepository ticketRepository) {
    this.cityRepository = cityRepository;
    this.companyRepository = companyRepository;
    this.busRepository = busRepository;
    this.seatRepository = seatRepository;
    this.tripRepository = tripRepository;
    this.ticketRepository = ticketRepository;
  }

  public List<TripDto> searchTrips(String from, String to, String date) {
    if (from == null || from.isBlank() || to == null || to.isBlank() || date == null || date.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from, to, and date are required");
    }

    City fromCity = ensureCity(from);
    City toCity = ensureCity(to);
    LocalDate travelDate = parseDate(date);
    LocalDateTime start = travelDate.atStartOfDay();
    LocalDateTime end = travelDate.atTime(LocalTime.MAX);

    List<Trip> trips = tripRepository.findByFromCityAndToCityAndDepartureTimeBetween(fromCity, toCity, start, end);
    return trips.stream().sorted(Comparator.comparing(Trip::getDepartureTime)).map(this::toTripDto).toList();
  }

  public NearestTripsResponse findNearestTrips(String from, String to, String date) {
    if (from == null || from.isBlank() || to == null || to.isBlank() || date == null || date.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from, to, and date are required");
    }

    City fromCity = ensureCity(from);
    City toCity = ensureCity(to);
    LocalDate travelDate = parseDate(date);
    LocalDateTime start = travelDate.plusDays(1).atStartOfDay();
    LocalDateTime end = travelDate.plusDays(7).atTime(LocalTime.MAX);

    List<Trip> windowTrips = tripRepository.findByFromCityAndToCityAndDepartureTimeBetween(fromCity, toCity, start, end);
    if (windowTrips.isEmpty()) {
      return new NearestTripsResponse(null, List.of());
    }

    LocalDate closestDate =
        windowTrips.stream().map(t -> t.getDepartureTime().toLocalDate()).min(LocalDate::compareTo).orElse(null);
    if (closestDate == null) {
      return new NearestTripsResponse(null, List.of());
    }

    List<TripDto> mapped =
        windowTrips.stream()
            .filter(t -> t.getDepartureTime().toLocalDate().equals(closestDate))
            .sorted(Comparator.comparing(Trip::getDepartureTime))
            .map(this::toTripDto)
            .toList();

    return new NearestTripsResponse(closestDate.toString(), mapped);
  }

  public List<TripDto> listAdminTrips() {
    return tripRepository.findAll().stream().sorted(Comparator.comparing(Trip::getDepartureTime)).map(this::toTripDto).toList();
  }

  public TripDto createAdminTrip(AdminTripRequest request) {
    City fromCity = ensureCity(request.from());
    City toCity = ensureCity(request.to());
    BusCompany company = ensureCompany(request.company());
    Bus bus = ensureCompanyBus(company);

    Trip trip = new Trip();
    trip.setCompany(company);
    trip.setBus(bus);
    trip.setFromCity(fromCity);
    trip.setToCity(toCity);
    trip.setDepartureTime(parseDateTime(request.date(), request.departureTime()));
    trip.setDurationMinutes(300);
    trip.setBasePrice(request.basePrice());
    trip.setStatus(TripStatus.SCHEDULED);
    trip.setCreatedAt(Instant.now());
    tripRepository.save(trip);

    return toTripDto(trip);
  }

  public TripDto updateAdminTrip(String id, AdminTripRequest request) {
    Trip trip = tripRepository.findById(parseUuid(id))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

    City fromCity = ensureCity(request.from());
    City toCity = ensureCity(request.to());
    BusCompany company = ensureCompany(request.company());
    Bus bus = ensureCompanyBus(company);

    trip.setFromCity(fromCity);
    trip.setToCity(toCity);
    trip.setCompany(company);
    trip.setBus(bus);
    trip.setDepartureTime(parseDateTime(request.date(), request.departureTime()));
    trip.setBasePrice(request.basePrice());
    if (trip.getDurationMinutes() == null) trip.setDurationMinutes(300);
    tripRepository.save(trip);
    return toTripDto(trip);
  }

  public void deleteAdminTrip(String id) {
    UUID uuid = parseUuid(id);
    try {
      if (!tripRepository.existsById(uuid)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
      }
      tripRepository.deleteById(uuid);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip has existing bookings");
    }
  }

  public List<Integer> getOccupiedSeats(String tripId) {
    UUID uuid = parseUuid(tripId);
    return ticketRepository.findByTrip_Id(uuid).stream().map(t -> t.getSeat().getSeatNumber()).distinct().toList();
  }

  private TripDto toTripDto(Trip trip) {
    String date = trip.getDepartureTime().toLocalDate().toString();
    String departureTime = trip.getDepartureTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    String duration = formatDuration(trip.getDurationMinutes(), trip.getDepartureTime(), trip.getArrivalTime());
    return new TripDto(
        trip.getId().toString(),
        trip.getFromCity().getName(),
        trip.getToCity().getName(),
        date,
        trip.getCompany().getName(),
        departureTime,
        trip.getDepartureTime().toString(),
        duration,
        trip.getBasePrice(),
        DEFAULT_IMAGE,
        true);
  }

  private String formatDuration(Integer minutes, LocalDateTime departure, LocalDateTime arrival) {
    if (minutes != null && minutes > 0) {
      int hours = minutes / 60;
      int mins = minutes % 60;
      return String.format(Locale.US, "%dh %02dm", hours, mins);
    }
    if (departure != null && arrival != null) {
      long diff = java.time.Duration.between(departure, arrival).toMinutes();
      int hours = (int) (diff / 60);
      int mins = (int) (diff % 60);
      return String.format(Locale.US, "%dh %02dm", hours, mins);
    }
    return "5h 00m";
  }

  private City ensureCity(String name) {
    String trimmed = name.trim();
    return cityRepository.findByNameIgnoreCase(trimmed)
        .orElseGet(() -> {
          City city = new City();
          city.setName(trimmed);
          city.setCountryCode("TR");
          city.setCreatedAt(Instant.now());
          return cityRepository.save(city);
        });
  }

  private BusCompany ensureCompany(String name) {
    String trimmed = name.trim();
    return companyRepository.findByNameIgnoreCase(trimmed)
        .orElseGet(() -> {
          BusCompany company = new BusCompany();
          company.setName(trimmed);
          company.setCreatedAt(Instant.now());
          return companyRepository.save(company);
        });
  }

  private Bus ensureCompanyBus(BusCompany company) {
    return busRepository.findFirstByCompanyOrderByCreatedAtAsc(company)
        .map(bus -> {
          ensureSeats(bus);
          return bus;
        })
        .orElseGet(() -> {
          Bus bus = new Bus();
          bus.setCompany(company);
          bus.setPlateNo("BUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.US));
          bus.setModel("Coach");
          bus.setSeatRows(DEFAULT_ROWS);
          bus.setSeatCols(DEFAULT_COLS);
          bus.setCreatedAt(Instant.now());
          Bus saved = busRepository.save(bus);
          ensureSeats(saved);
          return saved;
        });
  }

  private void ensureSeats(Bus bus) {
    if (!seatRepository.findByBus(bus).isEmpty()) return;
    List<Seat> seats = new ArrayList<>();
    for (int row = 1; row <= bus.getSeatRows(); row += 1) {
      for (int col = 1; col <= bus.getSeatCols(); col += 1) {
        int number = (row - 1) * bus.getSeatCols() + col;
        Seat seat = new Seat();
        seat.setBus(bus);
        seat.setSeatNumber(number);
        seat.setRowNo(row);
        seat.setColNo(col);
        seat.setIsVip(VIP_ROWS.contains(row));
        seat.setCreatedAt(Instant.now());
        seats.add(seat);
      }
    }
    seatRepository.saveAll(seats);
  }

  private LocalDate parseDate(String date) {
    try {
      return LocalDate.parse(date);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format");
    }
  }

  private LocalDateTime parseDateTime(String date, String time) {
    try {
      return LocalDateTime.parse(date + "T" + time);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date/time format");
    }
  }

  private UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid trip id");
    }
  }
}
