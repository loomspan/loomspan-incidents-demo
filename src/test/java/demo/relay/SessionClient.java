package demo.relay;

import java.net.*;
import java.net.http.*;
import tools.jackson.databind.ObjectMapper;

/** Real cookie session and CSRF flow, shared by HTTP integration tests. */
class SessionClient {
    final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    final String base;
    final ObjectMapper json=new ObjectMapper();
    SessionClient(int port) { base="http://127.0.0.1:"+port+"/api"; }
    HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    HttpResponse<String> post(String path,String body) throws Exception { return send(path,body,"application/json",true); }
    HttpResponse<String> send(String path,String body,String type,boolean csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type",type);
        if(csrf) { var token=json.readTree(get("/csrf").body());request.header(token.get("headerName").asText(),token.get("token").asText()); }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void login(String name) throws Exception {
        var response=send("/login","username="+name+"&password=relay-demo","application/x-www-form-urlencoded",true);
        org.assertj.core.api.Assertions.assertThat(response.statusCode()).isEqualTo(204);
    }
}
