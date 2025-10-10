package com.era.miqrosheet.app.controller;

import cn.hutool.extra.servlet.ServletUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.era.miqrosheet.infra.config.OAuthConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/oauth2")
public class AuthController {

    // 创建一个全局的 OkHttpClient 实例（推荐做法）
    private static final OkHttpClient client = new OkHttpClient();
    private final OAuthConfig oAuthConfig;

    /**
     * OAuth2 重定向处理
     * http://localhost:8081/miqrosheet/oauth2/redirect?redirectUri=http://localhost:8081/miqrosheet
     */
    @GetMapping("/redirect")
    public void redirect(HttpServletRequest request,
                         HttpServletResponse response,
                         String code, String redirectUri) throws IOException {
        String requestUrl = request.getRequestURL().toString();
        log.info("requestUrl: {}", requestUrl);

        Cookie cookie = ServletUtil.getCookie(request, "access_token");
        if (cookie != null) {
            String accessToken = cookie.getValue();
            if (accessToken != null) {
                response.sendRedirect(redirectUri);
                return;
            }
        }

        if (code == null) {
            log.info("redirect to authorize");
            response.sendRedirect(oAuthConfig.getAuthorizeURL(requestUrl + "?redirectUri=" + redirectUri));
            return;
        }

        log.info("code: {}", code);
        String tokenURL = oAuthConfig.getTokenURL(code, requestUrl + "?redirectUri=" + redirectUri);
        log.info("tokenURL: {}", tokenURL);
        Request httpReq = new Request.Builder().url(tokenURL).get().build();
        try (Response resp = client.newCall(httpReq).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                JSONObject json = JSON.parseObject(resp.body().string());
                log.info("获取access_token : {}", json.getString("msg"));
                if (200 == json.getIntValue("code")) {
                    String accessToken = json.getString("access_token");
                    Long expiresIn = json.getLong("expires_in");
                    cookie = new Cookie("access_token", accessToken);
                    cookie.setPath("/");
                    cookie.setMaxAge(expiresIn.intValue());
                    response.addCookie(cookie);
                    response.sendRedirect(redirectUri);
                } else {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(json.toJSONString());
                }
            }
        }
    }
}
