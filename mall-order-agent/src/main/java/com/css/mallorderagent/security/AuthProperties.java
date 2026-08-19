package com.css.mallorderagent.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "agent.auth")
public class AuthProperties {

    private boolean enabled = true;
    private String demoInitialPassword = "DemoLogin@2026!";
    private String adminInitialPassword = "";
    private String evaluationToken = "";
    private long absoluteSessionSeconds = 28_800;
    private int loginMaxFailures = 5;
    private long loginLockSeconds = 900;
    private List<String> trustedProxies = new ArrayList<>(List.of("127.0.0.1", "::1"));
    private boolean secureCookie;
    private String serviceToken = "";
    private String mcpServiceToken = "";
    private boolean demoImpersonationEnabled;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDemoInitialPassword() { return demoInitialPassword; }
    public void setDemoInitialPassword(String value) { this.demoInitialPassword = value; }
    public String getAdminInitialPassword() { return adminInitialPassword; }
    public void setAdminInitialPassword(String value) { this.adminInitialPassword = value; }
    public String getEvaluationToken() { return evaluationToken; }
    public void setEvaluationToken(String value) { this.evaluationToken = value; }
    public long getAbsoluteSessionSeconds() { return absoluteSessionSeconds; }
    public void setAbsoluteSessionSeconds(long value) { this.absoluteSessionSeconds = value; }
    public int getLoginMaxFailures() { return loginMaxFailures; }
    public void setLoginMaxFailures(int value) { this.loginMaxFailures = value; }
    public long getLoginLockSeconds() { return loginLockSeconds; }
    public void setLoginLockSeconds(long value) { this.loginLockSeconds = value; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> value) { this.trustedProxies = value != null ? value : List.of(); }
    public boolean isSecureCookie() { return secureCookie; }
    public void setSecureCookie(boolean value) { this.secureCookie = value; }
    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String value) { this.serviceToken = value; }
    public String getMcpServiceToken() { return mcpServiceToken; }
    public void setMcpServiceToken(String value) { this.mcpServiceToken = value; }
    public boolean isDemoImpersonationEnabled() { return demoImpersonationEnabled; }
    public void setDemoImpersonationEnabled(boolean value) { this.demoImpersonationEnabled = value; }
}
