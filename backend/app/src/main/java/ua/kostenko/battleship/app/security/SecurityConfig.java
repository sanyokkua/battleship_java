package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.HeaderWriterFilter;
import ua.kostenko.battleship.app.config.BattleshipProperties;
import ua.kostenko.battleship.app.web.ProblemAdvice;
import ua.kostenko.battleship.app.web.dto.ProblemCode;
import ua.kostenko.battleship.application.registry.SessionRegistry;

/**
 * The one request-security chain: stateless, no URL authorization (the session filter and the use cases decide who
 * may do what), anti-forgery through the contract's readable cookie, and no CORS at all because the interface and the
 * API share one origin (R35, R36).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            SessionRegistry sessions,
            ObjectMapper wireMapper,
            FixedWindowBuckets buckets,
            BattleshipProperties properties)
            throws Exception {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookiePath("/");
        csrfTokens.setCookieCustomizer(
                cookie -> cookie.httpOnly(false).secure(true).sameSite("Strict"));

        // The plain handler is required: the default Xor handler masks the token per response, so the raw cookie
        // value the contract tells a client to echo would be refused (research.md D26).
        CsrfTokenRequestAttributeHandler plainHandler = new CsrfTokenRequestAttributeHandler();
        plainHandler.setCsrfRequestAttributeName(null);

        return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Headers are written before the chain runs: an event stream commits its response from another
                // thread, so a header write while this thread unwinds the chain races the container's recycling.
                .headers(headers -> headers.withObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                    @Override
                    public <F extends HeaderWriterFilter> F postProcess(F filter) {
                        filter.setShouldWriteHeadersEagerly(true);
                        return filter;
                    }
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(plainHandler))
                .exceptionHandling(handling ->
                        handling.accessDeniedHandler((request, response, denied) -> ProblemAdvice.writeProblem(
                                request, response, ProblemCode.REQUEST_SECURITY_REJECTED, wireMapper)))
                // Refused before the anti-forgery check, size, then site; both run before the rate limit, so a refusal
                // never spends quota.
                .addFilterBefore(new SiteIsolationFilter(properties.publicBaseUrl(), wireMapper), CsrfFilter.class)
                .addFilterBefore(
                        new RequestSizeFilter(properties.maxRequestBodyBytes(), wireMapper), SiteIsolationFilter.class)
                .addFilterAfter(new SessionCookieFilter(sessions, wireMapper), CsrfFilter.class)
                .addFilterAfter(
                        new RateLimitFilter(buckets, properties.rateLimit(), wireMapper), SessionCookieFilter.class)
                .build();
    }
}
