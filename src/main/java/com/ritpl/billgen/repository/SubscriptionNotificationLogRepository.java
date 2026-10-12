package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.SubscriptionNotificationLog;

public interface SubscriptionNotificationLogRepository extends JpaRepository<SubscriptionNotificationLog, Long> {
    @EntityGraph(attributePaths = {"subscription", "customer"})
    List<SubscriptionNotificationLog> findTop30ByCustomer_IdOrderByCreatedAtDesc(Long customerId);
    java.util.Optional<SubscriptionNotificationLog> findByIdAndCustomer_Id(Long id, Long customerId);
    boolean existsBySubscription_IdAndNotificationTypeAndDaysBefore(Long subscriptionId, String notificationType, int daysBefore);
}
