package com.busgo.controller;

import com.busgo.dto.AdminCatalogDtos.CityDto;
import com.busgo.dto.AdminCatalogDtos.CompanyDto;
import com.busgo.service.AdminCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {
  private final AdminCatalogService catalogService;

  public CatalogController(AdminCatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/cities")
  public List<CityDto> listCities() {
    return catalogService.listCities();
  }

  @GetMapping("/companies")
  public List<CompanyDto> listCompanies() {
    return catalogService.listCompanies();
  }
}
