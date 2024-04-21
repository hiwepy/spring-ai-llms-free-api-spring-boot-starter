package org.springframework.ai.llmsfreeapi.chat.messages;

import org.springframework.ai.chat.messages.ChatMessage;
import org.springframework.ai.chat.messages.MessageType;

import java.util.Map;

public class DocumentChatMessage extends ChatMessage {

   private String fileUrl;

    public DocumentChatMessage(String role, String fileUrl, String content) {
        super(MessageType.valueOf(role), content);
        this.fileUrl = fileUrl;
    }

    public DocumentChatMessage(String role, String fileUrl, String content, Map<String, Object> properties) {
        super(MessageType.valueOf(role), content, properties);
        this.fileUrl = fileUrl;
    }

    public DocumentChatMessage(MessageType messageType, String fileUrl, String content) {
        super(messageType, content);
        this.fileUrl = fileUrl;
    }

    public DocumentChatMessage(MessageType messageType, String fileUrl, String content, Map<String, Object> properties) {
        super(messageType, content, properties);
        this.fileUrl = fileUrl;
    }

    public String getFileUrl() {
        return fileUrl;
    }

}
