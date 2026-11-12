package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.TestcontainersConfiguration;
import com.splitapp.splitapp.dto.PendingInvitationResponse;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.GroupInvitation;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.entity.InvitationStatus;
import com.splitapp.splitapp.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class GroupInvitationRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupInvitationRepository invitationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldLoadMyInvitationsWithGroupAndInviterInOneQuery() {
        for (int i = 1; i <= 3; i++) {
            User owner = userRepository.saveAndFlush(
                    new User("Jan", "Kowalski", "owner" + i + "@example.com", "hash"));
            ExpenseGroup group = new ExpenseGroup("Grupa " + i, owner);
            group.addMember(owner, GroupRole.OWNER);
            groupRepository.saveAndFlush(group);
            invitationRepository.saveAndFlush(
                    new GroupInvitation(group, "friend@example.com", owner));
        }
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // the response is built from the group name and the inviter, as in the service
        List<PendingInvitationResponse> result = invitationRepository
                .findAllByEmailAndStatusOrderByIdDesc("friend@example.com", InvitationStatus.PENDING)
                .stream()
                .map(PendingInvitationResponse::from)
                .toList();

        assertEquals(3, result.size());
        assertEquals("Grupa 3", result.get(0).groupName());
        assertEquals("Grupa 1", result.get(2).groupName());
        // without the entity graph the groups and inviters are fetched one by one (6 fetches)
        assertEquals(0, statistics.getEntityFetchCount());
    }
}
