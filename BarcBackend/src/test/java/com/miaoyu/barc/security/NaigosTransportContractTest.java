package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.utils.NaigosService;
import java.io.IOException;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 以离线 HTTP 替身验证既有外部协议、编码、解析与失败传播，不请求上游账号服务。 */
class NaigosTransportContractTest {
    @Test void loginEncodesReservedCharactersAndReturnsOnlySuccessfulToken() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = response("{\"code\":0,\"message\":\"成功\",\"data\":\"remote-token\"}");
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newHttpClient).thenReturn(client); when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
            assertEquals("remote-token", new NaigosService().getNaigosToken("email", "a+b@example.test", "test & pass"));
            var request = ArgumentCaptor.forClass(HttpRequest.class); verify(client).send(request.capture(), any());
            assertEquals("POST", request.getValue().method()); String query = request.getValue().uri().getRawQuery();
            assertTrue(query.contains("account=a%2Bb%40example.test")); assertTrue(query.contains("password=test+%26+pass"));
            when(response.body()).thenReturn("{\"code\":1,\"message\":\"失败\",\"data\":null}");
            assertNull(new NaigosService().getNaigosToken("email", "a", "b"));
            when(response.body()).thenReturn("not-json"); assertThrows(IOException.class, () -> new NaigosService().getNaigosToken("email", "a", "b"));
        }
    }

    @Test void archiveUsesAuthorizationHeaderAndPreservesExternalProfileContract() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = response("""
            {"code":0,"message":"成功","data":{"id":7,"qq_id":12345,"nickname":"名称","group_real_user_id":"u","register_real_group_id":"g","city":"上海","score":9,"avatar":"https://example.test/a.png","favorite":2,"email":"a@example.test","safe_level":1,"is_bot_memory":true}}
            """);
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newHttpClient).thenReturn(client); when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
            var archive = new NaigosService().getNaigosArchive("remote-token"); var serialized = new ObjectMapper().valueToTree(archive);
            assertEquals(7, serialized.path("id").asInt()); assertEquals(12345, serialized.path("qq_id").asLong());
            assertEquals("名称", serialized.path("nickname").asText()); assertEquals("上海", serialized.path("city").asText());
            assertEquals("u", serialized.path("group_real_user_id").asText()); assertEquals("g", serialized.path("register_real_group_id").asText());
            assertEquals(9, serialized.path("score").asInt()); assertEquals(2, serialized.path("favorite").asInt()); assertTrue(serialized.path("is_bot_memory").asBoolean());
            var request = ArgumentCaptor.forClass(HttpRequest.class); verify(client).send(request.capture(), any());
            assertEquals("GET", request.getValue().method()); assertEquals("remote-token", request.getValue().headers().firstValue("Authorization").orElseThrow()); assertNull(request.getValue().uri().getQuery());
            when(response.body()).thenReturn("{\"code\":1,\"message\":\"失败\",\"data\":null}"); assertNull(new NaigosService().getNaigosArchive("remote-token"));
            when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new IOException("offline"));
            assertThrows(IOException.class, () -> new NaigosService().getNaigosArchive("remote-token"));
        }
    }

    @SuppressWarnings("unchecked") private HttpResponse<String> response(String body) { var value = (HttpResponse<String>) mock(HttpResponse.class); when(value.body()).thenReturn(body); return value; }
}
