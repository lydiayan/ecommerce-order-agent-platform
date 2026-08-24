package com.css.mallorderagent.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebConfigTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void proxiedSamePagePostIsNotRejectedByLocalOnlyCorsMapping() throws Exception {
        mockMvc.perform(post("/agent/test")
                        .header("Origin", "https://example.trycloudflare.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({WebConfig.class, TestController.class})
    static class TestWebConfig {
    }

    @RestController
    static class TestController {

        @PostMapping("/agent/test")
        String post() {
            return "ok";
        }
    }
}
