package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.AuthType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "request_auths")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestAuth extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    private AuthType authType;

    @Column(name = "api_key_header", length = 200)
    private String apiKeyHeader;

    @Column(name = "api_key_value", length = 2000)
    private String apiKeyValue;

    @Column(name = "api_key_add_to", length = 20)
    private String apiKeyAddTo;

    @Column(name = "bearer_token", length = 5000)
    private String bearerToken;

    @Column(name = "basic_username", length = 500)
    private String basicUsername;

    @Column(name = "basic_password", length = 500)
    private String basicPassword;

    @Column(name = "oauth2_grant_type", length = 50)
    private String oauth2GrantType;

    @Column(name = "oauth2_auth_url", length = 2000)
    private String oauth2AuthUrl;

    @Column(name = "oauth2_token_url", length = 2000)
    private String oauth2TokenUrl;

    @Column(name = "oauth2_client_id", length = 500)
    private String oauth2ClientId;

    @Column(name = "oauth2_client_secret", length = 500)
    private String oauth2ClientSecret;

    @Column(name = "oauth2_scope", length = 1000)
    private String oauth2Scope;

    @Column(name = "oauth2_callback_url", length = 2000)
    private String oauth2CallbackUrl;

    @Column(name = "oauth2_access_token", length = 5000)
    private String oauth2AccessToken;

    @Column(name = "jwt_secret", length = 2000)
    private String jwtSecret;

    @Column(name = "jwt_payload", columnDefinition = "TEXT")
    private String jwtPayload;

    @Column(name = "jwt_algorithm", length = 20)
    private String jwtAlgorithm;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private Request request;
}
