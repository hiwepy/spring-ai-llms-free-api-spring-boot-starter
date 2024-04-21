package org.springframework.ai.llmsfreeapi.chat.messages;

import org.springframework.ai.chat.messages.ChatMessage;
import org.springframework.ai.chat.messages.MessageType;

import java.util.Map;

public class ImageChatMessage extends ChatMessage {

   private String imageUrl;

    public ImageChatMessage(String role, String imageUrl, String content) {
        super(MessageType.valueOf(role), content);
        this.imageUrl = imageUrl;
    }

    public ImageChatMessage(String role, String imageUrl, String content, Map<String, Object> properties) {
        super(MessageType.valueOf(role), content, properties);
        this.imageUrl = imageUrl;
    }

    public ImageChatMessage(MessageType messageType, String imageUrl, String content) {
        super(messageType, content);
        this.imageUrl = imageUrl;
    }

    public ImageChatMessage(MessageType messageType, String imageUrl, String content, Map<String, Object> properties) {
        super(messageType, content, properties);
        this.imageUrl = imageUrl;
    }

    public String getFileUrl() {
        return imageUrl;
    }

}
