package tw.ipola;

import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Service
class ApiService {
 private final JdbcTemplate db;
 private final PasswordEncoder passwords;
 ApiService(JdbcTemplate db, PasswordEncoder passwords) { this.db=db; this.passwords=passwords; }
 enum Species { CAT, DOG }
 enum Kind { SIGHTING, FEEDING }
 record Register(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=60) String nickname, @NotBlank @Size(min=10,max=72) String password) {}
 record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
 record AnimalInput(@NotNull Species species, @NotBlank @Size(max=80) String name, @NotNull @Size(max=1000) String description) {}
 record ObservationInput(Long animalId, @NotNull Kind kind, @NotNull @PastOrPresent Instant observedAt,
  @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
  @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
  @NotNull @Size(max=1000) String notes,
  @Size(max=1000) @Pattern(regexp="https://[^\\s]+", message="must be an HTTPS URL") String photoUrl) {}
 record Member(long id, String nickname) {}
 record Token(String accessToken, Instant expiresAt, Member member) {}
 record Animal(long id, Species species, String name, String description) {}
 record Observation(long id, Long animalId, Kind kind, Instant observedAt, double latitude, double longitude, String locationPrecision, String notes, String photoUrl, Member recorder) {}
 record Page<T>(List<T> items, int limit, int offset) {}

  @Transactional
 Member register(Register input) {
  checkPassword(input.password());
  long id = insert("INSERT INTO members(email,nickname,password_hash) VALUES (?,?,?)", input.email().toLowerCase(Locale.ROOT), input.nickname(), passwords.encode(input.password()));
  return new Member(id,input.nickname());
 }
  Token login(Login input) {
  var rows = db.queryForList("SELECT id,nickname,password_hash FROM members WHERE email=?", input.email().toLowerCase(Locale.ROOT));
  String stored = rows.isEmpty() ? "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" : (String)rows.getFirst().get("password_hash");
  if (!passwords.matches(input.password(),stored) || rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
  var row=rows.getFirst(); long id=((Number)row.get("id")).longValue();
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
  String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  Instant expires=Instant.now().plus(24,ChronoUnit.HOURS);
  db.update("INSERT INTO access_tokens(token_hash,member_id,expires_at) VALUES (?,?,?)", SecurityConfig.hash(token), id, Timestamp.from(expires));
  return new Token(token,expires,new Member(id,(String)row.get("nickname")));
 }
  void logout(String auth) { db.update("DELETE FROM access_tokens WHERE token_hash=?",SecurityConfig.hash(auth.substring(7))); }
  Member me(Authentication auth) { return db.queryForObject("SELECT id,nickname FROM members WHERE id=?",(r,n)->new Member(r.getLong(1),r.getString(2)),memberId(auth)); }
  Animal createAnimal(AnimalInput input, Authentication auth) {
  long id=insert("INSERT INTO animals(species,name,description,creator_id) VALUES (?,?,?,?)",input.species().name(),input.name(),input.description(),memberId(auth));
  return new Animal(id,input.species(),input.name(),input.description());
 }
  Page<Animal> animals(int limit,int offset) {
  pageCheck(limit,offset);
  return new Page<>(db.query("SELECT id,species,name,description FROM animals ORDER BY id DESC LIMIT ? OFFSET ?", (r,n)->new Animal(r.getLong(1),Species.valueOf(r.getString(2)),r.getString(3),r.getString(4)),limit,offset),limit,offset);
 }
  Animal animal(long id) {
  var rows=db.query("SELECT id,species,name,description FROM animals WHERE id=?",(r,n)->new Animal(r.getLong(1),Species.valueOf(r.getString(2)),r.getString(3),r.getString(4)),id);
  if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Animal not found");return rows.getFirst();
 }
  @Transactional
 Map<String,Long> createObservation(ObservationInput input,Authentication auth) {
  if(input.animalId()!=null) animal(input.animalId());
  long id=insert("INSERT INTO observations(animal_id,member_id,kind,observed_at,latitude,longitude,notes,photo_url) VALUES (?,?,?,?,?,?,?,?)",input.animalId(),memberId(auth),input.kind().name(),Timestamp.from(input.observedAt()),input.latitude(),input.longitude(),input.notes(),input.photoUrl());
  return Map.of("id",id);
 }
  Page<Observation> observations(Long animalId,int limit,int offset,Authentication auth) {
  return observationsFor(animalId,null,limit,offset,auth);
 }
  Page<Observation> memberObservations(long id,int limit,int offset,Authentication auth) {
  return observationsFor(null,id,limit,offset,auth);
 }
 private Page<Observation> observationsFor(Long animalId,Long memberId,int limit,int offset,Authentication auth) {
  pageCheck(limit,offset); boolean precise=auth!=null && auth.getPrincipal() instanceof Long;
  String sql="SELECT o.*,m.nickname FROM observations o JOIN members m ON m.id=o.member_id WHERE 1=1";
  List<Object> args=new ArrayList<>();
  if(animalId!=null){sql+=" AND o.animal_id=?";args.add(animalId);}
  if(memberId!=null){sql+=" AND o.member_id=?";args.add(memberId);}
  sql+=" ORDER BY o.observed_at DESC,o.id DESC LIMIT ? OFFSET ?";args.add(limit);args.add(offset);
  return new Page<>(db.query(sql,(r,n)->{
   double lat=r.getDouble("latitude"),lon=r.getDouble("longitude");
   if(!precise){lat=coarse(lat);lon=coarse(lon);}
   return new Observation(r.getLong("id"),r.getObject("animal_id",Long.class),Kind.valueOf(r.getString("kind")),r.getTimestamp("observed_at").toInstant(),lat,lon,precise?"EXACT":"APPROXIMATE",precise?r.getString("notes"):null,precise?r.getString("photo_url"):null,new Member(r.getLong("member_id"),r.getString("nickname")));
  },args.toArray()),limit,offset);
 }
 private static double coarse(double v){return (Math.floor(v*100)+0.5)/100;}
 private static long memberId(Authentication auth){return (Long)auth.getPrincipal();}
 private static void pageCheck(int limit,int offset){if(limit<1||limit>100||offset<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");}
 private static void checkPassword(String password){if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password exceeds 72 UTF-8 bytes");}
 private long insert(String sql,Object... args){
  var key=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);
  return Objects.requireNonNull(key.getKey()).longValue();
 }
}
