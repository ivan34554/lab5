package com.example.lab5.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ SecurityProperties.class, JwtProperties.class })
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiAuthenticationEntryPoint entryPoint,
                                                   ApiAccessDeniedHandler accessDeniedHandler,
                                                   JwtService jwtService,
                                                   AppUserDetailsService userDetailsService) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers((RequestMatcher) SecurityConfig::skipsCsrf)
                .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService, entryPoint),
                BasicAuthenticationFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh")
                    .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()

                .requestMatchers(HttpMethod.GET, "/api/users").hasAuthority(Authority.USER_MANAGE)
                .requestMatchers(HttpMethod.POST, "/api/users/*/block", "/api/users/*/unblock", "/api/users/*/role")
                    .hasAuthority(Authority.USER_MANAGE)
                .requestMatchers(HttpMethod.DELETE, "/api/users/*").hasAuthority(Authority.USER_MANAGE)
                .requestMatchers(HttpMethod.GET, "/api/users/*").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/users/*").authenticated()

                .requestMatchers(HttpMethod.GET,
                        "/api/podcasts", "/api/podcasts/*",
                        "/api/episodes", "/api/episodes/*",
                        "/api/audio-files", "/api/audio-files/*",
                        "/api/publication-slots", "/api/publication-slots/*")
                    .authenticated()

                .requestMatchers(HttpMethod.POST,
                        "/api/podcasts/*/archive", "/api/podcasts/*/restore", "/api/publication-slots/*/cancel")
                    .hasAnyAuthority(Authority.CONTENT_WRITE, Authority.PODCAST_MODERATE)

                .requestMatchers(HttpMethod.POST,
                        "/api/podcasts", "/api/episodes", "/api/episodes/*/publish-now",
                        "/api/audio-files", "/api/audio-files/*/make-main",
                        "/api/publication-slots", "/api/publication-slots/*/publish")
                    .hasAuthority(Authority.CONTENT_WRITE)
                .requestMatchers(HttpMethod.PATCH,
                        "/api/podcasts/*", "/api/episodes/*", "/api/audio-files/*", "/api/publication-slots/*")
                    .hasAuthority(Authority.CONTENT_WRITE)
                .requestMatchers(HttpMethod.DELETE,
                        "/api/podcasts/*", "/api/episodes/*", "/api/audio-files/*", "/api/publication-slots/*")
                    .hasAuthority(Authority.CONTENT_WRITE)

                .anyRequest().denyAll());
        return http.build();
    }

    private static boolean skipsCsrf(HttpServletRequest request) {
        if (JwtAuthenticationFilter.hasBearerHeader(request)) {
            return true;
        }
        String path = request.getRequestURI();
        return HttpMethod.POST.matches(request.getMethod())
                && ("/auth/login".equals(path) || "/auth/refresh".equals(path));
    }
}
