package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.UserRole;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByCustomer_Id(Long customerId);
    Optional<AppUser> findByCustomer_Id(Long customerId);
    List<AppUser> findAllByRole(UserRole role);
}