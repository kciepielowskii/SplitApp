package com.splitapp.splitapp.service;

import com.splitapp.splitapp.TestcontainersConfiguration;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Calls the real bean through the Spring proxy, so it fails if @Validated is removed from GroupService.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GroupServiceValidationTest {
    @Autowired
    private GroupService groupService;

    @Test
    void shouldRejectBlankGroupName() {
        assertThrows(ConstraintViolationException.class,
                () -> groupService.createGroup(" ", null, 1L));
    }

    @Test
    void shouldRejectNullUserId() {
        assertThrows(ConstraintViolationException.class,
                () -> groupService.createGroup("Wyjazd", null, null));
    }
}
