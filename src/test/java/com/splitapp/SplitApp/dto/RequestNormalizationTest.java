package com.splitapp.SplitApp.dto;

import com.splitapp.SplitApp.entity.ExpenseCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestNormalizationTest {

    @Test
    void shouldTrimGroupNameAndDescription() {
        CreateGroupRequest request = new CreateGroupRequest("  Wyjazd ", "  Góry ", 1L);

        assertEquals("Wyjazd", request.groupName());
        assertEquals("Góry", request.groupDescription());
    }

    @Test
    void shouldTurnBlankGroupDescriptionIntoNull() {
        CreateGroupRequest request = new CreateGroupRequest("Wyjazd", "   ", 1L);

        assertNull(request.groupDescription());
    }

    @Test
    void shouldTrimExpenseDescriptionAndTurnBlankIntoNull() {
        CreateExpenseRequest trimmed = new CreateExpenseRequest(BigDecimal.TEN,
                ExpenseCategory.FOOD, "  Obiad ", 1L, List.of(1L));
        CreateExpenseRequest blank = new CreateExpenseRequest(BigDecimal.TEN,
                ExpenseCategory.FOOD, "  ", 1L, List.of(1L));

        assertEquals("Obiad", trimmed.description());
        assertNull(blank.description());
    }
}
