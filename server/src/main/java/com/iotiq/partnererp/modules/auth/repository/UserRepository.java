package com.iotiq.partnererp.modules.auth.repository;

import com.iotiq.partnererp.modules.auth.entity.User;
import com.iotiq.partnererp.modules.auth.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "role")
    Optional<User> findWithRoleById(UUID id);

    /** Row lock used when a change must be checked against concurrent edits of the same account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(UUID id);

    long countByRoleCodeAndStatus(String roleCode, UserStatus status);

    boolean existsByRoleCode(String roleCode);

    /** Active super admins, locked so two concurrent demotions cannot remove the last one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role.code = 'SUPER_ADMIN' and u.status = :status")
    List<User> lockSuperAdmins(UserStatus status);

    @Query("select u.role.id, count(u) from User u group by u.role.id")
    List<Object[]> countByRole();

    /** Accounts under a temporary sign-in lockout at {@code now}. */
    long countByLockedUntilAfter(Instant now);

    long countByMustChangePasswordTrueAndStatus(UserStatus status);
}
