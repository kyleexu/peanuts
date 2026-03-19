package com.ganten.peanuts.market.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置，用于提供静态资源
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 提供 WebSocket 客户端 HTML 页面
        registry.addResourceHandler("/websocket-client.html").addResourceLocations("classpath:/websocket-client.html");
    }
}
