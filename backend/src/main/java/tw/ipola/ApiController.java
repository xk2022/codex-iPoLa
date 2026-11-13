package tw.ipola;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tw.ipola.ApiService.*;

@RestController
@RequestMapping("/api")
class ApiController {
 private final ApiService service;
 private final RecordOperations records;
 ApiController(ApiService service,RecordOperations records){this.service=service;this.records=records;}
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
 /** Replace owner-editable fields with optimistic concurrency protection. */
 @PutMapping("/observations/{id}")
 Map<String,Long> updateObservation(@PathVariable long id,@Valid @RequestBody ObservationUpdate input,Authentication auth){return records.update(id,input,auth);}
 /** Remove the owner's record immediately; the version prevents stale deletion. */
 @DeleteMapping("/observations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 void deleteObservation(@PathVariable long id,@RequestParam long version,Authentication auth){records.delete(id,version,auth);}
 /** Upload one bounded image, replacing an existing private photo only on success. */
 @PostMapping(value="/observations/{id}/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 Map<String,Long> uploadPhoto(@PathVariable long id,@RequestParam long version,@RequestParam("file") MultipartFile file,Authentication auth){return records.upload(id,version,file,auth);}
 /** Private photos require owner authentication on every request. */
 @GetMapping("/observations/{id}/photo")
 ResponseEntity<byte[]> photo(@PathVariable long id,Authentication auth){return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(records.photo(id,auth));}
 /** Remove both private photo association and any legacy external URL. */
 @DeleteMapping("/observations/{id}/photo")
 Map<String,Long> removePhoto(@PathVariable long id,@RequestParam long version,Authentication auth){return records.removePhoto(id,version,auth);}

}
