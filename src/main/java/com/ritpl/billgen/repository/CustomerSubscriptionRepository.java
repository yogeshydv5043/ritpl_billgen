package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.CustomerSubscription;
import com.ritpl.billgen.entity.SubscriptionStatus;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, Long> {

    @Query("select distinct s from CustomerSubscription s left join fetch s.plan p left join fetch p.templates "
            + "where s.customer.id = :customerId order by s.createdAt desc")
    List<CustomerSubscription> findHistoryForCustomer(@Param("customerId") Long customerId);

    Optional<CustomerSubscription> findFirstByCustomer_IdAndStatus(Long customerId, SubscriptionStatus status);

    long countByPlan_IdAndStatus(Long planId, SubscriptionStatus status);

    List<CustomerSubscription> findByStatus(SubscriptionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CustomerSubscription s join fetch s.plan where s.customer.id = :customerId and s.status = :status")
    Optional<CustomerSubscription> findForUpdate(@Param("customerId") Long customerId, @Param("status") SubscriptionStatus status);

    @EntityGraph(attributePaths = {"customer", "plan"})
    List<CustomerSubscription> findByStatusAndEndsOnBetween(SubscriptionStatus status, java.time.LocalDate from, java.time.LocalDate until);

    @EntityGraph(attributePaths = {"customer", "plan"})
    List<CustomerSubscription> findByStatusAndEndsOnBefore(SubscriptionStatus status, java.time.LocalDate day);
}