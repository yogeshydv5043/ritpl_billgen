package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.Notification;
import com.ritpl.billgen.entity.NotificationTriggerEvent;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByDeletedAtIsNullOrderByUpdatedAtDesc();
    List<Notification> findByActiveTrueAndDeletedAtIsNull();
    List<Notification> findByTriggerEventAndActiveTrueAndDeletedAtIsNull(NotificationTriggerEvent trigger);
    Optional<Notification> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByNameIgnoreCase(String name);
    long countByActiveTrueAndDeletedAtIsNull();
}
