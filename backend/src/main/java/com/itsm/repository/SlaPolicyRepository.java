package com.itsm.repository;

import com.itsm.domain.incident.Priority;
import com.itsm.domain.sla.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findFirstByServiceIdAndPriorityAndActiveTrue(Long serviceId, Priority priority);

    Optional<SlaPolicy> findFirstByServiceIsNullAndPriorityAndActiveTrue(Priority priority);

    @Query("select p from SlaPolicy p left join fetch p.service order by p.priority, p.service.name nulls first")
    List<SlaPolicy> findAllOrdered();
}
