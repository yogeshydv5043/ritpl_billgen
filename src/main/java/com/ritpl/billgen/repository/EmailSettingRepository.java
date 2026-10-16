package com.ritpl.billgen.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.EmailSetting;

/** The one transport row, keyed so a save cannot create a second one. */
public interface EmailSettingRepository extends JpaRepository<EmailSetting, Long> {
}
