package com.example.mallorder.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceTokenFilterTest {

    private final ServiceTokenFilter filter = new ServiceTokenFilter("test-service-token");

    @Test
    void rejectsMissingCredentialAndAcceptsMatchingCredential() throws Exception {
        MockHttpServletRequest deniedRequest = new MockHttpServletRequest("GET", "/orders/ORD1");
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        filter.doFilter(deniedRequest, deniedResponse, new MockFilterChain());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, deniedResponse.getStatus());

        MockHttpServletRequest allowedRequest = new MockHttpServletRequest("GET", "/orders/ORD1");
        allowedRequest.addHeader("Authorization", "Bearer test-service-token");
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        filter.doFilter(allowedRequest, allowedResponse, new MockFilterChain());
        assertEquals(HttpServletResponse.SC_OK, allowedResponse.getStatus());
    }

    @Test
    void keepsHealthProbePublic() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }
}
