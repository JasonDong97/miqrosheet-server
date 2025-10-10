package com.era.miqrosheet.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oauth2")
public class OAuthConfig {

    private String oauthUrl;
    private String clientId;
    private String clientSecret;

    /**
     * 获取授权地址
     *
     * @param redirectUri 重定向地址
     * @return 授权地址
     */
    public String getAuthorizeURL(String redirectUri) {
        return oauthUrl + "/authorize?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + redirectUri
                + "&scope=userinfo";
    }

    /**
     * 获取令牌地址
     *
     * @return 令牌地址
     */
    public String getTokenURL(String code, String redirectUri) {
        return oauthUrl + "/token?grant_type=authorization_code&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&code=" + code
                + "&redirect_uri=" + redirectUri;
    }

}
