package com.splitapp.splitapp.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProblemJsonTest {

    @Test
    void shouldEscapeEveryField() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/a\"b\\c");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemJson.write(request, response, HttpStatus.BAD_REQUEST, "T \"x\"", "D \\ \"y\"");

        String body = response.getContentAsString();
        assertEquals("T \"x\"", JsonPath.<String>read(body, "$.title"));
        assertEquals("D \\ \"y\"", JsonPath.<String>read(body, "$.detail"));
        assertEquals("/a\"b\\c", JsonPath.<String>read(body, "$.instance"));
        assertEquals(400, JsonPath.<Integer>read(body, "$.status"));
    }
}
