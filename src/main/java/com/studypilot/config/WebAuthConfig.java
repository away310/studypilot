package com.studypilot.config;

import com.studypilot.adapter.web.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册登录校验拦截器（覆盖静态页面与全部 API）。 */
@Configuration
public class WebAuthConfig implements WebMvcConfigurer {

    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login.html",
                        "/app.css",
                        "/login.css",
                        "/favicon.ico",
                        "/error"
                );
    }
}
