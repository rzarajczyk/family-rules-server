package pl.zarajczyk.familyrules.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import pl.zarajczyk.familyrules.shared.DataRepository

@Configuration
@EnableWebSecurity
class SecurityConfig {

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
    fun apiV2FilterChain(http: HttpSecurity, dataRepository: DataRepository): SecurityFilterChain {
        http
            .securityMatcher("/api/v2/**")
            .csrf { it.disable() }
            .addFilterBefore(
                ApiV2KeyAuthFilter(dataRepository, excludedUris = setOf("/api/v2/register-instance")),
                UsernamePasswordAuthenticationFilter::class.java
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
    fun guiFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/bff/**").authenticated()
                    .requestMatchers("/gui/**").permitAll()
                    .anyRequest().authenticated()
            }
            .rememberMe { rm ->
                rm
                    .key("remember-me")
                    .rememberMeParameter("remember-me")
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