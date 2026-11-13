package tw.ipola;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}","ipola.registration.open=true"})
class ApiIntegrationTest {
 static final Path photos=temporaryPhotos();
 static Path temporaryPhotos(){try{return Files.createTempDirectory("ipola-test-photos-");}catch(Exception ex){throw new IllegalStateException(ex);}}
 @DynamicPropertySource static void photoDirectory(DynamicPropertyRegistry properties){properties.add("ipola.photos.directory",()->photos.toString());}
 @LocalServerPort int port;
 @Autowired DataSource source;
 @Autowired JdbcTemplate db;
 final HttpClient client=HttpClient.newHttpClient();
 final ObjectMapper json=new ObjectMapper();
 HttpResponse<String> call(String method,String path,String body,String token)throws Exception {
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
  if(token!=null)b.header("Authorization","Bearer "+token);
  if(body!=null)b.header("Content-Type","application/json");
  return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 JsonNode data(HttpResponse<String> response){return json.readTree(response.body());}
 @Test void identityPrivacyAndCareHistory()throws Exception {
  String email="cat-"+UUID.randomUUID()+"@example.com";
  String user="{\"email\":\""+email+"\",\"nickname\":\"照顧者\",\"password\":\"test-password-123\"}";
  assertEquals(201,call("POST","/auth/register",user,null).statusCode());
  assertEquals(409,call("POST","/auth/register",user,null).statusCode());
  assertEquals(401,call("POST","/auth/login","{\"email\":\""+email+"\",\"password\":\"incorrect-password\"}",null).statusCode());
  var login=call("POST","/auth/login",user,null);assertEquals(200,login.statusCode());
  String token=data(login).get("accessToken").asText();
  long member=data(login).get("member").get("id").asLong();
  assertEquals(200,call("GET","/auth/me",null,token).statusCode());
  assertEquals(401,call("POST","/animals","{}",null).statusCode());
  var animal=call("POST","/animals","{\"species\":\"CAT\",\"name\":\"橘貓\",\"description\":\"尾巴有白色斑紋\"}",token);
  assertEquals(201,animal.statusCode());long id=data(animal).get("id").asLong();
  for(String kind:new String[]{"SIGHTING","FEEDING"}) {
   String observation="{\"animalId\":"+id+",\"kind\":\""+kind+"\",\"observedAt\":\""+Instant.now().minusSeconds(10)+"\",\"latitude\":25.033123,\"longitude\":121.565432,\"notes\":\"詳細地址\",\"photoUrl\":\"https://example.com/photo.jpg\"}";
   assertEquals(201,call("POST","/observations",observation,token).statusCode());
  }
  var publicResult=call("GET","/observations?animalId="+id,null,null);
  assertEquals(200,publicResult.statusCode());assertFalse(publicResult.body().contains("25.033123"));assertFalse(publicResult.body().contains("121.565432"));assertFalse(publicResult.body().contains("詳細地址"));assertFalse(publicResult.body().contains("photo.jpg"));assertFalse(publicResult.body().contains(email));
  var publicItem=data(publicResult).get("items").get(0);
  assertEquals("APPROXIMATE",publicItem.get("locationPrecision").asText());
  assertEquals(25.035,publicItem.get("latitude").asDouble(),0.0000001);
  assertTrue(publicItem.get("notes").isNull());
  assertTrue(publicItem.get("photoUrl").isNull());
  var privateItem=data(call("GET","/observations?animalId="+id,null,token)).get("items").get(0);
  assertEquals(25.033123,privateItem.get("latitude").asDouble(),0.0000001);
  assertEquals("EXACT",privateItem.get("locationPrecision").asText());
  assertEquals("詳細地址",privateItem.get("notes").asText());
  assertTrue(privateItem.get("photoUrl").isNull());
  assertEquals(2,data(call("GET","/members/"+member+"/observations",null,null)).get("items").size());
  assertEquals(400,call("GET","/observations?limit=101",null,null).statusCode());
  assertEquals(401,call("GET","/observations",null,"invalid-token").statusCode());
  assertEquals(204,call("POST","/auth/logout",null,token).statusCode());
  assertEquals(401,call("GET","/observations",null,token).statusCode());
 }
 @Test void unrecognizedAnimalAndInputValidation()throws Exception {
  String user="{\"email\":\"dog-"+UUID.randomUUID()+"@example.com\",\"nickname\":\"路人\",\"password\":\"test-password-123\"}";
  assertEquals(201,call("POST","/auth/register",user,null).statusCode());
  String token=data(call("POST","/auth/login",user,null)).get("accessToken").asText();
  String base="{\"kind\":\"SIGHTING\",\"observedAt\":\""+Instant.now().minusSeconds(10)+"\",\"latitude\":24.1,\"longitude\":120.2,\"notes\":\"尚未辨識\"}";
  assertEquals(201,call("POST","/observations",base,token).statusCode());
  assertEquals(400,call("POST","/observations",base.replace("24.1","91.0"),token).statusCode());
  assertEquals(400,call("POST","/observations",base.replace("SIGHTING","OTHER"),token).statusCode());
  assertEquals(404,call("POST","/observations",base.replace("{","{\"animalId\":999999,"),token).statusCode());
 }
 @Test void databaseAndMigrationAreRealAndValidated()throws Exception {
  try(var connection=source.getConnection()) {
   assertEquals(System.getenv().getOrDefault("TEST_DB_EXPECTED_PRODUCT","H2"),connection.getMetaData().getDatabaseProductName());
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=TRUE",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=TRUE",Integer.class));
 }
 @Test void paginationUsesStableOrderAndAppliesToMemberHistory()throws Exception {
  String user="{\"email\":\"page-"+UUID.randomUUID()+"@example.com\",\"nickname\":\"分頁驗證\",\"password\":\"test-password-123\"}";
  assertEquals(201,call("POST","/auth/register",user,null).statusCode());
  var login=data(call("POST","/auth/login",user,null));
  String token=login.get("accessToken").asText();
  long member=login.get("member").get("id").asLong();
  long animal=data(call("POST","/animals","{\"species\":\"DOG\",\"name\":\"分頁犬\",\"description\":\"測試\"}",token)).get("id").asLong();
  long[] ids=new long[3];
  String when=Instant.now().minusSeconds(30).toString();
  for(int i=0;i<3;i++) {
   var response=call("POST","/observations","{\"animalId\":"+animal+",\"kind\":\"FEEDING\",\"observedAt\":\""+when+"\",\"latitude\":25.033123,\"longitude\":121.565432,\"notes\":\"分頁\"}",token);
   assertEquals(201,response.statusCode());ids[i]=data(response).get("id").asLong();
  }
  for(String path:new String[]{"/observations?animalId="+animal+"&","/members/"+member+"/observations?"}) {
   var first=data(call("GET",path+"limit=1&offset=0",null,null));
   var second=data(call("GET",path+"limit=1&offset=1",null,token));
   assertEquals(1,first.get("limit").asInt());assertEquals(0,first.get("offset").asInt());
   assertEquals(1,first.get("items").size());assertEquals(ids[2],first.get("items").get(0).get("id").asLong());
   assertEquals(1,second.get("offset").asInt());assertEquals(ids[1],second.get("items").get(0).get("id").asLong());
   assertTrue(data(call("GET",path+"limit=1&offset=3",null,null)).get("items").isEmpty());
   assertEquals("APPROXIMATE",first.get("items").get(0).get("locationPrecision").asText());
   assertEquals("EXACT",second.get("items").get(0).get("locationPrecision").asText());
   assertEquals(400,call("GET",path+"limit=0",null,null).statusCode());
   assertEquals(400,call("GET",path+"offset=-1",null,null).statusCode());
  }
 }
 record Session(String token,long member) {}
 Session session(String prefix)throws Exception {
  String user="{\"email\":\""+prefix+"-"+UUID.randomUUID()+"@example.com\",\"nickname\":\"照顧者\",\"password\":\"test-password-123\"}";
  assertEquals(201,call("POST","/auth/register",user,null).statusCode());
  var result=data(call("POST","/auth/login",user,null));
  return new Session(result.get("accessToken").asText(),result.get("member").get("id").asLong());
 }
 String observationBody(long version,String notes) {
  return "{\"version\":"+version+",\"animalId\":null,\"kind\":\"SIGHTING\",\"observedAt\":\""+Instant.now().minusSeconds(20)+"\",\"latitude\":25.033123,\"longitude\":121.565432,\"notes\":\""+notes+"\"}";
 }
 HttpResponse<byte[]> photo(String method,long id,long version,byte[] content,String token)throws Exception {
  String boundary="IpolaTestBoundary";
  var body=new ByteArrayOutputStream();
  body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  body.write(content);body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/observations/"+id+"/photo?version="+version));
  if(token!=null)request.header("Authorization","Bearer "+token);
  if(method.equals("POST"))request.header("Content-Type","multipart/form-data; boundary="+boundary);
  return client.send(request.method(method,method.equals("POST")?HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()):HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
 }
 byte[] image(String format,int width,int height)throws Exception {
  var stream=new ByteArrayOutputStream();
  assertTrue(ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),format,stream));
  return stream.toByteArray();
 }
 byte[] pngWithMetadata()throws Exception {
  byte[] original=image("png",1800,1000);
  byte[] text="Comment\u0000PrivateLocationMetadata".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
  byte[] type="tEXt".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  var chunk=new ByteArrayOutputStream();var out=new java.io.DataOutputStream(chunk);
  out.writeInt(text.length);out.write(type);out.write(text);
  var crc=new java.util.zip.CRC32();crc.update(type);crc.update(text);out.writeInt((int)crc.getValue());
  var result=new ByteArrayOutputStream();result.write(original,0,33);result.write(chunk.toByteArray());result.write(original,33,original.length-33);
  return result.toByteArray();
 }
 byte[] orientedJpeg()throws Exception {
  var payload=new ByteArrayOutputStream();var exif=new java.io.DataOutputStream(payload);
  exif.write(new byte[]{'E','x','i','f',0,0,'M','M'});exif.writeShort(42);exif.writeInt(8);
  exif.writeShort(1);exif.writeShort(274);exif.writeShort(3);exif.writeInt(1);exif.writeShort(6);exif.writeShort(0);exif.writeInt(0);
  byte[] original=image("jpg",40,20);
  var result=new ByteArrayOutputStream();var out=new java.io.DataOutputStream(result);
  out.write(original,0,2);out.writeShort(0xffe1);out.writeShort(payload.size()+2);out.write(payload.toByteArray());out.write(original,2,original.length-2);
  return result.toByteArray();
 }
 @Test void onlyOwnerCanReadContentAndEditOrDeleteWithCurrentVersion()throws Exception {
  var owner=session("owner");var other=session("other");
  long id=data(call("POST","/observations",observationBody(0,"private-note"),owner.token())).get("id").asLong();
  String history="/members/"+owner.member()+"/observations";
  for(String route:new String[]{"/observations",history}) {
   var items=data(call("GET",route,null,other.token())).get("items");
   JsonNode item=null;for(var candidate:items)if(candidate.get("id").asLong()==id)item=candidate;
   assertNotNull(item);assertEquals("EXACT",item.get("locationPrecision").asText());
   assertTrue(item.get("notes").isNull());assertTrue(item.get("photoUrl").isNull());assertEquals(0,item.get("version").asLong());
  }
  assertEquals(401,call("PUT","/observations/"+id,observationBody(0,"x"),null).statusCode());
  assertEquals(404,call("PUT","/observations/"+id,observationBody(0,"x"),other.token()).statusCode());
  var updated=call("PUT","/observations/"+id,observationBody(0,"edited"),owner.token());
  assertEquals(200,updated.statusCode());assertEquals(1,data(updated).get("version").asLong());
  assertEquals(409,call("PUT","/observations/"+id,observationBody(0,"stale"),owner.token()).statusCode());
  assertEquals(404,call("DELETE","/observations/"+id+"?version=1",null,other.token()).statusCode());
  assertEquals(409,call("DELETE","/observations/"+id+"?version=0",null,owner.token()).statusCode());
  assertEquals(204,call("DELETE","/observations/"+id+"?version=1",null,owner.token()).statusCode());
  for(var item:data(call("GET",history,null,owner.token())).get("items"))assertNotEquals(id,item.get("id").asLong());
  assertEquals(404,call("PUT","/observations/"+id,observationBody(1,"gone"),owner.token()).statusCode());
 }
 @Test void photosAreReencodedPrivateVersionedAndRemoved()throws Exception {
  var owner=session("photo-owner");var other=session("photo-other");
  long id=data(call("POST","/observations",observationBody(0,"photo-note"),owner.token())).get("id").asLong();
  var png=pngWithMetadata();
  assertEquals(401,photo("POST",id,0,png,null).statusCode());
  assertEquals(404,photo("POST",id,0,png,other.token()).statusCode());
  var uploaded=photo("POST",id,0,png,owner.token());
  assertEquals(200,uploaded.statusCode());assertEquals(1,json.readTree(uploaded.body()).get("version").asLong());
  var downloaded=photo("GET",id,1,new byte[0],owner.token());
  assertEquals(200,downloaded.statusCode());assertEquals("image/jpeg",downloaded.headers().firstValue("content-type").orElse(""));
  assertTrue(downloaded.headers().firstValue("cache-control").orElse("").contains("no-store"));
  assertEquals("nosniff",downloaded.headers().firstValue("x-content-type-options").orElse(""));
  assertEquals(0xff,downloaded.body()[0]&255);assertEquals(0xd8,downloaded.body()[1]&255);
  assertFalse(new String(downloaded.body(),java.nio.charset.StandardCharsets.ISO_8859_1).contains("PrivateLocationMetadata"));
  var decoded=ImageIO.read(new java.io.ByteArrayInputStream(downloaded.body()));assertEquals(1600,decoded.getWidth());
  assertEquals(401,photo("GET",id,1,new byte[0],null).statusCode());
  assertEquals(404,photo("GET",id,1,new byte[0],other.token()).statusCode());
  assertEquals(409,photo("POST",id,0,png,owner.token()).statusCode());
  var privateItem=data(call("GET","/members/"+owner.member()+"/observations",null,owner.token())).get("items").get(0);
  assertEquals("/api/observations/"+id+"/photo",privateItem.get("photoUrl").asText());
  var otherItem=data(call("GET","/members/"+owner.member()+"/observations",null,other.token())).get("items").get(0);
  assertTrue(otherItem.get("notes").isNull());assertTrue(otherItem.get("photoUrl").isNull());
  var removed=photo("DELETE",id,1,new byte[0],owner.token());assertEquals(200,removed.statusCode());
  assertEquals(2,json.readTree(removed.body()).get("version").asLong());
  assertEquals(404,photo("GET",id,2,new byte[0],owner.token()).statusCode());
  assertEquals(200,photo("POST",id,2,orientedJpeg(),owner.token()).statusCode());
  var rotated=ImageIO.read(new java.io.ByteArrayInputStream(photo("GET",id,3,new byte[0],owner.token()).body()));
  assertEquals(20,rotated.getWidth());assertEquals(40,rotated.getHeight());
  assertEquals(204,call("DELETE","/observations/"+id+"?version=3",null,owner.token()).statusCode());
  assertEquals(404,photo("GET",id,3,new byte[0],owner.token()).statusCode());
 }
 @Test void invalidOrOversizePhotosDoNotReplaceExistingPhoto()throws Exception {
  var owner=session("invalid-photo");
  long id=data(call("POST","/observations",observationBody(0,"safe"),owner.token())).get("id").asLong();
  assertEquals(200,photo("POST",id,0,image("png",10,10),owner.token()).statusCode());
  for(byte[] invalid:new byte[][]{"<svg></svg>".getBytes(),new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff},image("png",4000,3001)}) {
   var rejected=photo("POST",id,1,invalid,owner.token());
   assertTrue(rejected.statusCode()==400||rejected.statusCode()==413||rejected.statusCode()==415,"unexpected invalid image status "+rejected.statusCode());
   assertEquals(200,photo("GET",id,1,new byte[0],owner.token()).statusCode());
  }
  assertEquals(413,photo("POST",id,1,new byte[5*1024*1024+1],owner.token()).statusCode());
  assertEquals(200,photo("GET",id,1,new byte[0],owner.token()).statusCode());
  assertEquals(204,call("DELETE","/observations/"+id+"?version=1",null,owner.token()).statusCode());
 }
 @Test void publicGridCentersStayWithinCoordinateBounds()throws Exception {
  var owner=session("grid");
  for(double latitude:new double[]{-90,90}) {
   var created=call("POST","/observations",observationBody(0,"grid").replace("25.033123",Double.toString(latitude)).replace("121.565432",latitude<0?"-180":"180"),owner.token());
   assertEquals(201,created.statusCode());
  }
  for(var item:data(call("GET","/members/"+owner.member()+"/observations",null,null)).get("items")) {
   double latitude=item.get("latitude").asDouble(),longitude=item.get("longitude").asDouble();
   assertTrue(latitude>=-90&&latitude<=90);assertTrue(longitude>=-180&&longitude<=180);
   assertEquals(89.995,Math.abs(latitude),0.0000001);assertEquals(179.995,Math.abs(longitude),0.0000001);
  }
 }
}
