package pl.macieserafin.hourforge.config;

import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Answers rejected input briefly. Spring's own handling writes every rejected value to the log, which would copy
 * people's text (and megabytes of it) into log files; here only the names of the fields are logged.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RejectedInputAdvice {
    private static final Logger log = LoggerFactory.getLogger(RejectedInputAdvice.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException exception) {
        var fields = exception.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).distinct().toList();
        log.info("Input rejected: fields={}", fields);
        return answer(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> invalid(HandlerMethodValidationException exception) {
        log.info("Input rejected: parameters={}", exception.getParameterValidationResults().size());
        return answer(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException exception) {
        var tooLarge = Stream.iterate((Throwable) exception, cause -> cause.getCause() != null && cause.getCause() != cause, Throwable::getCause)
                .anyMatch(cause -> cause instanceof RequestSizeLimitFilter.BodyTooLargeException)
                || exception.getCause() instanceof RequestSizeLimitFilter.BodyTooLargeException;
        log.info("Body rejected: {}", tooLarge ? "too large" : "unreadable");
        return answer(tooLarge ? HttpStatus.CONTENT_TOO_LARGE : HttpStatus.BAD_REQUEST);
    }

    /** Two writers changed the same row at once; the later one is told to reload instead of overwriting. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> changedMeanwhile() {
        return answer(HttpStatus.CONFLICT);
    }

    private static ResponseEntity<Map<String, Object>> answer(HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", status.getReasonPhrase()));
    }
}
