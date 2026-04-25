package com.policyguard.config.stub;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic stub chat model for the {@code stub} profile.
 * Parses [Doc: X, Para: Y] text lines from the prompt and wraps the first
 * hit in a citation response.  No LLM call is made.
 */
@Component
@Profile("stub")
@Primary
public class StubChatModel implements ChatModel {

    private static final Pattern HIT_PATTERN =
            Pattern.compile("\\[Doc:\\s*([^,\\]]+),\\s*Para:\\s*([^\\]]+)\\]\\s*(.+)");

    @Override
    public ChatResponse call(Prompt prompt) {
        String answer = buildAnswer(prompt.getContents());
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(answer), ChatGenerationMetadata.NULL)));
    }

    private String buildAnswer(String promptText) {
        Matcher matcher = HIT_PATTERN.matcher(promptText);
        if (matcher.find()) {
            String docId   = matcher.group(1).strip();
            String paraRef = matcher.group(2).strip();
            String text    = matcher.group(3).strip();
            return "Per the policy: %s [Doc: %s, Para: %s]".formatted(text, docId, paraRef);
        }
        return "I cannot answer this based on the available policy documents.";
    }
}
