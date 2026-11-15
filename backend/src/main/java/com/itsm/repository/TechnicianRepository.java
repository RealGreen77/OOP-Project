package com.itsm.repository;

import com.itsm.domain.user.Technician;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Technicians (including team leads). Skills are fetched eagerly via an entity graph to avoid N+1 queries. */
public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    @EntityGraph(attributePaths = {"skills", "serviceExpertise"})
    @Query("select t from Technician t where t.active = true order by t.userCode")
    List<Technician> findAllActiveWithSkills();

    @EntityGraph(attributePaths = {"skills", "serviceExpertise"})
    @Query("select t from Technician t order by t.userCode")
    List<Technician> findAllWithSkills();

    @EntityGraph(attributePaths = {"skills", "serviceExpertise", "teamLead"})
    Optional<Technician> findWithSkillsById(Long id);
}
