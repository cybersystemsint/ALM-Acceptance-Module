/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.config;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 *
 * @author jgithu
 */


@Configuration
public class GlobalCorsConfig {


    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        // Without this, the browser can receive Content-Disposition (e.g. on file export/
        // download endpoints) but JS can't read it on cross-origin responses - it silently
        // falls back to a generic filename instead of the server-provided one.
        config.addExposedHeader("Content-Disposition");

        source.registerCorsConfiguration("/**", config);

        // A plain @Bean CorsFilter is only registered for DispatcherType.REQUEST by default,
        // so it's skipped for error-dispatched responses (404s, 500s, etc). That means any
        // endpoint that errors - including one that simply isn't deployed yet - comes back
        // with zero CORS headers, and the browser reports it as "blocked by CORS policy"
        // instead of showing the real underlying error. Registering ERROR (and ASYNC, for
        // endpoints using DeferredResult/async processing) here makes real failures visible
        // as themselves instead of masquerading as CORS problems.
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC));
        registration.setOrder(0);
        return registration;
    }
}
