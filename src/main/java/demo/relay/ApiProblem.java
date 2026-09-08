package demo.relay;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

public class ApiProblem extends RuntimeException {
    final HttpStatus status;
    public ApiProblem(HttpStatus status, String message) { super(message); this.status=status; }
    public static ApiProblem conflict(String message) { return new ApiProblem(HttpStatus.CONFLICT,message); }
    public static ApiProblem bad(String message) { return new ApiProblem(HttpStatus.BAD_REQUEST,message); }
    public static ApiProblem missing() { return new ApiProblem(HttpStatus.NOT_FOUND,"Record not found."); }
}

@RestControllerAdvice
class ProblemAdvice {
    @ExceptionHandler(ApiProblem.class)
    ResponseEntity<?> problem(ApiProblem e) { return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage())); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","Request fields are invalid.")); }
}
