package com.splitapp.splitapp;

import com.jayway.jsonpath.JsonPath;
import com.splitapp.splitapp.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '500ms'")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LockTimeoutTest {
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldAnswer409WhenTheGroupStaysLockedLongerThanTheLockTimeout() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        long userId = register(email);
        long groupId = createGroup(email);
        String url = "/groups/%d/expenses".formatted(groupId);
        String body = """
                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d]}
                """.formatted(userId, userId);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // another transaction holds the row lock while the request waits for it
            int status = holdTheGroupLockWhile(groupId,
                    () -> pool.submit(() -> statusOfPost(email, url, body)).get(10, TimeUnit.SECONDS));

            assertEquals(409, status);
        } finally {
            pool.shutdownNow();
        }

        // the lock is released now, so the same request goes through
        assertEquals(201, statusOfPost(email, url, body));
    }

    private <T> T holdTheGroupLockWhile(long groupId, Callable<T> action) {
        return new TransactionTemplate(transactionManager).execute(transaction -> {
            groupRepository.findByIdForUpdate(groupId).orElseThrow();
            try {
                return action.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private long register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Jan","lastName":"Kowalski","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long createGroup(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/groups")
                        .with(httpBasic(email, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupName\":\"Wyjazd\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private int statusOfPost(String email, String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                        .with(httpBasic(email, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }
}
