package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.kostenko.battleship.app.web.ProblemAdvice;
import ua.kostenko.battleship.app.web.dto.ProblemCode;

/**
 * Refuses a body larger than {@code battleship.max-request-body-bytes} with 413 for every request, the body-less
 * operations included (R40). A declared length is judged up front. A body of unknown length (chunked) is read here, at
 * most the ceiling plus one byte, so an oversize one is refused as the same 413 before any controller runs and a
 * smaller one is replayed to the controller from memory; the ceiling is small, so nothing large is ever buffered.
 */
final class RequestSizeFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ObjectMapper wireMapper;

    RequestSizeFilter(int maxBytes, ObjectMapper wireMapper) {
        this.maxBytes = maxBytes;
        this.wireMapper = wireMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            refuse(request, response);
            return;
        }
        if (declared < 0) {
            byte[] body;
            try {
                body = request.getInputStream().readNBytes(maxBytes + 1);
            } catch (IOException unreadable) {
                // A body that cannot be read (a malformed chunk, a dropped connection) is not a body to pass on.
                ProblemAdvice.writeProblem(request, response, ProblemCode.MALFORMED_REQUEST, wireMapper);
                return;
            }
            if (body.length > maxBytes) {
                refuse(request, response);
                return;
            }
            chain.doFilter(new Replayed(request, body), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemAdvice.writeProblem(request, response, ProblemCode.PAYLOAD_TOO_LARGE, wireMapper);
    }

    /** The already-read body handed on again to whatever reads the request next. */
    private static final class Replayed extends HttpServletRequestWrapper {
        private final byte[] body;

        Replayed(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] target, int offset, int length) {
                    return source.read(target, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("blocking reads only");
                }
            };
        }
    }
}
