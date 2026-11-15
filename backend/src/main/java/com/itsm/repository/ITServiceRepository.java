package com.itsm.repository;

import com.itsm.domain.catalog.ITService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ITServiceRepository extends JpaRepository<ITService, Long> {

    Optional<ITService> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = {"dependencies", "defaultCategory"})
    @Query("select s from ITService s order by s.name")
    List<ITService> findAllWithDependencies();

    @EntityGraph(attributePaths = {"dependencies", "defaultCategory"})
    @Query("select s from ITService s where s.active = true order by s.name")
    List<ITService> findActiveWithDependencies();
}
