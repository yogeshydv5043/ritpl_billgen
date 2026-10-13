package com.ritpl.billgen.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<Customer> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    List<Customer> findBySystemAccountFalseOrderByCompanyNameAsc();
    List<Customer> findBySystemAccountFalseAndDeletedAtIsNullOrderByCompanyNameAsc();
    Optional<Customer> findByIdAndDeletedAtIsNullAndSystemAccountFalse(Long id);
    Optional<Customer> findByEmailIgnoreCaseAndDeletedAtIsNotNull(String email);
    List<Customer> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();
    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);

    /** Newest live accounts, used by the dashboard's "Top 10 new customers" table. */
    List<Customer> findTop10BySystemAccountFalseAndDeletedAtIsNullOrderByCreatedAtDesc();

    long countBySystemAccountFalseAndDeletedAtIsNullAndStatus(AccountStatus status);
    long countBySystemAccountFalseAndDeletedAtIsNullAndCreatedAtBetween(LocalDateTime from, LocalDateTime until);
    long countBySystemAccountFalseAndDeletedAtIsNullAndStatusAndCreatedAtBetween(
            AccountStatus status, LocalDateTime from, LocalDateTime until);

    /** Accounts with no subscription row at all, live or historical. */
    @Query("select count(c) from Customer c where c.systemAccount = false and c.deletedAt is null "
            + "and not exists (select 1 from CustomerSubscription s where s.customer.id = c.id)")
    long countWithoutAnySubscription();

    /** Accounts whose active term ends inside the window (used by the "expiring soon" KPI). */
    @Query("select count(distinct c.id) from Customer c join CustomerSubscription s on s.customer.id = c.id "
            + "where c.systemAccount = false and c.deletedAt is null and s.status = com.ritpl.billgen.entity.SubscriptionStatus.ACTIVE "
            + "and s.endsOn is not null and s.endsOn between :from and :until")
    long countExpiringBetween(@Param("from") LocalDate from, @Param("until") LocalDate until);

    @Query("select c from Customer c where c.systemAccount = false and c.deletedAt is null "
            + "and (lower(c.companyName) like lower(concat('%', cast(:term as string), '%')) "
            + "or lower(c.contactName) like lower(concat('%', cast(:term as string), '%')) "
            + "or lower(c.email) like lower(concat('%', cast(:term as string), '%'))) order by c.companyName asc")
    List<Customer> searchVisibleGlobal(@Param("term") String term);

    @Query("select c from Customer c where c.systemAccount = false and c.deletedAt is null and "
            + "(lower(c.companyName) like lower(concat('%', cast(:term as string), '%')) "
            + "or lower(c.contactName) like lower(concat('%', cast(:term as string), '%')) "
            + "or lower(c.email) like lower(concat('%', cast(:term as string), '%'))) order by c.companyName asc")
    List<Customer> searchVisible(@Param("term") String term);

    long countBySystemAccountFalse();
    long countBySystemAccountFalseAndDeletedAtIsNull();
}