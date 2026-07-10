package com.example.mallordermemory.service;

import com.example.mallordermemory.profile.UserProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileServiceTest {

    @Test
    void formatProfileIncludesVersionAndFields() {
        UserProfile profile = new UserProfile();
        profile.setUserId("u1");
        profile.setVersion(3);
        profile.setConfidence(new BigDecimal("0.820"));
        profile.setOccupation("Java开发工程师");
        profile.setDepartment("技术部");
        profile.setResponseStyle("简洁");
        profile.setProfileJson("{\"interests\":[\"电商\"]");

        String text = new UserProfileService(null).formatProfile(profile);
        assertTrue(text.contains("版本: v3"));
        assertTrue(text.contains("职业: Java开发工程师"));
        assertTrue(text.contains("部门: 技术部"));
    }
}
