package com.voxlus.cloudflarecrusher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * @author Cameron Wolfe
 */
public class CloudflareCrusher {
  
  @Getter
  private static String userAgent = "";
  
  @Getter
  @Setter
  private static OkHttpClient httpClient;
  @Getter
  @Setter
  private static OkHttpClient nonRedirectableClient;
  @Getter
  private static CookieJar cookieJar = new CookieJar() {
    private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
    
    @Override
    public void saveFromResponse (HttpUrl url, List<Cookie> cookies) {
      cookieStore.put(url.host(), cookies);
    }
    
    @Override
    public List<Cookie> loadForRequest (HttpUrl url) {
      List<Cookie> cookies = cookieStore.get(url.host());
      return cookies != null ? cookies : new ArrayList<>();
    }
  };
  
  public CloudflareCrusher () {
    this("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36");
  }
  
  public CloudflareCrusher (String userAgent) {
    CloudflareCrusher.userAgent = userAgent;
    
    httpClient = new OkHttpClient().newBuilder().cookieJar(cookieJar).followRedirects(true).build();
    nonRedirectableClient = new OkHttpClient()
        .newBuilder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .build();
  }
  
}
