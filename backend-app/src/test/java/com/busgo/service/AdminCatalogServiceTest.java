package com.busgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busgo.dto.AdminCatalogDtos.CompanyDto;
import com.busgo.dto.AdminCatalogDtos.CompanyRequest;
import com.busgo.model.BusCompany;
import com.busgo.model.User;
import com.busgo.repo.BusCompanyRepository;
import com.busgo.repo.CityRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {
  @Mock private CityRepository cityRepository;
  @Mock private BusCompanyRepository companyRepository;

  private AdminCatalogService adminCatalogService;

  @BeforeEach
  void setUp() {
    adminCatalogService = new AdminCatalogService(cityRepository, companyRepository);
  }

  @Test
  void listCompaniesReturnsScopedCompanyForNonManagerAdmin() {
    User admin = new User();
    admin.setEmail("companyadmin@busgo.com");
    admin.setCompanyName("Scoped Lines");

    BusCompany company = new BusCompany();
    company.setId(UUID.randomUUID());
    company.setName("Scoped Lines");

    when(companyRepository.findByNameIgnoreCase("Scoped Lines")).thenReturn(Optional.of(company));

    List<CompanyDto> companies = adminCatalogService.listCompanies(admin);

    assertEquals(1, companies.size());
    assertEquals("Scoped Lines", companies.getFirst().name());
  }

  @Test
  void createCompanyRejectsNonManagerAdmin() {
    User admin = new User();
    admin.setEmail("companyadmin@busgo.com");

    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class,
            () ->
                adminCatalogService.createCompany(
                    admin, new CompanyRequest("Metro Bus", "555", "ops@metro.com", "")));

    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    assertEquals("Only admin@busgo.com can manage companies", error.getReason());
    verify(companyRepository, never()).save(any(BusCompany.class));
  }

  @Test
  void createCompanyAllowsAdminBusgoCom() {
    User admin = new User();
    admin.setEmail("admin@busgo.com");

    when(companyRepository.findByNameIgnoreCase("Metro Bus")).thenReturn(Optional.empty());
    when(companyRepository.save(any(BusCompany.class)))
        .thenAnswer(
            invocation -> {
              BusCompany company = invocation.getArgument(0, BusCompany.class);
              company.setId(UUID.randomUUID());
              return company;
            });

    CompanyDto company =
        adminCatalogService.createCompany(
            admin, new CompanyRequest("Metro Bus", "555", "ops@metro.com", ""));

    assertEquals("Metro Bus", company.name());
    assertEquals("555", company.phone());
    assertEquals("ops@metro.com", company.email());
  }

  @Test
  void deleteCompanyRejectsNonManagerAdmin() {
    User admin = new User();
    admin.setEmail("legacyadmin@busgo.com");

    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class,
            () -> adminCatalogService.deleteCompany(admin, UUID.randomUUID().toString()));

    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    assertEquals("Only admin@busgo.com can manage companies", error.getReason());
    verify(companyRepository, never()).deleteById(any(UUID.class));
  }
}
