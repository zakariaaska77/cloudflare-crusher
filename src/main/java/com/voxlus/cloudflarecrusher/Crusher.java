package com.voxlus.cloudflarecrusher;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import okhttp3.Cookie;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Cameron Wolfe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crusher {
  
  private String url;
  private String referer = url;
  private ArrayList<Cookie> cookies;
  
  @SneakyThrows
  public static String body (@NonNull String url) {
    Crusher crusher = Crusher.builder().url(url).build();
    return crusher.execute(new Request.Builder().url(url)).body().string();
  }
  
  public List<Cookie> solve () {
    
    long start = System.currentTimeMillis();
    
    if (cookies == null) { cookies = Lists.newArrayList(); }
    
    try {
      
      OkHttpClient client = CloudflareCrusher.getHttpClient();
      OkHttpClient nonRedirectableClient = CloudflareCrusher.getNonRedirectableClient();
      
      Request request = new Request.Builder()
          .url(getUrl())
          .header("User-Agent", CloudflareCrusher.getUserAgent())
          .header("Referer", getUrl())
          .build();
      
      Response response = client.newCall(request).execute();
      cookies.addAll(client.cookieJar().loadForRequest(request.url()));
      
      if (isCloudflareChallenge(response)) {
        
        String body = response.body().string();
        
        String jschl_vc = regex(body, "name=\"jschl_vc\" value=\"(.+?)\"").get(0);
        String pass = regex(body, "name=\"pass\" value=\"(.+?)\"").get(0);
        double jschl_answer = getAnswer(body);
        
        //@formatter:off
        String url = "https://" + (request.url().host()) + "/cdn-cgi/l/chk_jschl?jschl_vc=" + jschl_vc + "&pass=" + pass + "&jschl_answer=" + jschl_answer;
        //@formatter:on
        
        TimeUnit.MILLISECONDS.sleep(5500 - (System.currentTimeMillis() - start));
        
        request = new Request.Builder()
            .url(url)
            .header("User-Agent", CloudflareCrusher.getUserAgent())
            .header("Referer", url)
            .build();
        
        response = nonRedirectableClient.newCall(request).execute();
        
        if (response.code() == 302) {
          cookies.addAll(nonRedirectableClient.cookieJar().loadForRequest(request.url()));
        } else {
          return solve();
        }
      }
      
      
    } catch (Exception ignored) { ignored.printStackTrace(); }
    
    return cookies;
  }
  
  public String getCookie () {
    StringBuilder builder = new StringBuilder();
    getCookies().forEach(cookie -> builder.append(cookie.toString()).append("; "));
    return builder.toString();
    
  }
  
  public Response execute (boolean solve, Request.Builder builder) throws IOException {
    
    Request request = builder
        .header("User-Agent", CloudflareCrusher.getUserAgent())
        .header("Referer", getUrl())
        .build();
    
    Response response = CloudflareCrusher.getHttpClient().newCall(request).execute();
    
    boolean challenge = isCloudflareChallenge(response);
    
    if (challenge && solve) {
      return execute(true, builder);
    }
    
    return response;
  }
  
  public Response execute (Request.Builder builder) throws IOException, InterruptedException {
    return execute(true, builder);
  }
  
  @SneakyThrows (value = MalformedURLException.class)
  public double getAnswer (String challenge) {
    
    double a;
    
    List<String> search = regex(challenge, "var s,t,o,p,b,r,e,a,k,i,n,g,f, (.+?)=\\{\"(.+?)\"");
    
    if (search.size() == 0) {
      return 0;
    }
    
    String variableA = search.get(0);
    String variableB = search.get(1);
    
    StringBuilder builder = new StringBuilder();
    builder
        .append("var a=")
        .append(regex(challenge, variableA + "=\\{\"" + variableB + "\":(.+?)\\}").get(0))
        .append(";");
    
    List<String> b = regex(challenge, variableA + "\\." + variableB + "(.+?)\\;");
    for (int i = 0 ; i < b.size() - 1 ; i++) {
      builder.append("a").append(b.get(i)).append(";");
    }
    
    a = (double) eval(builder.toString());
    
    List<String> fixedNumber = regex(challenge, "toFixed\\((.+?)\\)");
    
    if (fixedNumber == null) {
      return 0;
    }
    
    a = Double.valueOf(String.valueOf(eval(String.valueOf(a) + ".toFixed(" + fixedNumber.get(0) + ");")));
    a += new URL(getUrl()).getHost().length();
    return a;
  }
  
  private List<String> regex (String text, String textPattern) {
    Pattern pattern = Pattern.compile(textPattern);
    Matcher matcher = pattern.matcher(text);
    List<String> group = Lists.newArrayList();
    
    while (matcher.find()) {
      if (matcher.groupCount() >= 1) {
        if (matcher.groupCount() > 1) {
          group.add(matcher.group(1));
          group.add(matcher.group(2));
        }
        else {
          group.add(matcher.group(1));
        }
      }
    }
    return group;
  }
  
  @SneakyThrows
  public Object eval (String javascript) {
    ScriptEngineManager factory = new ScriptEngineManager();
    ScriptEngine engine = factory.getEngineByName("ECMAScript");
    return engine.eval(javascript);
  }
  
  private boolean isCloudflareChallenge (Response response) {
    return response.code() == 503 && ("cloudflare".equals(response.header("Server")));
  }
  
}
