package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.GroupInvitation;
import com.splitapp.SplitApp.entity.InvitationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    boolean existsByGroupIdAndEmailAndStatus(Long groupId, String email, InvitationStatus status);

    Optional<GroupInvitation> findByIdAndGroupId(Long id, Long groupId);

    Optional<GroupInvitation> findByIdAndEmailAndStatus(Long id, String email,
                                                        InvitationStatus status);

    List<GroupInvitation> findAllByGroupIdAndStatusOrderByIdDesc(Long groupId,
                                                                 InvitationStatus status);

    // the response shows the group name and the inviter, so load both in the same query
    @EntityGraph(attributePaths = {"group", "invitedBy"})
    List<GroupInvitation> findAllByEmailAndStatusOrderByIdDesc(String email,
                                                               InvitationStatus status);
}
