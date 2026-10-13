package com.ritpl.billgen.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.SubscriptionNotificationLog;

public interface SubscriptionNotificationLogRepository extends JpaRepository<SubscriptionNotificationLog, Long> {
    @EntityGraph(attributePaths = {"subscription", "customer"})
    List<SubscriptionNotificationLog> findTop30ByCustomer_IdOrderByCreatedAtDesc(Long customerId);
    java.util.Optional<SubscriptionNotificationLog> findByIdAndCustomer_Id(Long id, Long customerId);
    long countByCustomer_IdAndStatusIn(Long customerId, java.util.List<com.ritpl.billgen.entity.NotificationStatus> statuses);

    /**
     * Duplicate-reminder guard for one subscription and reminder period. This is an explicit query
     * rather than a derived one: the trailing {@code DaysBefore} would otherwise be parsed as the
     * property {@code days} combined with the {@code Before} keyword and fail at startup.
     */
    @Query("select case when count(n) > 0 then true else false end from SubscriptionNotificationLog n"
            + " where n.subscription.id = :subscriptionId"
            + " and n.notificationType = :notificationType"
            + " and n.daysBefore = :daysBefore")
    boolean existsBySubscription_IdAndNotificationTypeAndDaysBefore(@Param("subscriptionId") Long subscriptionId,
            @Param("notificationType") String notificationType, @Param("daysBefore") int daysBefore);
}
