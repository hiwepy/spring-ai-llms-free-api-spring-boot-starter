package org.springframework.ai.llmsfreeapi.autoconfigure;

import org.springframework.ai.llmsfreeapi.api.LLMsFreeApiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(LLMsFreeApiChatProperties.CONFIG_PREFIX)
public class LLMsFreeApiChatProperties {

    public static final String CONFIG_PREFIX = "spring.ai.llmsfreeapi.chat";


    /**
     * Enable LLMsFreeApi chat client.
     */
    private boolean enabled = true;

    /**
     * Client lever LLMsFreeApi options. Use this property to configure generative temperature,
     * topK and topP and alike parameters. The null values are ignored defaulting to the
     * generative's defaults.
     */
    @NestedConfigurationProperty
    private LLMsFreeApiChatOptions options = LLMsFreeApiChatOptions.builder()
            .build();

    public LLMsFreeApiChatOptions getOptions() {
        return this.options;
    }

    public void setOptions(LLMsFreeApiChatOptions options) {
        this.options = options;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
