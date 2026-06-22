package io.livelattice.core.controller;

import io.livelattice.core.model.entity.User;
import io.livelattice.core.service.UserClaims;
import io.livelattice.core.service.UserService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAuthController {

    private final UserService userService;

    public InternalAuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/internal/auth/users/provision")
    public ResponseEntity<Map<String, String>> provision(
            @RequestHeader("x-auth-subject") String subject,
            @RequestHeader("x-auth-email") String email,
            @RequestHeader("x-auth-display-name") String displayName) {
        User user = userService.provision(new UserClaims(subject, email, displayName));
        return ResponseEntity.ok(Map.of("id", user.getId().toString(), "externalSubject", user.getExternalSubject()));
    }
}
