package com.splitapp.SplitApp;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationFlowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterUserAndCreateGroupForThem() throws Exception {
        String email = uniqueEmail();

        long userId = registerUser(email);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":%d}
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("Wyjazd"))
                .andExpect(jsonPath("$.createdById").value(userId))
                .andExpect(jsonPath("$.members[0].userId").value(userId))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void shouldReturn409WhenRegisteringSameEmailTwice() throws Exception {
        String email = uniqueEmail();
        registerUser(email);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void shouldReturn404WhenCreatingGroupForUnknownUser() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    private long registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String registerBody(String email) {
        return """
                {"firstName":"Jan","lastName":"Kowalski","email":"%s","password":"secret123"}
                """.formatted(email);
    }

    // the database is shared between tests in this class and is not rolled back
    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@example.com";
    }
}
