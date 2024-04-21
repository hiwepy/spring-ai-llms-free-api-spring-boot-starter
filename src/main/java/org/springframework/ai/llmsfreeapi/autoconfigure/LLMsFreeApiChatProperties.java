package org.springframework.ai.llmsfreeapi.autoconfigure;

import org.springframework.ai.llmsfreeapi.api.LLMsFreeApi;
import org.springframework.ai.llmsfreeapi.api.LLMsFreeApiChatOptions;
import org.springframework.ai.llmsfreeapi.util.ApiUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(LLMsFreeApiChatProperties.CONFIG_PREFIX)
public class LLMsFreeApiChatProperties {

    public static final String CONFIG_PREFIX = "spring.ai.zhipuai.chat";


    /**
     * Enable ZhipuAi chat client.
     */
    private boolean enabled = true;

    /**
     * Client lever ZhipuAi options. Use this property to configure generative temperature,
     * topK and topP and alike parameters. The null values are ignored defaulting to the
     * generative's defaults.
     */
    @NestedConfigurationProperty
    private LLMsFreeApiChatOptions options = LLMsFreeApiChatOptions.builder()
            .withModel(LLMsFreeApi.ChatModel.GLM_3_TURBO.getValue())
            .withMaxToken(ApiUtils.DEFAULT_MAX_TOKENS)
            .withDoSample(Boolean.TRUE)
            .withTemperature(ApiUtils.DEFAULT_TEMPERATURE)
            .withTopP(ApiUtils.DEFAULT_TOP_P)
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
