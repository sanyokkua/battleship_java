package ua.kostenko.battleship.app.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import ua.kostenko.battleship.app.observability.CorrelationIdFilter;
import ua.kostenko.battleship.app.web.dto.Problem;
import ua.kostenko.battleship.app.web.dto.ProblemCode;
import ua.kostenko.battleship.app.web.dto.Violation;
import ua.kostenko.battleship.application.result.ApplicationFailure;

/**
 * Turns every failure into one generated {@link Problem} served as {@code application/problem+json}. The title is a
 * fixed text per code; exception messages, stack traces and request content never reach the document (R49, R50).
 */
@RestControllerAdvice
public class ProblemAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(ProblemAdvice.class);

    private static final String ONE_OF_NO_MATCH = "Failed deserialization for Command";

    private record Entry(int status, String title) {}

    private static final Map<ProblemCode, Entry> TABLE = Map.ofEntries(
            Map.entry(ProblemCode.MALFORMED_REQUEST, new Entry(400, "Malformed request")),
            Map.entry(ProblemCode.SESSION_REQUIRED, new Entry(401, "Session required")),
            Map.entry(ProblemCode.REQUEST_SECURITY_REJECTED, new Entry(403, "Request rejected")),
            Map.entry(ProblemCode.GAME_UNAVAILABLE, new Entry(404, "Game unavailable")),
            Map.entry(ProblemCode.INVITATION_UNAVAILABLE, new Entry(409, "Invitation unavailable")),
            Map.entry(ProblemCode.ACTION_NOT_ALLOWED, new Entry(409, "Action not allowed")),
            Map.entry(ProblemCode.PLACEMENT_OUT_OF_BOUNDS, new Entry(409, "Ship is out of bounds")),
            Map.entry(ProblemCode.PLACEMENT_OVERLAP, new Entry(409, "Ships may not overlap")),
            Map.entry(ProblemCode.PLACEMENT_TOUCHING, new Entry(409, "Ships may not touch")),
            Map.entry(ProblemCode.RANDOM_ARRANGEMENT_FAILED, new Entry(409, "Random arrangement failed")),
            Map.entry(ProblemCode.TARGET_ALREADY_FIRED, new Entry(409, "Target already fired")),
            Map.entry(ProblemCode.GAME_EXPIRED, new Entry(410, "Game expired")),
            Map.entry(ProblemCode.PAYLOAD_TOO_LARGE, new Entry(413, "Payload too large")),
            Map.entry(ProblemCode.UNSUPPORTED_MEDIA_TYPE, new Entry(415, "Unsupported media type")),
            Map.entry(ProblemCode.VALIDATION_FAILED, new Entry(422, "Validation failed")),
            Map.entry(ProblemCode.RATE_LIMIT_EXCEEDED, new Entry(429, "Too many requests")),
            Map.entry(ProblemCode.INTERNAL_ERROR, new Entry(500, "Internal error")),
            Map.entry(ProblemCode.SERVICE_UNAVAILABLE, new Entry(503, "Service unavailable")));

    /** Builds the problem document for a code; also the entry point for filters that answer outside MVC. */
    static Problem problem(
            ProblemCode code, String correlationId, List<Violation> violations, Integer retryAfterSeconds) {
        Entry entry = TABLE.get(code);
        Problem problem = new Problem()
                .title(entry.title())
                .status(entry.status())
                .code(code)
                .correlationId(correlationId)
                .retryAfterSeconds(retryAfterSeconds);
        return problem.violations(violations == null || violations.isEmpty() ? null : violations);
    }

    /**
     * Writes the problem document for a code on a response that never reaches MVC, such as a rejection by a security
     * filter, through the same builder and mapper as the advice so there is one problem shape.
     */
    public static void writeProblem(
            HttpServletRequest request, HttpServletResponse response, ProblemCode code, ObjectMapper wireMapper)
            throws IOException {
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        Problem problem = problem(code, correlationId, null, null);
        LOG.info("problem code={} status={} correlationId={}", code.getValue(), problem.getStatus(), correlationId);
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        wireMapper.writeValue(response.getOutputStream(), problem);
    }

    @ExceptionHandler(ApplicationFailure.class)
    ResponseEntity<Problem> applicationFailure(HttpServletRequest request, ApplicationFailure failure) {
        ProblemCode code;
        try {
            code = ProblemCode.fromValue(failure.code());
        } catch (IllegalArgumentException unknownCode) {
            return respond(request, ProblemCode.INTERNAL_ERROR, null, null, unknownCode);
        }
        List<Violation> violations;
        try {
            violations = failure.field() == null
                    ? null
                    : List.of(
                            new Violation().field(failure.field()).rule(Violation.RuleEnum.fromValue(failure.rule())));
        } catch (IllegalArgumentException unknownRule) {
            return respond(request, ProblemCode.INTERNAL_ERROR, null, null, unknownRule);
        }
        return respond(request, code, violations, failure.retryAfterSeconds(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Problem> unreadableBody(HttpServletRequest request, HttpMessageNotReadableException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof JsonMappingException mapping) {
            return respond(request, ProblemCode.VALIDATION_FAILED, List.of(violationOf(mapping)), null, null);
        }
        if (cause != null
                && cause.getClass() == IOException.class
                && String.valueOf(cause.getMessage()).startsWith(ONE_OF_NO_MATCH)) {
            return respond(request, ProblemCode.VALIDATION_FAILED, List.of(unknownCommandType()), null, null);
        }
        return respond(request, ProblemCode.MALFORMED_REQUEST, null, null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> constraintViolations(HttpServletRequest request, MethodArgumentNotValidException failure) {
        List<Violation> violations = failure.getBindingResult().getAllErrors().stream()
                .map(error -> error.unwrap(ConstraintViolation.class))
                .map(violation -> violationOf(violation, ""))
                .sorted(Comparator.comparing(Violation::getField))
                .toList();
        return respond(request, ProblemCode.VALIDATION_FAILED, violations, null, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Problem> unsupportedMediaType(HttpServletRequest request) {
        return respond(request, ProblemCode.UNSUPPORTED_MEDIA_TYPE, null, null, null);
    }

    /** Spring MVC's own refusals (unknown path, wrong method, missing parameter) are all client errors. */
    @ExceptionHandler(ServletException.class)
    ResponseEntity<Problem> servletFailure(HttpServletRequest request, ServletException failure) {
        ProblemCode code = failure instanceof ErrorResponse refusal
                        && refusal.getStatusCode().is4xxClientError()
                ? ProblemCode.MALFORMED_REQUEST
                : ProblemCode.INTERNAL_ERROR;
        return respond(request, code, null, null, code == ProblemCode.INTERNAL_ERROR ? failure : null);
    }

    /**
     * An event stream that ended underneath an open response (its client went away): the response is already
     * committed, so there is nothing to answer and nothing went wrong on our side. Streams never time out.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void streamEnded() {}

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(HttpServletRequest request, Exception failure) {
        return respond(request, ProblemCode.INTERNAL_ERROR, null, null, failure);
    }

    private ResponseEntity<Problem> respond(
            HttpServletRequest request,
            ProblemCode code,
            List<Violation> violations,
            Integer retryAfterSeconds,
            Exception cause) {
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        Problem problem = problem(code, correlationId, violations, retryAfterSeconds);
        if (cause == null) {
            LOG.info("problem code={} status={} correlationId={}", code.getValue(), problem.getStatus(), correlationId);
        } else {
            LOG.error(
                    "problem code={} status={} correlationId={} cause={}",
                    code.getValue(),
                    problem.getStatus(),
                    correlationId,
                    cause.getClass().getName());
        }
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfterSeconds != null) {
            response.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString());
        }
        return response.body(problem);
    }

    private static Violation violationOf(JsonMappingException failure) {
        String pointer = pointerOf(failure);
        Violation.RuleEnum rule = Violation.RuleEnum.INVALID_TYPE;
        if (failure instanceof UnrecognizedPropertyException) {
            rule = Violation.RuleEnum.UNKNOWN_FIELD;
        } else if (failure instanceof InvalidFormatException invalid
                && invalid.getTargetType().isEnum()) {
            rule = Violation.RuleEnum.UNKNOWN_VALUE;
        } else if (failure instanceof InvalidFormatException) {
            rule = Violation.RuleEnum.INVALID_FORMAT;
        } else if (failure.getClass() == JsonMappingException.class && pointer.equals("/command")) {
            return unknownCommandType();
        }
        return new Violation().field(pointer).rule(rule);
    }

    /** The generated oneOf deserializer fails without a path when the discriminator is missing or unknown. */
    private static Violation unknownCommandType() {
        return new Violation().field("/command/type").rule(Violation.RuleEnum.UNKNOWN_VALUE);
    }

    /** Maps a Bean Validation violation to the contract's rule and a pointer made of property names only. */
    static Violation violationOf(ConstraintViolation<?> violation, String pointerPrefix) {
        StringBuilder pointer = new StringBuilder(pointerPrefix);
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getName() != null) {
                pointer.append('/').append(node.getName());
            }
            if (node.getIndex() != null) {
                pointer.append('/').append(node.getIndex());
            }
        }
        Violation.RuleEnum rule =
                switch (violation.getConstraintDescriptor().getAnnotation()) {
                    case NotNull ignored -> Violation.RuleEnum.REQUIRED;
                    case Min ignored -> Violation.RuleEnum.OUT_OF_RANGE;
                    case Max ignored -> Violation.RuleEnum.OUT_OF_RANGE;
                    case Size size ->
                        violation.getInvalidValue() instanceof CharSequence text
                                        && text.toString().codePointCount(0, text.length()) < size.min()
                                ? Violation.RuleEnum.TOO_SHORT
                                : Violation.RuleEnum.TOO_LONG;
                    case Pattern ignored -> Violation.RuleEnum.INVALID_FORMAT;
                    default -> Violation.RuleEnum.INVALID_TYPE;
                };
        return new Violation().field(pointer.toString()).rule(rule);
    }

    /** RFC 6901 pointer built from the Jackson path; only field names and indexes, never values. */
    private static String pointerOf(JsonProcessingException failure) {
        StringBuilder pointer = new StringBuilder();
        if (failure instanceof JsonMappingException mapping) {
            for (JsonMappingException.Reference step : mapping.getPath()) {
                pointer.append('/');
                pointer.append(
                        step.getFieldName() != null
                                ? step.getFieldName().replace("~", "~0").replace("/", "~1")
                                : Integer.toString(step.getIndex()));
            }
        }
        return pointer.toString();
    }
}
