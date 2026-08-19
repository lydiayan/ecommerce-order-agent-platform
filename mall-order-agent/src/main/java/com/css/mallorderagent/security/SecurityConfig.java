package com.css.mallorderagent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.security.core.session.SessionRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "agent.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    SessionRegistry sessionRegistry(FindByIndexNameSessionRepository sessions) {
        return new SpringSessionBackedSessionRegistry(sessions);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(AppUserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    DefaultCookieSerializer cookieSerializer(AuthProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("MALL_AGENT_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(properties.isSecureCookie());
        return serializer;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AppUserRepository users,
                                            ApiTokenRepository tokens,
                                            LoginRateLimiter rateLimiter,
                                            ClientIpResolver clientIpResolver,
                                            SecurityAuditService auditService,
                                            SessionRegistry sessionRegistry,
                                            AuthProperties properties,
                                            ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("XSRF-TOKEN");
        csrf.setHeaderName("X-XSRF-TOKEN");

        http
                .csrf(config -> config
                        .csrfTokenRepository(csrf)
                        .ignoringRequestMatchers("/internal/evaluation/**"))
                .sessionManagement(session -> {
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
                    session.sessionFixation(fixation -> fixation.migrateSession());
                    session.maximumSessions(1).maxSessionsPreventsLogin(false)
                            .sessionRegistry(sessionRegistry);
                })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login.html", "/login.js", "/change-password.html",
                                "/change-password.js", "/style.css", "/auth.css", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/auth/csrf", "/agent/order/health", "/vector/milvus/health").permitAll()
                        .requestMatchers("/auth/me", "/auth/change-password", "/auth/logout",
                                "/auth/impersonation/exit").authenticated()
                        .requestMatchers("/internal/evaluation/**").hasAuthority("EVALUATION_ACT_AS")
                        .requestMatchers("/admin.html", "/admin.js", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/vector/milvus/**").hasAuthority("KNOWLEDGE_ADMIN")
                        .requestMatchers("/agent/demo/reset").hasAuthority("DEMO_RESET")
                        .requestMatchers("/agent/demo/**").hasRole("ADMIN")
                        .requestMatchers("/", "/index.html", "/app.js", "/agent/**")
                            .hasAnyRole("HR", "ENGINEERING", "SALES", "CUSTOMER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/auth/login")
                        .successHandler((request, response, authentication) -> {
                            SecurityUserPrincipal principal = (SecurityUserPrincipal) authentication.getPrincipal();
                            request.getSession().setAttribute(
                                    SessionGuardFilter.AUTHENTICATED_AT, java.time.Instant.now().getEpochSecond());
                            users.recordLoginSuccess(principal.userId());
                            auditService.record("LOGIN_SUCCESS", principal.getUsername(), null,
                                    "SUCCESS", null, request);
                            response.setContentType("application/json");
                            String redirect = principal.passwordChangeRequired() ? "/change-password.html"
                                    : principal.roles().contains("ADMIN") ? "/admin.html" : "/";
                            objectMapper.writeValue(response.getWriter(),
                                    Map.of("code", 200, "message", "success", "redirect", redirect));
                        })
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            String ip = clientIpResolver.resolve(request);
                            try {
                                rateLimiter.recordFailure(ip);
                                if (username != null && !username.isBlank()) {
                                    users.recordLoginFailure(username, properties.getLoginMaxFailures(),
                                            properties.getLoginLockSeconds());
                                }
                            } catch (RuntimeException e) {
                                response.setStatus(503);
                                response.setContentType("application/json");
                                objectMapper.writeValue(response.getWriter(),
                                        Map.of("code", 503, "message", "认证会话服务暂不可用"));
                                return;
                            }
                            auditService.record("LOGIN_FAILURE", username, null,
                                    "DENIED", exception.getClass().getSimpleName(), request);
                            response.setStatus(401);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(),
                                    Map.of("code", 401, "message", "用户名或密码错误，或账户暂时不可用"));
                        }).permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("MALL_AGENT_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            auditService.record("LOGOUT",
                                    authentication != null ? authentication.getName() : null,
                                    null, "SUCCESS", null, request);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(),
                                    Map.of("code", 200, "message", "success"));
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (request.getRequestURI().endsWith(".html") || request.getRequestURI().equals("/")) {
                                response.sendRedirect("/login.html");
                            } else {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                objectMapper.writeValue(response.getWriter(),
                                        Map.of("code", 401, "message", "请先登录"));
                            }
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            auditService.record("ACCESS_DENIED", request.getUserPrincipal() != null
                                            ? request.getUserPrincipal().getName() : null,
                                    request.getRequestURI(), "DENIED", null, request);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getWriter(),
                                    Map.of("code", 403, "message", "没有执行该操作的权限"));
                        }))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'"))
                        .frameOptions(frame -> frame.deny()))
                .addFilterBefore(new LoginThrottleFilter(rateLimiter, clientIpResolver, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ApiTokenAuthenticationFilter(tokens),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionGuardFilter(users, properties, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
