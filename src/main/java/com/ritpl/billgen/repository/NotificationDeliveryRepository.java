package com.ritpl.billgen.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.DeliveryStatus;
import com.ritpl.billgen.entity.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    Optional<NotificationDelivery> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "customer")
    @Query("select d from NotificationDelivery d where (:customerId is null or d.customer.id = :customerId) "
            + "and (cast(:trigger as string) is null or d.triggerEvent = :trigger) "
            + "and (:status is null or d.status = :status) "
            + "and (cast(:from as timestamp) is null or d.createdAt >= :from) order by d.createdAt desc")
    Page<NotificationDelivery> search(@Param("customerId") Long customerId, @Param("trigger") String trigger,
            @Param("status") DeliveryStatus status, @Param("from") Instant from, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    List<NotificationDelivery> findTop50ByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = "customer")
    List<NotificationDelivery> findTop50ByNotification_IdOrderByCreatedAtDesc(Long notificationId);

    long countByStatus(DeliveryStatus status);
    long countByCustomer_IdAndCreatedAtAfter(Long customerId, Instant from);
}
