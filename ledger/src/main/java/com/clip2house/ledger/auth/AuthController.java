package com.clip2house.ledger.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record TokenResponse(String token, String nickname, Instant expiresAt) {}

    @PostMapping("/guests")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse createGuest() {
        var created = authService.createGuest();
        return new TokenResponse(created.token().token(), created.player().getNickname(), created.token().expiresAt());
    }

    public record PromoteRequest(@NotBlank @Email String email,
                                 @NotBlank @Size(min = 8, max = 72) String password) {}

    @PostMapping("/account/promote")
    public Map<String, Object> promote(@RequestAttribute("playerId") Long playerId,
                                       @Valid @RequestBody PromoteRequest request) {
        var player = authService.promote(playerId, request.email(), request.password());
        return Map.of("nickname", player.getNickname(), "email", player.getEmail());
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        var token = authService.login(request.email(), request.password());
        return Map.of("token", token.token(), "expiresAt", token.expiresAt());
    }
}
