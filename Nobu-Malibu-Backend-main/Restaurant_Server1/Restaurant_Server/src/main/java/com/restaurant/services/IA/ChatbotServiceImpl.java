package com.restaurant.services.IA;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final WebClient.Builder webClientBuilder;

    @Override
    public String chat(String message) {

        try {

            WebClient webClient = webClientBuilder
                    .baseUrl("https://api.groq.com/openai")
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "temperature", 0.3,
                    "messages", List.of(

                            // 🧠 SYSTEM PROMPT RESTRICTIVO (CLAVE)
                            Map.of(
        "role", "system",
        "content",
                "You are the virtual assistant of Nobu Malibu restaurant located in Cartagena, Colombia. " +
                "You only answer questions related to the restaurant menu, food categories, prices, and table reservations. " +

                "IMPORTANT LANGUAGE RULE: " +
                "If the user writes in Spanish, respond in Spanish. " +
                "If the user writes in English, respond in English. " +

                "If the user asks about the restaurant location, say it is located in Cartagena, Colombia. " +

                "If the user asks something outside the restaurant topics, respond in the same language used by the user. " +

                "Spanish response: 'Lo siento, solo puedo ayudarte con el menú, precios y reservas del restaurante.' " +

                "English response: 'Sorry, I can only help with the restaurant menu, prices, and table reservations.' " +

                "Keep responses short, professional, and friendly. " +
                "Do not invent information. " +
                "Do not talk about games, coding, stories, politics, or unrelated topics."
),

                            // 👤 USER MESSAGE
                            Map.of(
                                    "role", "user",
                                    "content", message
                            )
                    )
            );

            Map response = webClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List choices = (List) response.get("choices");

            if (choices != null && !choices.isEmpty()) {

                Map choice = (Map) choices.get(0);
                Map messageMap = (Map) choice.get("message");

                return messageMap.get("content").toString();
            }

            return "No hubo respuesta del asistente.";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }
}
