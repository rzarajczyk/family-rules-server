package pl.zarajczyk.familyrules.configuration.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import pl.zarajczyk.familyrules.domain.DevicesService
import pl.zarajczyk.familyrules.domain.UsersService

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun authenticationProvider(
        userDetailsManager: UserDetailsManager,
        passwordEncoder: PasswordEncoder
    ): DaoAuthenticationProvider {
        val authProvider = DaoAuthenticationProvider()
        authProvider.setUserDetailsService(userDetailsManager)
        authProvider.setPasswordEncoder(passwordEncoder)
        return authProvider
    }

    @Bean
    @Order(1)
    fun apiV1FilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { authorize ->
                authorize
                    .anyRequest().permitAll()
            }

        return http.build()
    }

    @Bean
    @Order(2)
    fun apiV2FilterChain(http: HttpSecurity, devicesService: DevicesService): SecurityFilterChain {
        http
            .securityMatcher("/api/v2/**")
            .csrf { it.disable() }
            .addFilterBefore(
                ApiRequestLoggingFilter(),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .addFilterAfter(
                ApiV2KeyAuthFilter(devicesService, excludedUris = setOf("/api/v2/register-instance")),
                ApiRequestLoggingFilter::class.java
            )
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/api/v2/register-instance").permitAll()
                    .anyRequest().authenticated()
            }

        return http.build()
    }

    @Bean
    @Order(3)
    fun integrationApiFilterChain(http: HttpSecurity, usersService: UsersService): SecurityFilterChain {
        http
            .securityMatcher("/integration-api/v1/**")
            .csrf { it.disable() }
            .addFilterBefore(
                ApiRequestLoggingFilter(),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .addFilterAfter(
                IntegrationApiKeyAuthFilter(usersService),
                ApiRequestLoggingFilter::class.java
            )
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authorize ->
                authorize.anyRequest().authenticated()
            }

        return http.build()
    }


    @Bean
    @Order(4)
    fun guiFilterChain(
        http: HttpSecurity,
        userDetailsManager: UserDetailsManager,
        authenticationProvider: DaoAuthenticationProvider
    ): SecurityFilterChain {
        http
            .authenticationProvider(authenticationProvider)
            .csrf { it.disable() }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/bff/**").authenticated()
                    .requestMatchers("/gui/**").permitAll()
                    .requestMatchers("/api/**").permitAll()  // Exclude API endpoints from GUI security
                    .anyRequest().authenticated()
            }
            .rememberMe { rm ->
                rm
                    .key("remember-me")
                    .rememberMeParameter("remember-me")
                    .userDetailsService(userDetailsManager)
            }
            .formLogin { form ->
                form.loginPage("/gui/login.html")
                    .loginProcessingUrl("/bff/security-login")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/gui/index.html", true)
                    .permitAll()
            }

        return http.build()
    }
}