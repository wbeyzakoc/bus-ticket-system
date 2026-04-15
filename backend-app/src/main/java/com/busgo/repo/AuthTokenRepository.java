package com.busgo.repo;

import com.busgo.model.AuthToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
  Optional<AuthToken> findByToken(String token);
  void deleteByToken(String token);
}
