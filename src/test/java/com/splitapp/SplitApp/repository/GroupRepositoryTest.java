package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.TestcontainersConfiguration;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GroupRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Test
    void shouldListOnlyTheGroupsOfTheUserNewestFirstWithTheirRole() {
        User caller = saveUser("caller@example.com");
        User other = saveUser("other@example.com");
        saveGroup("Moja", caller);
        ExpenseGroup shared = saveGroup("Wspólna", other);
        shared.addMember(caller, GroupRole.MEMBER);
        groupRepository.saveAndFlush(shared);
        saveGroup("Cudza", other);

        List<GroupSummaryView> result = groupRepository.findSummariesByMemberUserId(caller.getId());

        assertEquals(2, result.size());
        assertEquals("Wspólna", result.get(0).getGroupName());
        assertEquals(GroupRole.MEMBER, result.get(0).getRole());
        assertEquals("Moja", result.get(1).getGroupName());
        assertEquals(GroupRole.OWNER, result.get(1).getRole());
    }

    @Test
    void shouldReturnNothingForAUserWithoutGroups() {
        User loner = saveUser("loner@example.com");
        saveGroup("Cudza", saveUser("owner@example.com"));

        assertTrue(groupRepository.findSummariesByMemberUserId(loner.getId()).isEmpty());
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(new User("Jan", "Kowalski", email, "hash"));
    }

    private ExpenseGroup saveGroup(String name, User owner) {
        ExpenseGroup group = new ExpenseGroup(name, owner);
        group.addMember(owner, GroupRole.OWNER);
        return groupRepository.saveAndFlush(group);
    }
}
