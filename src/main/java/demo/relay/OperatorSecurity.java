package demo.relay;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Configuration
@EnableMethodSecurity(jsr250Enabled=true)
public class OperatorSecurity {
    @Bean InMemoryUserDetailsManager operators() {
        var encoder=new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
            User.withUsername("viewer").password("{bcrypt}"+encoder.encode("relay-demo")).roles("VIEWER").build(),
            User.withUsername("responder").password("{bcrypt}"+encoder.encode("relay-demo")).roles("RESPONDER").build(),
            User.withUsername("commander").password("{bcrypt}"+encoder.encode("relay-demo")).roles("COMMANDER").build(),
            User.withUsername("presenter").password("{bcrypt}"+encoder.encode("relay-demo")).roles("PRESENTER").build()
        );
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a->a
            .requestMatchers("/api/session", "/api/csrf").permitAll()
            .requestMatchers("/api/environment/**").hasRole("PRESENTER")
            .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/**").authenticated()
            .requestMatchers("/api/checkout").authenticated()
            .requestMatchers("/api/incidents").hasAnyRole("RESPONDER","COMMANDER","PRESENTER")
            .requestMatchers("/api/**").hasAnyRole("RESPONDER","COMMANDER")
            .anyRequest().permitAll())
            .formLogin(f->f.loginProcessingUrl("/api/login").successHandler((q,r,a)->r.setStatus(204))
                .failureHandler((q,r,e)->{r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Invalid username or password.\"}");}))
            .logout(l->l.logoutUrl("/api/logout").logoutSuccessHandler((q,r,a)->r.setStatus(204)))
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->{r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Sign in to Relay.\"}");})
                .accessDeniedHandler((q,r,x)->{r.setStatus(403);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Your role does not permit this action, or your session needs refreshing.\"}");}))
            .build();
    }
    static boolean role(String role) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));
    }
    static String actor() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth==null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")?null:auth.getName();
    }
    static boolean canRepair(String action) {
        return role("COMMANDER") || (role("RESPONDER") && !Set.of("ROLLBACK_CHECKOUT","RESTORE_DB_LINK","RESTORE_DB_DNS").contains(action));
    }
    static void requireRepair(String action) {
        if(!canRepair(action)) throw new org.springframework.security.access.AccessDeniedException("This repair requires an authorized operator; rollback, DNS and network changes require an incident commander.");
    }
}

@RestController
class SessionController {
    @GetMapping("/api/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/api/session") Map<String,Object> session() {
        String actor=OperatorSecurity.actor();
        return Map.of("username",actor==null?"":actor,"roles",List.of("VIEWER","RESPONDER","COMMANDER","PRESENTER").stream().filter(OperatorSecurity::role).toList());
    }
}
