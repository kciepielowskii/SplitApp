package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.GroupResponse;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.PaymentStatus;
import com.splitapp.splitapp.exception.BusinessRuleException;
import com.splitapp.splitapp.exception.GroupAccessDeniedException;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.repository.ExpenseRepository;
import com.splitapp.splitapp.repository.GroupRepository;
import com.splitapp.splitapp.repository.PaymentRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class MembershipService {
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final GroupAccess groupAccess;

    @Transactional
    public GroupResponse transferOwnership(@NotNull Long groupId, @NotNull Long currentUserId,
                                           @NotNull Long newOwnerId) {
        ExpenseGroup group = loadGroup(groupId, currentUserId);

        if (!group.isOwner(currentUserId)) {
            throw new GroupAccessDeniedException("Only the group owner can transfer ownership");
        }
        if (currentUserId.equals(newOwnerId)) {
            throw new BusinessRuleException("You are already the owner of this group");
        }
        if (!group.isMember(newOwnerId)) {
            throw new BusinessRuleException("The new owner must be a member of this group");
        }

        group.transferOwnership(currentUserId, newOwnerId, groupRepository::flush);
        return GroupResponse.from(group);
    }

    @Transactional
    public void leaveGroup(@NotNull Long groupId, @NotNull Long currentUserId) {
        ExpenseGroup group = loadGroup(groupId, currentUserId);

        if (group.isOwner(currentUserId)) {
            throw new BusinessRuleException(
                    "The group owner cannot leave; transfer ownership to another member first");
        }
        if (paymentRepository.existsInvolvingUser(groupId, currentUserId, PaymentStatus.PENDING)) {
            throw new BusinessRuleException(
                    "You have pending payments in this group; wait until they are confirmed or delete them");
        }

        long balance = BalanceCalculator.netCents(
                        expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(groupId),
                        paymentRepository.findAllByGroupIdAndStatus(groupId, PaymentStatus.CONFIRMED))
                .getOrDefault(currentUserId, 0L);
        if (balance != 0) {
            throw new BusinessRuleException("Settle your balance before leaving the group");
        }

        group.removeMember(currentUserId);
    }

    private ExpenseGroup loadGroup(Long groupId, Long currentUserId) {
        groupAccess.requireMemberAndLock(groupId, currentUserId);
        return groupRepository.findWithMembersById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
    }
}
