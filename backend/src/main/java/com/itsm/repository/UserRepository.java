package com.itsm.repository;

import com.itsm.domain.user.Administrator;
import com.itsm.domain.user.ITManager;
import com.itsm.domain.user.TeamLead;
import com.itsm.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * // [Pattern: Repository] Persistence is hidden behind an interface; Spring Data generates the implementation.
 * // [OOP: Polymorphism] Queries such as {@code select a from Administrator a} return subclasses (ITManager) too.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserCode(String userCode);

    @Query("select count(u) from User u where type(u) = :type")
    long countByType(@Param("type") Class<? extends User> type);

    @Query("select a from Administrator a where a.active = true")
    List<Administrator> findActiveAdministrators();

    @Query("select m from ITManager m where m.active = true")
    List<ITManager> findActiveItManagers();

    @Query("select t from TeamLead t where t.active = true")
    List<TeamLead> findActiveTeamLeads();

    @Query("""
            select u from User u
            where (:q is null or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.userCode) like lower(concat('%', cast(:q as string), '%')))
            order by u.userCode
            """)
    List<User> search(@Param("q") String query);
}
