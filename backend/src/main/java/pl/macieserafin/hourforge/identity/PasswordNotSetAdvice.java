package pl.macieserafin.hourforge.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Gives the missing password its own code, so the frontend can offer to set one instead of asking for it. */
@RestControllerAdvice
public class PasswordNotSetAdvice {
    @ExceptionHandler(PasswordNotSetException.class)
    ResponseEntity<Map<String, String>> passwordNotSet(PasswordNotSetException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("code", PasswordNotSetException.CODE));
    }
}
