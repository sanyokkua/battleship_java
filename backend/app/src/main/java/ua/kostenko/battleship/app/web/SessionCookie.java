package ua.kostenko.battleship.app.web;

import org.springframework.http.ResponseCookie;

/** The one spelling of the anonymous session cookie: {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}, no domain. */
public final class SessionCookie {
    public static final String NAME = "__Host-battleship_session";

    private SessionCookie() {}

    /** A session cookie with no expiry attribute, so it lasts as long as the browser keeps it. */
    public static String issued(String value) {
        return base(value).build().toString();
    }

    /** The cookie that removes a session the service no longer knows. */
    public static String cleared() {
        return base("").maxAge(0).build().toString();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Strict");
    }
}
