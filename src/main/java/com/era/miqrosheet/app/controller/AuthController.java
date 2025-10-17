package com.era.miqrosheet.app.controller;

import cn.hutool.core.util.StrUtil;
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
import java.net.URL;

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
     * test:
     * - http://localhost:8081/miqrosheet/oauth2/authorize
     * - http://192.168.5.10:20080?gridKey=test
     */
    @GetMapping("/authorize")
    public void redirect(HttpServletRequest request,
                         HttpServletResponse response,
                         String code) throws IOException {
        log.info(StrUtil.repeat("-", 50));
        String serverRedirectUrl = request.getRequestURL().toString();
        String clientRedirectUrl = ServletUtil.getHeader(request, "referer", "UTF-8");
        if (clientRedirectUrl != null) {
            clientRedirectUrl = StrUtil.replaceLast(clientRedirectUrl, "#/", "");
            URL url = new URL(clientRedirectUrl);
            serverRedirectUrl = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + request.getRequestURI();
        }

        log.info("clientRedirectUrl: {}", clientRedirectUrl);
        log.info("serverRedirectUrl: {}", serverRedirectUrl);

        Cookie cookie = ServletUtil.getCookie(request, "access_token");
        if (cookie != null) {
            String accessToken = cookie.getValue();
            if (accessToken != null) {
                response.sendRedirect(clientRedirectUrl);
                return;
            }
        }

        if (code == null) {
            String authorizeURL = oAuthConfig.getAuthorizeURL(serverRedirectUrl);
            log.info("重定向: {}", authorizeURL);
//            response.setContentType("text/html;charset=UTF-8");
//            response.getWriter().write("<script>alert('请先登录授权');</script>");
//            response.getWriter().write("<script>window.location.href='"+authorizeURL+"'</script>");
            response.sendRedirect(authorizeURL);
            return;
        }

        log.info("已获取到 code: {}", code);
        String tokenURL = oAuthConfig.getTokenURL(code, serverRedirectUrl);
        log.info("获取 access_token 请求: {}", tokenURL);
        Request httpReq = new Request.Builder().url(tokenURL).get().build();
        try (Response resp = client.newCall(httpReq).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                String text = resp.body().string();
                log.info("获取 access_token 响应: {}", text);
                JSONObject json = JSON.parseObject(text);
                if (200 == json.getIntValue("code")) {
                    String accessToken = json.getString("access_token");
                    Long expiresIn = json.getLong("expires_in");
                    cookie = new Cookie("access_token", accessToken);
                    cookie.setPath("/");
                    cookie.setMaxAge(expiresIn.intValue());
                    response.addCookie(cookie);
                    log.info("重定向：{}", clientRedirectUrl);
                    response.sendRedirect(clientRedirectUrl);
                } else {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(text);
                }
            }
        }
    }
}
