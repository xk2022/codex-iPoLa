package tw.ipola;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

/** Owner-authorized record and private-photo operations. */
interface RecordOperations {
 Map<String,Long> update(long id, ApiService.ObservationUpdate input, Authentication auth);
 void delete(long id, long version, Authentication auth);
 Map<String,Long> upload(long id, long version, MultipartFile file, Authentication auth);
 byte[] photo(long id, Authentication auth);
 Map<String,Long> removePhoto(long id, long version, Authentication auth);
}
