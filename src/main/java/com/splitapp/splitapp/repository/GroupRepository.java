package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<ExpenseGroup> findWithMembersById(Long id);

    boolean existsByIdAndMembersUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ExpenseGroup g where g.id = :id")
    Optional<ExpenseGroup> findByIdForUpdate(@Param("id") Long id);

    // one query for the whole list: the group columns and the caller's role, newest group first
    @Query("""
            select g.id as id, g.groupName as groupName, g.groupDescription as groupDescription,
                   m.role as role
            from ExpenseGroup g join g.members m
            where m.user.id = :userId
            order by g.id desc
            """)
    List<GroupSummaryView> findSummariesByMemberUserId(@Param("userId") Long userId);
}
