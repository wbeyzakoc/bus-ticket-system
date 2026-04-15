package com.busgo.controller;

import com.busgo.dto.AdminCatalogDtos.CityDto;
import com.busgo.dto.AdminCatalogDtos.CityRequest;
import com.busgo.dto.AdminCatalogDtos.CompanyDto;
import com.busgo.dto.AdminCatalogDtos.CompanyRequest;
import com.busgo.dto.BookingDtos.TicketDto;
import com.busgo.dto.TripDtos.AdminTripRequest;
import com.busgo.dto.TripDtos.TripDto;
import com.busgo.service.AuthService;
import com.busgo.service.AdminCatalogService;
import com.busgo.service.BookingService;
import com.busgo.service.TripService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final AuthService authService;
  private final TripService tripService;
  private final BookingService bookingService;
  private final AdminCatalogService catalogService;

  public AdminController(
      AuthService authService,
      TripService tripService,
      BookingService bookingService,
      AdminCatalogService catalogService) {
    this.authService = authService;
    this.tripService = tripService;
    this.bookingService = bookingService;
    this.catalogService = catalogService;
  }

  @GetMapping("/trips")
  public List<TripDto> listTrips(HttpServletRequest request) {
    authService.requireAdmin(request);
    return tripService.listAdminTrips();
  }

  @PostMapping("/trips")
  public TripDto createTrip(@Valid @RequestBody AdminTripRequest request, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    return tripService.createAdminTrip(request);
  }

  @PutMapping("/trips/{id}")
  public TripDto updateTrip(@PathVariable String id, @Valid @RequestBody AdminTripRequest request, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    return tripService.updateAdminTrip(id, request);
  }

  @DeleteMapping("/trips/{id}")
  public ResponseEntity<Void> deleteTrip(@PathVariable String id, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    tripService.deleteAdminTrip(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/tickets")
  public List<TicketDto> listAllTickets(HttpServletRequest request) {
    authService.requireAdmin(request);
    return bookingService.listAllTickets();
  }

  @GetMapping("/cities")
  public List<CityDto> listCities(HttpServletRequest request) {
    authService.requireAdmin(request);
    return catalogService.listCities();
  }

  @PostMapping("/cities")
  public CityDto createCity(@Valid @RequestBody CityRequest request, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    return catalogService.createCity(request);
  }

  @DeleteMapping("/cities/{id}")
  public ResponseEntity<Void> deleteCity(@PathVariable String id, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    catalogService.deleteCity(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/companies")
  public List<CompanyDto> listCompanies(HttpServletRequest request) {
    authService.requireAdmin(request);
    return catalogService.listCompanies();
  }

  @PostMapping("/companies")
  public CompanyDto createCompany(@Valid @RequestBody CompanyRequest request, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    return catalogService.createCompany(request);
  }

  @DeleteMapping("/companies/{id}")
  public ResponseEntity<Void> deleteCompany(@PathVariable String id, HttpServletRequest httpRequest) {
    authService.requireAdmin(httpRequest);
    catalogService.deleteCompany(id);
    return ResponseEntity.noContent().build();
  }
}
