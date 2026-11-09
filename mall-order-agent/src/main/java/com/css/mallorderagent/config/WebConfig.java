package com.css.mallorderagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/style.css", "/app.js", "/favicon.ico")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/login.html", "/login.js", "/change-password.html",
                        "/change-password.js", "/admin.html", "/admin.js", "/auth.css")
                .addResourceLocations("classpath:/static/");
    }
}
