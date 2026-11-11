package com.splitapp.SplitApp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserEmailConstraintTest {
    private static final String INSERT = """
            insert into users (first_name, last_name, email, password, created_at)
            values ('Jan', 'Kowalski', ?, 'hash', now())
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectEmailWithUpperCaseLettersInTheDatabase() {
        String email = "Upper-" + UUID.randomUUID() + "@Example.com";

        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(INSERT, email));

        assertTrue(ex.getMessage().contains("chk_users_email_lowercase"));
    }

    @Test
    void shouldAcceptLowerCaseEmail() {
        String email = "lower-" + UUID.randomUUID() + "@example.com";

        assertDoesNotThrow(() -> jdbcTemplate.update(INSERT, email));
    }
}
