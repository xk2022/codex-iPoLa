package tw.ipola;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
class ApiIntegrationTest {
 @LocalServerPort int port;
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
  String user="{\"email\":\"cat@example.com\",\"nickname\":\"照顧者\",\"password\":\"test-password-123\"}";
  assertEquals(201,call("POST","/auth/register",user,null).statusCode());
  assertEquals(409,call("POST","/auth/register",user,null).statusCode());
  assertEquals(401,call("POST","/auth/login","{\"email\":\"cat@example.com\",\"password\":\"incorrect-password\"}",null).statusCode());
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
  assertEquals(200,publicResult.statusCode());assertFalse(publicResult.body().contains("25.033123"));assertFalse(publicResult.body().contains("121.565432"));assertFalse(publicResult.body().contains("詳細地址"));assertFalse(publicResult.body().contains("photo.jpg"));assertFalse(publicResult.body().contains("cat@example.com"));
  var publicItem=data(publicResult).get("items").get(0);
  assertEquals("APPROXIMATE",publicItem.get("locationPrecision").asText());
  var privateItem=data(call("GET","/observations?animalId="+id,null,token)).get("items").get(0);
  assertEquals(25.033123,privateItem.get("latitude").asDouble(),0.0000001);
  assertEquals("EXACT",privateItem.get("locationPrecision").asText());
  assertEquals(2,data(call("GET","/members/"+member+"/observations",null,null)).get("items").size());
  assertEquals(400,call("GET","/observations?limit=101",null,null).statusCode());
  assertEquals(401,call("GET","/observations",null,"invalid-token").statusCode());
  assertEquals(204,call("POST","/auth/logout",null,token).statusCode());
  assertEquals(401,call("GET","/observations",null,token).statusCode());
 }
 @Test void unrecognizedAnimalAndInputValidation()throws Exception {
  String user="{\"email\":\"dog@example.com\",\"nickname\":\"路人\",\"password\":\"test-password-123\"}";
  assertEquals(201,call("POST","/auth/register",user,null).statusCode());
  String token=data(call("POST","/auth/login",user,null)).get("accessToken").asText();
  String base="{\"kind\":\"SIGHTING\",\"observedAt\":\""+Instant.now().minusSeconds(10)+"\",\"latitude\":24.1,\"longitude\":120.2,\"notes\":\"尚未辨識\"}";
  assertEquals(201,call("POST","/observations",base,token).statusCode());
  assertEquals(400,call("POST","/observations",base.replace("24.1","91.0"),token).statusCode());
  assertEquals(400,call("POST","/observations",base.replace("SIGHTING","OTHER"),token).statusCode());
  assertEquals(404,call("POST","/observations",base.replace("{","{\"animalId\":999999,"),token).statusCode());
 }
}
