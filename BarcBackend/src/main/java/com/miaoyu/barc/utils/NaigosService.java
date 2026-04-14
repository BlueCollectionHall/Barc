package com.miaoyu.barc.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.pojo.NaigosApiPojo;
import com.miaoyu.barc.pojo.NaigosUserCurrentApiPojo;
import com.miaoyu.barc.user.mapper.BarcNaigosUuidMapper;
import com.miaoyu.barc.user.mapper.UserBasicMapper;
import com.miaoyu.barc.user.model.NaigosUserArchiveModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class NaigosService {

    public String getNaigosToken(String type, String account, String password) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("login_type", "normal");
        params.put("account", account);
        params.put("password", password);
        params.put("account_type", type);
        // 构建 Query String
        String queryString = params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) +
                        "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String url = "https://sapi.naigos.cn/users/sign/in?" + queryString;
        // 发送 POST 请求
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString("")) // 默认是 GET，可省略
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        NaigosApiPojo naigosResponse = mapper.readValue(response.body(), NaigosApiPojo.class);
        if (naigosResponse.getCode() == 0) {
            return naigosResponse.getData().toString();
        }
        return null;
    }

    public NaigosUserArchiveModel getNaigosArchive(String token) throws IOException, InterruptedException {
        String url = "https://sapi.naigos.cn/users/archive/current";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        NaigosUserCurrentApiPojo naigosResponse = mapper.readValue(response.body(), NaigosUserCurrentApiPojo.class);
        if (naigosResponse.getCode() == 0) {
            return naigosResponse.getData();
        }
        return null;
    }
}


