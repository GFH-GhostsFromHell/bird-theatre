package ca.gfh.birdtheatre;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Consistent safe API errors for account and camera forms, without stack traces. */
@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> status(ResponseStatusException error) {
        String message=error.getReason();
        if(message==null || message.isBlank()) message="The request could not be completed.";
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(Map.of("message",
            "Please check the form fields and try again. Usernames must be 3–32 characters, "
            + "and passwords must be at least 12 characters."));
    }
}
