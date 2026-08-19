package com.css.mallorderagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/agent/**")
                .allowedOrigins("http://127.0.0.1:8087", "http://localhost:8087")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/style.css", "/app.js", "/favicon.ico")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/login.html", "/login.js", "/change-password.html",
                        "/change-password.js", "/admin.html", "/admin.js", "/auth.css")
                .addResourceLocations("classpath:/static/");
    }
}
