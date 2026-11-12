package com.splitapp.splitapp.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @Test
    void shouldNormalizeEmailInConstructor() {
        User user = new User("Jan", "Kowalski", "  Jan.Kowalski@Example.COM ", "hash");

        assertEquals("jan.kowalski@example.com", user.getEmail());
    }

    @Test
    void shouldRejectNullEmail() {
        assertThrows(NullPointerException.class,
                () -> new User("Jan", "Kowalski", null, "hash"));
    }

    @Test
    void shouldRejectNullPasswordHash() {
        assertThrows(NullPointerException.class,
                () -> new User("Jan", "Kowalski", "jan@example.com", null));
    }
}
