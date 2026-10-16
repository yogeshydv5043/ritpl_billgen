package com.ritpl.billgen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.AppInfo;

/** The single branding row. There is nothing to search, so there is nothing else here. */
public interface AppInfoRepository extends JpaRepository<AppInfo, Long> {
    boolean existsById(Long id);
}
