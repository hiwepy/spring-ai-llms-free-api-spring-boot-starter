package org.springframework.ai.llmsfreeapi.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMsFreeApiChatOptions implements FunctionCallingOptions, ChatOptions {

    /**
     * 所要调用的模型编码
     */
    @JsonProperty("model")
    private String model;

    /**
     * 是否开启联网搜索，默认false
     */
    @JsonProperty("use_search")
    private Boolean useSearch;

    /**
     * 可供模型调用的工具列表,tools 字段会计算 tokens ，同样受到 tokens 长度的限制
     */
    @NestedConfigurationProperty
    private @JsonProperty("tools") List<LLMsFreeApi.FunctionTool> tools;

    /**
     * 用于控制模型是如何选择要调用的函数，仅当工具类型为function时补充。默认为auto，当前仅支持auto
     */
    @NestedConfigurationProperty
    private @JsonProperty("tool_choice") LLMsFreeApi.ChatCompletionRequest.ToolChoice toolChoice;

    @Override
    public List<FunctionCallback> getFunctionCallbacks() {
        return null;
    }

    @Override
    public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {

    }

    @Override
    public Set<String> getFunctions() {
        return null;
    }

    @Override
    public void setFunctions(Set<String> functions) {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final LLMsFreeApiChatOptions options = new LLMsFreeApiChatOptions();

        public Builder withModel(String model) {
            this.options.setModel(model);
            return this;
        }

        public Builder withUseSearch(Boolean useSearch) {
            this.options.setUseSearch(useSearch);
            return this;
        }

        public Builder withTools(List<LLMsFreeApi.FunctionTool> tools) {
            this.options.setTools(tools);
            return this;
        }

        public Builder withToolChoice(LLMsFreeApi.ChatCompletionRequest.ToolChoice toolChoice) {
            this.options.setToolChoice(toolChoice);
            return this;
        }

        public LLMsFreeApiChatOptions build() {
            return this.options;
        }

    }

    @Override
    public Float getTemperature() {
        throw new UnsupportedOperationException("Unimplemented method 'getTemperature'");
    }

    public void setTemperature(Float temperature) {
        throw new UnsupportedOperationException("Unimplemented method 'setTemperature'");
    }

    @Override
    public Float getTopP() {
        throw new UnsupportedOperationException("Unimplemented method 'getTopP'");
    }

    public void setTopP(Float topP) {
        throw new UnsupportedOperationException("Unimplemented method 'setTopP'");
    }

    @Override
    @JsonIgnore
    public Integer getTopK() {
        throw new UnsupportedOperationException("Unimplemented method 'getTopK'");
    }

    @JsonIgnore
    public void setTopK(Integer topK) {
        throw new UnsupportedOperationException("Unimplemented method 'setTopK'");
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getModel() {
        return model;
    }

    public Boolean getUseSearch() {
        return useSearch;
    }

    public void setUseSearch(Boolean useSearch) {
        this.useSearch = useSearch;
    }

    public List<LLMsFreeApi.FunctionTool> getTools() {
        return tools;
    }

    public void setTools(List<LLMsFreeApi.FunctionTool> tools) {
        this.tools = tools;
    }

    public LLMsFreeApi.ChatCompletionRequest.ToolChoice getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(LLMsFreeApi.ChatCompletionRequest.ToolChoice toolChoice) {
        this.toolChoice = toolChoice;
    }

    /**
     * Convert the {@link LLMsFreeApiChatOptions} object to a {@link Map} of key/value pairs.
     * @return The {@link Map} of key/value pairs.
     */
    public Map<String, Object> toMap() {
        try {
            var json = new ObjectMapper().writeValueAsString(this);
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper factory method to create a new {@link LLMsFreeApiChatOptions} instance.
     * @return A new {@link LLMsFreeApiChatOptions} instance.
     */
    public static LLMsFreeApiChatOptions create() {
        return new LLMsFreeApiChatOptions();
    }

    /**
     * Filter out the non supported fields from the options.
     * @param options The options to filter.
     * @return The filtered options.
     */
    public static Map<String, Object> filterNonSupportedFields(Map<String, Object> options) {
        return options.entrySet().stream()
                .filter(e -> !e.getKey().equals("model"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


}
