package com.css.mallorderagent.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端 SPA 入口。
 */
@Controller
public class IndexController {

    /**
     * 返回打包在应用中的前端 SPA 首页；静态资源不存在时返回 404。
     *
     * @return index.html 静态资源响应
     */
    @GetMapping({"/", "/index.html"})
    public ResponseEntity<Resource> index() {
        Resource resource = new ClassPathResource("static/index.html");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}
