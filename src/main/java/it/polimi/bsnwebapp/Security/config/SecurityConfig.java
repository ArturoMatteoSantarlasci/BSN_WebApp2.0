package it.polimi.bsnwebapp.Security.config;

import it.polimi.bsnwebapp.Security.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configurazione di Spring Security per gestire l'autenticazione e l'autorizzazione.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;


    /**
     * Configura la catena di filtri di sicurezza HTTP.
     * @param http Istanza di {@link HttpSecurity} utilizzata per configurare la sicurezza.
     * @return Un {@link SecurityFilterChain} configurato.
     * @throws Exception se si verifica un errore nella configurazione.
     */
    //1) API: JWT stateless
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {

        http
                .securityMatcher("/api/**")
                // CSRF disabilitato per API stateless con JWT
                .csrf(csrf -> csrf.disable())

                // (opzionale) CORS: puoi abilitarlo così quando ti servirà
                // .cors(Customizer.withDefaults())

                // Autorizzazioni sulle richieste
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/dev/**").hasAuthority("USER_DEV")
                        .requestMatchers("/api/v1/med/**").hasAuthority("USER_MED")
                        .anyRequest().authenticated()
                )

                // Sessione stateless (niente HttpSession lato server)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Provider di autenticazione (usa il tuo UserDetailsService + PasswordEncoder)
                .authenticationProvider(authenticationProvider)

                // Filtro JWT prima del filtro username/password standard
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 2) WEB: thymeleaf
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // per partire veloce; poi lo riattiviamo
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/error",
                                "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login") // combacia col tuo th:action in login.html
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                );

        return http.build();
    }
}
