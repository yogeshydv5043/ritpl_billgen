package com.ritpl.billgen.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
}
