package tw.ipola;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Serializes owner writes and tracks the minimum data needed to reapply deletions after restore. */
@Service
class RecordOperationsImpl implements RecordOperations {
 private final JdbcTemplate db;
 private final ApiService api;
 private final Path directory;
 RecordOperationsImpl(JdbcTemplate db, ApiService api, @Value("${ipola.photos.directory:/workspace/ipola-private/photos}") String directory) {
  this.db=db;this.api=api;this.directory=Path.of(directory).toAbsolutePath().normalize();
 }
 private record Owned(long version,String photoKey) {}
 private long member(Authentication auth) {
  if(auth==null || !(auth.getPrincipal() instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  return (Long)auth.getPrincipal();
 }
 private Owned owned(long id,Authentication auth,boolean lock) {
  var rows=db.query("SELECT version,photo_key FROM observations WHERE id=? AND member_id=?"+(lock?" FOR UPDATE":""),
   (r,n)->new Owned(r.getLong(1),r.getString(2)),id,member(auth));
  if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return rows.getFirst();
 }
 private void version(Owned row,long version) {
  if(version<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(row.version()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Record changed; reload before editing");
 }
 private void audit(long id,long member,String action,long version) {
  db.update("INSERT INTO observation_audit(observation_id,member_id,action,record_version,occurred_at) VALUES (?,?,?,?,?)",id,member,action,version,Timestamp.from(Instant.now()));
 }
 @Override @Transactional public Map<String,Long> update(long id,ApiService.ObservationUpdate input,Authentication auth) {
  Owned row=owned(id,auth,true);version(row,input.version());
  if(input.animalId()!=null)api.animal(input.animalId());
  db.update("UPDATE observations SET animal_id=?,kind=?,observed_at=?,latitude=?,longitude=?,notes=?,version=version+1 WHERE id=? AND member_id=? AND version=?",
   input.animalId(),input.kind().name(),Timestamp.from(input.observedAt()),input.latitude(),input.longitude(),input.notes(),id,member(auth),input.version());
  audit(id,member(auth),"UPDATE",row.version()+1);
  return Map.of("id",id,"version",row.version()+1);
 }
 @Override @Transactional public void delete(long id,long version,Authentication auth) {
  Owned row=owned(id,auth,true);version(row,version);
  // No sensitive record payload is copied into the deletion ledger.
  audit(id,member(auth),"DELETE",version+1);
  db.update("DELETE FROM observations WHERE id=? AND member_id=?",id,member(auth));
  removeAfterCommit(row.photoKey());
 }
 @Override @Transactional public Map<String,Long> upload(long id,long version,MultipartFile file,Authentication auth) {
  Owned row=owned(id,auth,true);version(row,version);
  byte[] jpeg=PrivateImage.decode(file);
  String key=UUID.randomUUID()+".jpg";
  try {
   Files.createDirectories(directory);
   Files.setPosixFilePermissions(directory,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
   Files.createFile(path(key),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
   Files.write(path(key),jpeg,StandardOpenOption.WRITE);
  } catch(IOException e) { erase(key);throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Private photo storage unavailable"); }
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status) { if(status!=STATUS_COMMITTED)erase(key); }
  });
  db.update("UPDATE observations SET photo_key=?,photo_url=NULL,version=version+1 WHERE id=? AND member_id=?",key,id,member(auth));
  audit(id,member(auth),"PHOTO_UPLOAD",version+1);
  removeAfterCommit(row.photoKey());
  return Map.of("id",id,"version",version+1);
 }
 @Override public byte[] photo(long id,Authentication auth) {
  Owned row=owned(id,auth,false);
  if(row.photoKey()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  try {return Files.readAllBytes(path(row.photoKey()));}
  catch(NoSuchFileException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
  catch(IOException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Private photo storage unavailable");}
 }
 @Override @Transactional public Map<String,Long> removePhoto(long id,long version,Authentication auth) {
  Owned row=owned(id,auth,true);version(row,version);
  db.update("UPDATE observations SET photo_key=NULL,photo_url=NULL,version=version+1 WHERE id=? AND member_id=?",id,member(auth));
  audit(id,member(auth),"PHOTO_DELETE",version+1);
  removeAfterCommit(row.photoKey());
  return Map.of("id",id,"version",version+1);
 }
 private Path path(String key) {
  if(!key.matches("[0-9a-f-]{36}\\.jpg"))throw new IllegalStateException("Invalid photo storage key");
  return directory.resolve(key);
 }
 private void removeAfterCommit(String key) {
  if(key!=null)TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCommit(){erase(key);}
  });
 }
 private void erase(String key) {
  try {Files.deleteIfExists(path(key));}
  catch(IOException e){
   // The URL is already inaccessible. Report storage cleanup failure without logging private paths.
   org.slf4j.LoggerFactory.getLogger(RecordOperationsImpl.class).error("Private photo cleanup failed; operator cleanup required");
  }
 }
}
