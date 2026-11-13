package tw.ipola;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:invitations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
 "spring.datasource.username=${TEST_DB_USERNAME:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
 "ipola.registration.open=false"})
class InvitationIntegrationTest {
 static final String code=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
 static final String invited="allowed-"+UUID.randomUUID()+"@example.com";
 @DynamicPropertySource static void invitation(DynamicPropertyRegistry properties) {
  properties.add("ipola.registration.invitations",()->invited+":"+code);
 }
 @LocalServerPort int port;
 final HttpClient client=HttpClient.newHttpClient();
 HttpResponse<String> register(String email,String invitationCode)throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/register"))
   .header("Content-Type","application/json")
   .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\""+email+"\",\"nickname\":\"邀請照顧者\",\"password\":\"test-password-123\",\"invitationCode\":\""+invitationCode+"\"}"))
   .build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void onlyConfiguredInvitationsCanRegister()throws Exception {
  assertEquals(403,register("uninvited-"+UUID.randomUUID()+"@example.com",code).statusCode());
  assertEquals(403,register(invited,"").statusCode());
  assertEquals(403,register(invited,"wrong-invitation-code").statusCode());
  assertEquals(201,register(invited,code).statusCode());
  assertEquals(409,register(invited,code).statusCode());
 }
}
