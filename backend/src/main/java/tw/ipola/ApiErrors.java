package tw.ipola;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
class ApiErrors {
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<?> conflict(){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","data_conflict"));}
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ResponseEntity<?> validation(MethodArgumentNotValidException ex){return ResponseEntity.badRequest().body(Map.of("error","invalid_input","fields",ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()).distinct().toList()));}
}
