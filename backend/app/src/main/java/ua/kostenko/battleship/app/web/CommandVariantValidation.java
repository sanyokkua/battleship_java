package ua.kostenko.battleship.app.web;

import jakarta.validation.Validator;
import java.lang.reflect.Type;
import java.util.Comparator;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import ua.kostenko.battleship.app.web.dto.CommandRequest;
import ua.kostenko.battleship.application.result.ApplicationFailure;

/**
 * Bean Validation cascades through {@code CommandRequest.command} but stops at the generated oneOf wrapper, whose
 * payload is an untyped {@code Object}; the chosen command variant is therefore validated here.
 */
@ControllerAdvice
class CommandVariantValidation extends RequestBodyAdviceAdapter {
    private final Validator validator;

    CommandVariantValidation(Validator validator) {
        this.validator = validator;
    }

    @Override
    public boolean supports(
            MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == CommandRequest.class;
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof CommandRequest request
                && request.getCommand() != null
                && request.getCommand().getActualInstance() != null) {
            validator.validate(request.getCommand().getActualInstance()).stream()
                    .map(violation -> ProblemAdvice.violationOf(violation, "/command"))
                    .min(Comparator.comparing(violation -> violation.getField()))
                    .ifPresent(violation -> {
                        throw new ApplicationFailure(
                                "validation-failed",
                                violation.getField(),
                                violation.getRule().getValue(),
                                null);
                    });
        }
        return body;
    }
}
