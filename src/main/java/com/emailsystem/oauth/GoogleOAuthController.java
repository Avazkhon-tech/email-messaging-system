package com.emailsystem.oauth;

import com.emailsystem.oauth.dto.AuthorizeResponse;
import com.emailsystem.security.AuthUser;
import com.emailsystem.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/oauth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthService oauthService;

    @GetMapping("/authorize")
    public AuthorizeResponse authorize(@CurrentUser AuthUser user) {
        return new AuthorizeResponse(oauthService.buildAuthorizationUrl(user));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String target = oauthService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
