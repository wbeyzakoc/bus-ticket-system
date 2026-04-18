package com.busgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busgo.dto.TripDtos.AdminTripRequest;
import com.busgo.dto.TripDtos.ChatTripSearchRequest;
import com.busgo.model.Bus;
import com.busgo.model.BusCompany;
import com.busgo.model.City;
import com.busgo.model.Seat;
import com.busgo.model.Trip;
import com.busgo.model.TripStatus;
import com.busgo.model.User;
import com.busgo.repo.BusCompanyRepository;
import com.busgo.repo.BusRepository;
import com.busgo.repo.CityRepository;
import com.busgo.repo.SeatRepository;
import com.busgo.repo.TicketRepository;
import com.busgo.repo.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class TripServiceTest {
  @Test
  void createAdminTripUsesScopedAdminsCompany() {
    CityRepository cityRepository = Mockito.mock(CityRepository.class);
    BusCompanyRepository companyRepository = Mockito.mock(BusCompanyRepository.class);
    BusRepository busRepository = Mockito.mock(BusRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);

    TripService tripService =
        new TripService(
            cityRepository,
            companyRepository,
            busRepository,
            seatRepository,
            tripRepository,
            ticketRepository);

    User admin = new User();
    admin.setCompanyName("Scoped Lines");

    City fromCity = new City();
    fromCity.setName("Istanbul");
    fromCity.setCreatedAt(Instant.now());

    City toCity = new City();
    toCity.setName("Ankara");
    toCity.setCreatedAt(Instant.now());

    BusCompany company = new BusCompany();
    company.setName("Scoped Lines");
    company.setCreatedAt(Instant.now());

    Bus bus = new Bus();
    bus.setCompany(company);
    bus.setSeatRows(10);
    bus.setSeatCols(4);
    bus.setCreatedAt(Instant.now());

    when(cityRepository.findByNameIgnoreCase("Istanbul")).thenReturn(Optional.of(fromCity));
    when(cityRepository.findByNameIgnoreCase("Ankara")).thenReturn(Optional.of(toCity));
    when(companyRepository.findByNameIgnoreCase("Scoped Lines")).thenReturn(Optional.of(company));
    when(busRepository.findFirstByCompanyOrderByCreatedAtAsc(company)).thenReturn(Optional.of(bus));
    when(seatRepository.findByBus(bus)).thenReturn(List.of(new Seat()));
    when(tripRepository.save(any(com.busgo.model.Trip.class)))
        .thenAnswer(
            invocation -> {
              com.busgo.model.Trip saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });

    var response =
        tripService.createAdminTrip(
            admin,
            new AdminTripRequest(
                "Istanbul",
                "Ankara",
                "2026-05-01",
                "10:30",
                BigDecimal.valueOf(450),
                "Other Company"));

    assertEquals("Scoped Lines", response.company());
    verify(companyRepository).findByNameIgnoreCase("Scoped Lines");
  }

  @Test
  void searchTripsForChatReturnsExactMatchesFirst() {
    CityRepository cityRepository = Mockito.mock(CityRepository.class);
    BusCompanyRepository companyRepository = Mockito.mock(BusCompanyRepository.class);
    BusRepository busRepository = Mockito.mock(BusRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);

    TripService tripService =
        new TripService(
            cityRepository,
            companyRepository,
            busRepository,
            seatRepository,
            tripRepository,
            ticketRepository);

    City fromCity = city("Istanbul");
    City toCity = city("Ankara");
    Trip exactTrip = trip("Istanbul Express", fromCity, toCity, LocalDateTime.parse("2026-05-01T10:30:00"), BigDecimal.valueOf(450));

    when(cityRepository.findByNameIgnoreCase("Istanbul")).thenReturn(Optional.of(fromCity));
    when(cityRepository.findByNameIgnoreCase("Ankara")).thenReturn(Optional.of(toCity));
    when(tripRepository.findByFromCityAndToCityAndDepartureTimeBetween(
            any(City.class), any(City.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of(exactTrip));

    var response = tripService.searchTripsForChat(new ChatTripSearchRequest("Istanbul", "Ankara", "2026-05-01", false));

    assertEquals("exact", response.matchType());
    assertEquals(1, response.trips().size());
    assertEquals("Istanbul Express", response.trips().getFirst().company());
  }

  @Test
  void searchTripsForChatFallsBackToNearestTrips() {
    CityRepository cityRepository = Mockito.mock(CityRepository.class);
    BusCompanyRepository companyRepository = Mockito.mock(BusCompanyRepository.class);
    BusRepository busRepository = Mockito.mock(BusRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);

    TripService tripService =
        new TripService(
            cityRepository,
            companyRepository,
            busRepository,
            seatRepository,
            tripRepository,
            ticketRepository);

    City fromCity = city("Istanbul");
    City toCity = city("Ankara");
    Trip nearestTrip = trip("Closest Bus", fromCity, toCity, LocalDateTime.parse("2026-05-02T08:00:00"), BigDecimal.valueOf(500));
    Trip laterTrip = trip("Later Bus", fromCity, toCity, LocalDateTime.parse("2026-05-04T09:00:00"), BigDecimal.valueOf(520));

    when(cityRepository.findByNameIgnoreCase("Istanbul")).thenReturn(Optional.of(fromCity));
    when(cityRepository.findByNameIgnoreCase("Ankara")).thenReturn(Optional.of(toCity));
    when(tripRepository.findByFromCityAndToCityAndDepartureTimeBetween(
            any(City.class), any(City.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of());
    when(tripRepository.findByFromCityAndToCityOrderByDepartureTimeAsc(fromCity, toCity))
        .thenReturn(List.of(laterTrip, nearestTrip));

    var response = tripService.searchTripsForChat(new ChatTripSearchRequest("Istanbul", "Ankara", "2026-05-01", false));

    assertEquals("nearest", response.matchType());
    assertEquals(2, response.trips().size());
    assertEquals("Closest Bus", response.trips().getFirst().company());
  }

  @Test
  void searchTripsForChatRejectsUnknownCity() {
    CityRepository cityRepository = Mockito.mock(CityRepository.class);
    BusCompanyRepository companyRepository = Mockito.mock(BusCompanyRepository.class);
    BusRepository busRepository = Mockito.mock(BusRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);

    TripService tripService =
        new TripService(
            cityRepository,
            companyRepository,
            busRepository,
            seatRepository,
            tripRepository,
            ticketRepository);

    when(cityRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());

    assertThrows(
        ResponseStatusException.class,
        () -> tripService.searchTripsForChat(new ChatTripSearchRequest("Unknown", "Ankara", "2026-05-01", false)));
  }

  @Test
  void searchTripsForChatReturnsNearestUpcomingTripsWhenDateIsMissing() {
    CityRepository cityRepository = Mockito.mock(CityRepository.class);
    BusCompanyRepository companyRepository = Mockito.mock(BusCompanyRepository.class);
    BusRepository busRepository = Mockito.mock(BusRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);

    TripService tripService =
        new TripService(
            cityRepository,
            companyRepository,
            busRepository,
            seatRepository,
            tripRepository,
            ticketRepository);

    City fromCity = city("Istanbul");
    City toCity = city("Van");
    LocalDateTime tomorrowMorning = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime tomorrowEvening = tomorrowMorning.withHour(18);
    LocalDateTime nextWeek = LocalDateTime.now().plusDays(7).withHour(8).withMinute(0).withSecond(0).withNano(0);
    Trip nearestMorning = trip("Van Morning", fromCity, toCity, tomorrowMorning, BigDecimal.valueOf(900));
    Trip nearestEvening = trip("Van Evening", fromCity, toCity, tomorrowEvening, BigDecimal.valueOf(950));
    Trip laterTrip = trip("Van Next Week", fromCity, toCity, nextWeek, BigDecimal.valueOf(990));

    when(cityRepository.findByNameIgnoreCase("Istanbul")).thenReturn(Optional.of(fromCity));
    when(cityRepository.findByNameIgnoreCase("Van")).thenReturn(Optional.of(toCity));
    when(tripRepository.findByFromCityAndToCityOrderByDepartureTimeAsc(fromCity, toCity))
        .thenReturn(List.of(nearestMorning, nearestEvening, laterTrip));

    var response = tripService.searchTripsForChat(new ChatTripSearchRequest("Istanbul", "Van", null, true));

    assertEquals("route-nearest", response.matchType());
    assertEquals(tomorrowMorning.toLocalDate().toString(), response.requestedDate());
    assertEquals(2, response.trips().size());
    assertEquals("Van Morning", response.trips().getFirst().company());
  }

  private static City city(String name) {
    City city = new City();
    city.setName(name);
    city.setCreatedAt(Instant.now());
    return city;
  }

  private static Trip trip(String companyName, City fromCity, City toCity, LocalDateTime departureTime, BigDecimal basePrice) {
    BusCompany company = new BusCompany();
    company.setName(companyName);
    company.setCreatedAt(Instant.now());

    Trip trip = new Trip();
    trip.setId(UUID.randomUUID());
    trip.setCompany(company);
    trip.setFromCity(fromCity);
    trip.setToCity(toCity);
    trip.setDepartureTime(departureTime);
    trip.setDurationMinutes(300);
    trip.setBasePrice(basePrice);
    trip.setStatus(TripStatus.SCHEDULED);
    trip.setCreatedAt(Instant.now());
    return trip;
  }
}
