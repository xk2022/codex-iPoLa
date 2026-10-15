package tw.ipola;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean org.springframework.security.core.userdetails.UserDetailsService disabledDefaultUsers() {
  return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username); };
 }
 @Bean SecurityFilterChain security(HttpSecurity http, JdbcTemplate db) throws Exception {
  return http.csrf(c -> c.disable())
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a -> a
    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/animals", "/api/animals/*", "/api/observations", "/api/members/*/observations").permitAll()
    .anyRequest().authenticated())
   .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
    res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"error\":\"authentication_required\"}");
   }))
   .addFilterBefore(new OncePerRequestFilter() {
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
     String auth = req.getHeader("Authorization");
     if (auth != null) {
      if (!auth.startsWith("Bearer ") || auth.length() > 200) { res.sendError(401); return; }
      var ids = db.query("SELECT member_id FROM access_tokens WHERE token_hash=? AND expires_at>?", (r,n) -> r.getLong(1), hash(auth.substring(7)), Timestamp.from(Instant.now()));
      if (ids.isEmpty()) { res.sendError(401); return; }
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ids.getFirst(), null, List.of()));
     }
     chain.doFilter(req,res);
    }
   }, UsernamePasswordAuthenticationFilter.class).build();
 }
 static String hash(String token) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
  catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
}
