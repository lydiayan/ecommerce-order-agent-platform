/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.mallordercmpserver.util;

public class UserInfoHolder {

    //pass userInfo to tool methods, use InheritableThreadLocal can pass userInfo to child threads
    public static final InheritableThreadLocal<String> userInfoContext = new InheritableThreadLocal<>();

    public static String getUserInfo() {
        return userInfoContext.get();
    }
    public static void setUserInfo(String userInfo) {
        userInfoContext.set(userInfo);
    }


}
