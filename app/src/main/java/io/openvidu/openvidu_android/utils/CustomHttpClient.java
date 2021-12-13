package io.openvidu.openvidu_android.utils;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class CustomHttpClient {

  private final OkHttpClient client;
  private final String baseUrl;
  private final String basicAuth;

  public CustomHttpClient(String baseUrl, String basicAuth) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.basicAuth = basicAuth;

    try {
      this.client = new OkHttpClient.Builder()
          .hostnameVerifier((hostname, session) -> true).build();

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void httpCall(String url, String method, String contentType, RequestBody body,
      Callback callback) throws IOException {
    url = url.startsWith("/") ? url.substring(1) : url;
    Request request = new Request.Builder()
        .url(this.baseUrl + url)
        .header("Authorization", this.basicAuth).header("Content-Type", contentType)
        .method(method, body)
        .build();
    Call call = client.newCall(request);
    call.enqueue(callback);
  }

  public void dispose() {
    this.client.dispatcher().executorService().shutdown();
  }

}
