package com.ritpl.billgen.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.PolicyPage;
import com.ritpl.billgen.entity.PolicyType;

public interface PolicyPageRepository extends JpaRepository<PolicyPage, Long> {
    Optional<PolicyPage> findByPolicyType(PolicyType policyType);
    List<PolicyPage> findByStatusOrderByPolicyTypeAsc(ContentStatus status);
    Optional<PolicyPage> findByPolicyTypeAndStatus(PolicyType policyType, ContentStatus status);
}
