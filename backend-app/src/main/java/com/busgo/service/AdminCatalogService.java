package com.busgo.service;

import com.busgo.dto.AdminCatalogDtos.CityDto;
import com.busgo.dto.AdminCatalogDtos.CityRequest;
import com.busgo.dto.AdminCatalogDtos.CompanyDto;
import com.busgo.dto.AdminCatalogDtos.CompanyRequest;
import com.busgo.model.BusCompany;
import com.busgo.model.City;
import com.busgo.model.User;
import com.busgo.repo.BusCompanyRepository;
import com.busgo.repo.CityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCatalogService {
  private static final String COMPANY_MANAGER_EMAIL = "admin@busgo.com";
  private static final String COMPANY_MANAGER_ONLY_MESSAGE =
      "Only admin@busgo.com can manage companies";

  private final CityRepository cityRepository;
  private final BusCompanyRepository companyRepository;

  public AdminCatalogService(CityRepository cityRepository, BusCompanyRepository companyRepository) {
    this.cityRepository = cityRepository;
    this.companyRepository = companyRepository;
  }

  public List<CityDto> listCities() {
    return cityRepository.findAll().stream().map(this::toDto).toList();
  }

  public List<CompanyDto> listCompanies() {
    return companyRepository.findAll().stream().map(this::toDto).toList();
  }

  public List<CompanyDto> listCompanies(User admin) {
    if (canManageCompanies(admin)) {
      return companyRepository.findAll().stream().map(this::toDto).toList();
    }
    String managedCompany = managedCompanyName(admin);
    if (managedCompany != null) {
      return List.of(toDto(ensureCompany(managedCompany)));
    }
    return companyRepository.findAll().stream().map(this::toDto).toList();
  }

  @Transactional
  public CityDto createCity(CityRequest request) {
    String name = normalizeName(request.name());
    if (cityRepository.findByNameIgnoreCase(name).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "City already exists");
    }
    City city = new City();
    city.setName(name);
    city.setCountryCode(normalizeCountry(request.countryCode()));
    city.setCreatedAt(Instant.now());
    return toDto(cityRepository.save(city));
  }

  @Transactional
  public CompanyDto createCompany(User admin, CompanyRequest request) {
    requireCompanyManager(admin);

    String name = normalizeName(request.name());
    if (companyRepository.findByNameIgnoreCase(name).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Company already exists");
    }

    BusCompany company = new BusCompany();
    company.setName(name);
    company.setPhone(valueOrNull(request.phone()));
    company.setEmail(valueOrNull(request.email()));
    company.setLogoUrl(valueOrNull(request.logoUrl()));
    company.setCreatedAt(Instant.now());
    return toDto(companyRepository.save(company));
  }

  @Transactional
  public void deleteCity(String id) {
    UUID uuid = parseUuid(id);
    if (!cityRepository.existsById(uuid)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found");
    }
    try {
      cityRepository.deleteById(uuid);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "City is in use");
    }
  }

  @Transactional
  public void deleteCompany(User admin, String id) {
    requireCompanyManager(admin);
    UUID uuid = parseUuid(id);
    if (!companyRepository.existsById(uuid)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
    }
    try {
      companyRepository.deleteById(uuid);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Company is in use");
    }
  }

  private CityDto toDto(City city) {
    return new CityDto(city.getId().toString(), city.getName(), city.getCountryCode());
  }

  private CompanyDto toDto(BusCompany company) {
    return new CompanyDto(
        company.getId().toString(),
        company.getName(),
        company.getPhone(),
        company.getEmail(),
        company.getLogoUrl());
  }

  private String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
    }
    return name.trim();
  }

  private String normalizeCountry(String code) {
    if (code == null || code.isBlank()) return null;
    return code.trim().toUpperCase(Locale.US);
  }

  private String valueOrNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private void requireCompanyManager(User admin) {
    if (!canManageCompanies(admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, COMPANY_MANAGER_ONLY_MESSAGE);
    }
  }

  private boolean canManageCompanies(User admin) {
    if (admin == null || admin.getEmail() == null) return false;
    return admin.getEmail().trim().toLowerCase(Locale.US).equals(COMPANY_MANAGER_EMAIL);
  }

  private BusCompany ensureCompany(String name) {
    String normalized = normalizeName(name);
    return companyRepository.findByNameIgnoreCase(normalized)
        .orElseGet(
            () -> {
              BusCompany company = new BusCompany();
              company.setName(normalized);
              company.setCreatedAt(Instant.now());
              return companyRepository.save(company);
            });
  }

  private String managedCompanyName(User admin) {
    if (admin == null || admin.getCompanyName() == null) return null;
    String normalized = admin.getCompanyName().trim();
    return normalized.isBlank() ? null : normalized;
  }

  private UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id");
    }
  }
}
