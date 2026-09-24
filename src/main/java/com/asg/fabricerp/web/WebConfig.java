package com.asg.fabricerp.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LayoutModelInterceptor layoutModel;

    public WebConfig(LayoutModelInterceptor layoutModel) {
        this.layoutModel = layoutModel;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(layoutModel).excludePathPatterns("/api/**", "/css/**", "/js/**", "/images/**");
    }
}
