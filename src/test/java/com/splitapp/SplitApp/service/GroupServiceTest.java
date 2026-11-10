package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void shouldCreateGroupSuccessfully() throws Exception {
        User user = createUser();
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(groupRepository.save(any(ExpenseGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponse result = groupService.createGroup("Wyjazd", 1L);

        assertNotNull(result);
        assertEquals("Wyjazd", result.groupName());
        assertEquals(1, result.members().size());
        assertEquals(GroupRole.OWNER, result.members().get(0).role());
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> groupService.createGroup("Wyjazd", 1L));
        verify(groupRepository, never()).save(any(ExpenseGroup.class));
    }

    @Test
    void shouldRejectBlankGroupName() throws Exception {
        Method method = GroupService.class.getMethod("createGroup", String.class, Long.class);

        var violations = validator.forExecutables()
                .validateParameters(groupService, method, new Object[]{" ", 1L});

        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldRejectNullUserId() throws Exception {
        Method method = GroupService.class.getMethod("createGroup", String.class, Long.class);

        var violations = validator.forExecutables()
                .validateParameters(groupService, method, new Object[]{"Wyjazd", null});

        assertFalse(violations.isEmpty());
    }

    private User createUser() throws Exception {
        Constructor<User> constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
