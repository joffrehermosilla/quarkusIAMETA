package com.adobe.ajo.webhook.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class AiFallbackService {

    @ConfigProperty(name = "ai.openai.key", defaultValue = "dummy") String openAiKey;
    @ConfigProperty(name = "ai.deepseek.key") String deepSeekKey;
    @ConfigProperty(name = "ai.groq.key") String groqKey;
    @ConfigProperty(name = "ai.moonshot.key", defaultValue = "dummy") String moonshotKey;
    @ConfigProperty(name = "ai.google.key", defaultValue = "dummy") String googleKey;

    @Inject
    @RestClient
    GeminiClient geminiClient;

    public String chat(String userMessage) {
        return tryProvider("OpenAI", () -> getOpenAiModel("https://api.openai.com/v1", openAiKey, "gpt-4o"), userMessage,
            () -> tryProvider("DeepSeek", () -> getOpenAiModel("https://api.deepseek.com", deepSeekKey, "deepseek-chat"), userMessage,
            () -> tryProvider("Groq", () -> getOpenAiModel("https://api.groq.com/openai/v1", groqKey, "llama-3.3-70b-versatile"), userMessage,
            () -> tryProvider("Moonshot", () -> getOpenAiModel("https://api.moonshot.cn/v1", moonshotKey, "moonshot-v1-8k"), userMessage,
            () -> tryGemini(userMessage,
            () -> "Lo siento, todos los proveedores de IA están agotados o fuera de línea.")))));
    }

    private String tryProvider(String name, Supplier<ChatLanguageModel> modelSupplier, String message, Supplier<String> nextFallback) {
        try {
            System.out.println(">>> Intentando con: " + name);
            ChatLanguageModel model = modelSupplier.get();
            String response = model.generate(message);
            return response + "\n\n(Mensaje generado por: " + name + ")";
        } catch (Exception e) {
            System.err.println("!!! Error en " + name + ": " + e.getMessage());
            return nextFallback.get();
        }
    }

    private String tryGemini(String message, Supplier<String> nextFallback) {
        try {
            System.out.println(">>> Intentando con: Google Gemini (vía REST)");
            var response = geminiClient.generate(googleKey, new GeminiClient.GeminiRequest(message));
            
            // Extraer texto de la respuesta de Gemini (Estructura compleja de Maps)
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");
            
            return text + "\n\n(Mensaje generado por: Google Gemini)";
        } catch (Exception e) {
            System.err.println("!!! Error en Gemini: " + e.getMessage());
            return nextFallback.get();
        }
    }

    private ChatLanguageModel getOpenAiModel(String baseUrl, String apiKey, String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(8))
                .build();
    }
}
