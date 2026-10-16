package com.ritpl.billgen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.CustomerSettings;

/**
 * One settings row per account, keyed by the account id, so {@code findById(customerId)} is the whole
 * lookup. An account that has never saved its preferences has no row at all.
 */
public interface CustomerSettingsRepository extends JpaRepository<CustomerSettings, Long> {
}
