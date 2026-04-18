package com.busgo.controller;

import com.busgo.dto.TripDtos.ChatTripSearchRequest;
import com.busgo.dto.TripDtos.ChatTripSearchResponse;
import com.busgo.dto.TripDtos.NearestTripsResponse;
import com.busgo.dto.TripDtos.TripDto;
import jakarta.validation.Valid;
import com.busgo.service.TripService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {
  private final TripService tripService;

  public TripController(TripService tripService) {
    this.tripService = tripService;
  }

  @GetMapping
  public List<TripDto> searchTrips(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam String date) {
    return tripService.searchTrips(from, to, date);
  }

  @GetMapping("/nearest")
  public NearestTripsResponse nearestTrips(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam String date) {
    return tripService.findNearestTrips(from, to, date);
  }

  @PostMapping("/chat-search")
  public ChatTripSearchResponse chatSearch(@Valid @RequestBody ChatTripSearchRequest request) {
    return tripService.searchTripsForChat(request);
  }

  @GetMapping("/{tripId}/seats")
  public List<Integer> getOccupiedSeats(@PathVariable String tripId) {
    return tripService.getOccupiedSeats(tripId);
  }
}
