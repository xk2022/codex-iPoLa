package tw.ipola;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tw.ipola.ApiService.*;

@RestController
@RequestMapping("/api")
class ApiController {
 private final ApiService service;
 ApiController(ApiService service){this.service=service;}
 @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
 Member register(@Valid @RequestBody Register input){return service.register(input);}
 @PostMapping("/auth/login")
 Token login(@Valid @RequestBody Login input){return service.login(input);}
 @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
 void logout(@RequestHeader("Authorization") String auth){service.logout(auth);}
 @GetMapping("/auth/me")
 Member me(Authentication auth){return service.me(auth);}
 @PostMapping("/animals") @ResponseStatus(HttpStatus.CREATED)
 Animal createAnimal(@Valid @RequestBody AnimalInput input,Authentication auth){return service.createAnimal(input,auth);}
 @GetMapping("/animals")
 Page<Animal> animals(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){return service.animals(limit,offset);}
 @GetMapping("/animals/{id}")
 Animal animal(@PathVariable long id){return service.animal(id);}
 @PostMapping("/observations") @ResponseStatus(HttpStatus.CREATED)
 Map<String,Long> createObservation(@Valid @RequestBody ObservationInput input,Authentication auth){return service.createObservation(input,auth);}
 @GetMapping("/observations")
 Page<Observation> observations(@RequestParam(required=false) Long animalId,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset,Authentication auth){return service.observations(animalId,limit,offset,auth);}
 @GetMapping("/members/{id}/observations")
 Page<Observation> memberObservations(@PathVariable long id,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset,Authentication auth){return service.memberObservations(id,limit,offset,auth);}
}
