package com.kongklang.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final BusinessCsrfInterceptor businessCsrf;

    public WebConfig(LoginInterceptor loginInterceptor, BusinessCsrfInterceptor businessCsrf) {
        this.businessCsrf = businessCsrf;
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(loginInterceptor);
        registry.addInterceptor(businessCsrf).addPathPatterns("/**");
    }
}
