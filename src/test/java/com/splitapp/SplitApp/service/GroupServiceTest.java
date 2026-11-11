package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void shouldCreateGroupSuccessfully() {
        User user = createUser(1L);
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(groupRepository.save(any(ExpenseGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponse result = groupService.createGroup("Wyjazd", null, 1L);

        assertNotNull(result);
        assertEquals("Wyjazd", result.groupName());
        assertEquals(1L, result.createdById());
        assertEquals(1, result.members().size());
        assertEquals(1L, result.members().get(0).userId());
        assertEquals(GroupRole.OWNER, result.members().get(0).role());
    }

    @Test
    void shouldSaveGroupDescription() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createUser(1L)));
        when(groupRepository.save(any(ExpenseGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponse result = groupService.createGroup("Wyjazd", "Weekend w górach", 1L);

        assertEquals("Weekend w górach", result.groupDescription());
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                () -> groupService.createGroup("Wyjazd", null, 1L));

        assertEquals("User not found", ex.getMessage());
        verify(groupRepository, never()).save(any(ExpenseGroup.class));
    }

    @Test
    void shouldRejectGroupNameLongerThan100Characters() throws Exception {
        assertEquals(1, validateCreateGroup("a".repeat(101), null, 1L).size());
    }

    @Test
    void shouldAcceptGroupNameOf100Characters() throws Exception {
        assertTrue(validateCreateGroup("a".repeat(100), null, 1L).isEmpty());
    }

    @Test
    void shouldRejectGroupDescriptionLongerThan255Characters() throws Exception {
        assertEquals(1, validateCreateGroup("Wyjazd", "a".repeat(256), 1L).size());
    }

    private Set<ConstraintViolation<GroupService>> validateCreateGroup(
            String groupName, String groupDescription, Long userId) throws NoSuchMethodException {
        Method method = GroupService.class.getMethod(
                "createGroup", String.class, String.class, Long.class);
        return validator.forExecutables()
                .validateParameters(groupService, method,
                        new Object[]{groupName, groupDescription, userId});
    }

    private User createUser(Long id) {
        User user = new User("Jan", "Kowalski", "jan@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
