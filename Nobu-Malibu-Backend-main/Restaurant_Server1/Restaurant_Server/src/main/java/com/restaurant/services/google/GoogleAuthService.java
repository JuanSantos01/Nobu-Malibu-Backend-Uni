package com.restaurant.services.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.entity.User;
import com.restaurant.enums.UserRole;
import com.restaurant.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inicio de sesión con Google: el móvil obtiene un ID token de Google y el servidor lo valida
 * con el endpoint oficial tokeninfo (firma, vigencia y audiencia) antes de emitir SU propio JWT.
 * Variable de entorno requerida en Render: GOOGLE_CLIENT_IDS (ID de cliente "Web", separados por coma).
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    public record GoogleProfile(String email, String name) {
    }

    public static class GoogleAuthException extends RuntimeException {
        private final int status;

        public GoogleAuthException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    @Value("${google.client-ids:}")
    private String clientIdsRaw;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public GoogleProfile verify(String idToken) {
        Set<String> allowed = Arrays.stream(clientIdsRaw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (allowed.isEmpty())
            throw new GoogleAuthException(503, "El inicio con Google no está configurado en el servidor (falta GOOGLE_CLIENT_IDS).");
        if (idToken == null || idToken.isBlank()) throw new GoogleAuthException(400, "Falta el token de Google");

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new GoogleAuthException(401, "Google rechazó el token. Inténtalo de nuevo.");

            JsonNode n = mapper.readTree(res.body());
            String aud = n.path("aud").asText("");
            String iss = n.path("iss").asText("");
            String email = n.path("email").asText("");
            boolean verified = "true".equalsIgnoreCase(n.path("email_verified").asText("false"));
            long exp = n.path("exp").asLong(0);

            if (!allowed.contains(aud)) throw new GoogleAuthException(401, "El token no pertenece a esta aplicación.");
            if (!iss.equals("accounts.google.com") && !iss.equals("https://accounts.google.com"))
                throw new GoogleAuthException(401, "Emisor del token no válido.");
            if (exp * 1000 < System.currentTimeMillis()) throw new GoogleAuthException(401, "El token de Google expiró.");
            if (email.isEmpty() || !verified) throw new GoogleAuthException(401, "Tu correo de Google no está verificado.");

            return new GoogleProfile(email.toLowerCase(), n.path("name").asText(""));
        } catch (GoogleAuthException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleAuthException(503, "No se pudo contactar con Google.");
        } catch (Exception e) {
            throw new GoogleAuthException(503, "No se pudo contactar con Google.");
        }
    }

    /** Busca al usuario por correo; si no existe lo crea como CUSTOMER con una contraseña aleatoria. */
    public User findOrCreate(GoogleProfile p) {
        return userRepo.findFirstByEmail(p.email()).orElseGet(() -> {
            User u = new User();
            u.setEmail(p.email());
            u.setName(p.name().isBlank() ? p.email().substring(0, p.email().indexOf('@')) : p.name());
            u.setUserRole(UserRole.CUSTOMER);
            u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userRepo.save(u);
        });
    }
}
