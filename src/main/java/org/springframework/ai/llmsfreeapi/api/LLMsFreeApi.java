package org.springframework.ai.llmsfreeapi.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.llmsfreeapi.util.ApiUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class LLMsFreeApi {

    private static final Logger logger = LoggerFactory.getLogger(LLMsFreeApi.class);
    private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;
    private static final String REQUEST_BODY_NULL_ERROR = "The request body can not be null.";

    private final RestClient restClient;

    private final WebClient webClient;

    /**
     * Create a new client api with DEFAULT_BASE_URL
     * @param apiKey LLMs Free API Key.
     */
    public LLMsFreeApi(String apiKey) {
        this(ApiUtils.DEFAULT_BASE_URL, apiKey);
    }

    /**
     * Create a new client api.
     * @param baseUrl api base URL.
     * @param apiKey LLMs Free API Key.
     */
    public LLMsFreeApi(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
    }

    /**
     * Create a new client api.
     * @param baseUrl api base URL.
     * @param apiKey LLMs Free API Key.
     * @param restClientBuilder RestClient builder.
     * @param responseErrorHandler Response error handler.
     */
    public LLMsFreeApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
                       ResponseErrorHandler responseErrorHandler) {

        Consumer<HttpHeaders> jsonContentHeaders = ApiUtils.getJsonContentHeaders(apiKey);

        this.restClient = restClientBuilder.baseUrl(baseUrl)
                .defaultHeaders(jsonContentHeaders)
                .defaultStatusHandler(responseErrorHandler)
                .build();

        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeaders(jsonContentHeaders).build();
    }

    // --------------------------------------------------------------------------
    // Chat & Streaming Chat
    // --------------------------------------------------------------------------

    /**
     * Represents a tool the model may call. Currently, only functions are supported as a
     * tool.
     *
     * @param type The type of the tool. Currently, only 'function' is supported.
     * @param function The function definition.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionTool(@JsonProperty("type") FunctionTool.Type type, @JsonProperty("function") FunctionTool.Function function) {

        /**
         * Create a tool of type 'function' and the given function definition.
         * @param function function definition.
         */
        @ConstructorBinding
        public FunctionTool(FunctionTool.Function function) {
            this(FunctionTool.Type.FUNCTION, function);
        }

        /**
         * Create a tool of type 'function' and the given function definition.
         */
        public enum Type {

            /**
             * Function tool type.
             */
            @JsonProperty("function")
            FUNCTION

        }

        /**
         * Function definition.
         *
         * @param description A description of what the function does, used by the model
         * to choose when and how to call the function.
         * @param name The name of the function to be called. Must be a-z, A-Z, 0-9, or
         * contain underscores and dashes, with a maximum length of 64.
         * @param parameters The parameters the functions accepts, described as a JSON
         * Schema object. To describe a function that accepts no parameters, provide the
         * value {"type": "object", "properties": {}}.
         */
        public record Function(@JsonProperty("description") String description, @JsonProperty("name") String name,
                               @JsonProperty("parameters") Map<String, Object> parameters) {

            /**
             * Create tool function definition.
             * @param description tool function description.
             * @param name tool function name.
             * @param jsonSchema tool function schema as json.
             */
            @ConstructorBinding
            public Function(String description, String name, String jsonSchema) {
                this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema));
            }
        }
    }

    /**
     * Chat completion request object.
     * @param model 所要调用的模型编码
     * @param messages 调用语言模型时，将当前对话信息列表作为提示输入给模型， 按照 {"role": "user", "content": "你好"} 的json 数组形式进行传参； 可能的消息类型包括 System message、User message、Assistant message 和 Tool message。
     * @param stream 是否开启流式调用，默认false
     * @param useSearch 是否开启联网搜索，默认false
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            @JsonProperty("model") String model,
            @JsonProperty("messages") List<ChatCompletionMessage> messages,
            @JsonProperty("stream") Boolean stream,
            @JsonProperty("use_search") Boolean useSearch) {

        public ChatCompletionRequest(String model, List<ChatCompletionMessage> messages, Boolean stream) {
            this(model, messages, stream, false);
        }

        /**
         * 用于控制模型是如何选择要调用的函数，仅当工具类型为function时补充。默认为auto，当前仅支持auto
         */
        public enum ToolChoice {

            @JsonProperty("auto") AUTO

        }

        /**
         * Helper factory that creates a tool_choice of type 'none', 'auto' or selected function by name.
         */
        public static class ToolChoiceBuilder {
            /**
             * Model can pick between generating a message or calling a function.
             */
            public static final String AUTO = "none";
            /**
             * Model will not call a function and instead generates a message
             */
            public static final String NONE = "none";

            /**
             * Specifying a particular function forces the model to call that function.
             */
            public static String FUNCTION(String functionName) {
                return ModelOptionsUtils.toJsonString(Map.of("type", "function", "function", Map.of("name", functionName)));
            }
        }

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionMessage(
            @JsonProperty("content") Object content,
            @JsonProperty("role") ChatCompletionMessage.Role role,
            @JsonProperty("name") String name,
            @JsonProperty("tool_calls") List<ChatCompletionMessage.ToolCall> toolCalls) {

        /**
         * Create a chat completion message with the given content and role. All other fields are null.
         *
         * @param content The content of the message.
         * @param role    The role of the author of this message.
         */
        public ChatCompletionMessage(Object content, ChatCompletionMessage.Role role) {
            this(content, role, null, null);
        }

        /**
         * The role of the author of this message.
         */
        public enum Role {
            /**
             * System message.
             */
            @JsonProperty("system") SYSTEM,
            /**
             * User message.
             */
            @JsonProperty("user") USER,
            /**
             * Assistant message.
             */
            @JsonProperty("assistant") ASSISTANT,
            /**
             * Tool message.
             */
            @JsonProperty("tool") TOOL
        }

        /**
         * The relevant tool call.
         *
         * @param id       The ID of the tool call. This ID must be referenced when you submit the tool outputs in using the
         *                 Submit tool outputs to run endpoint.
         * @param type     The type of tool call the output is required for. For now, this is always function.
         * @param function The function definition.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ToolCall(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("function") ChatCompletionMessage.ChatCompletionFunction function) {
        }

        /**
         * The Document Content definition.
         *
         * @param type      The type of the Content .
         * @param text      The text of the Content.
         * @param fileUrl   The file url of the Content.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record DocumentContent (
            @JsonProperty("type") String type,
            @JsonProperty("text") String text,
            @JsonProperty("file_url") DocumentFile fileUrl) {

            public DocumentContent(String type, String text) {
                this(type, text, null);
            }

            public DocumentContent(String type, DocumentFile fileUrl) {
                this(type, null, fileUrl);
            }

        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record DocumentFile(
                @JsonProperty("url") String url) {
        }

        /**
         * The Document Content definition.
         *
         * @param type      The type of the Content .
         * @param text      The text of the Content.
         * @param imageUrl   The image url of the Content.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ImageContent (
                @JsonProperty("type") String type,
                @JsonProperty("text") String text,
                @JsonProperty("image_url") ImageFile imageUrl) {

            public ImageContent(String type, String text) {
                this(type, text, null);
            }

            public ImageContent(String type, ImageFile imageUrl) {
                this(type, null, imageUrl);
            }

        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ImageFile(
            @JsonProperty("url") String url) {
        }
        /**
         * The function definition.
         *
         * @param name      The name of the function.
         * @param arguments The arguments that the model expects you to pass to the function.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ChatCompletionFunction(
            @JsonProperty("name") String name,
            @JsonProperty("arguments") String arguments) {
        }
    }


    /**
     * The reason the model stopped generating tokens.
     * 模型推理终止的原因
     */
    public enum ChatCompletionFinishReason {

        /**
         * 推理自然结束或触发停止词
         */
        @JsonProperty("stop") STOP,
        /**
         * 到达 tokens 长度上限
         */
        @JsonProperty("length") LENGTH,
        /**
         * 模型推理内容被安全审核接口拦截。请注意，针对此类内容，请用户自行判断并决定是否撤回已公开的内容
         */
        @JsonProperty("sensitive") SENSITIVE,
        /**
         * 模型命中函数
         */
        @JsonProperty("tool_calls") TOOL_CALLS,
        /**
         * 模型推理异常
         */
        @JsonProperty("network_error") NETWORK_ERROR

    }


    /**
     * 模型同步调用响应内容
     * Represents a chat completion response returned by model, based on the provided
     * input.
     *
     * @param id A unique identifier for the chat completion.
     * @param created The Unix timestamp (in seconds) of when the chat completion was
     * created.
     * @param model The model used for the chat completion.
     * @param choices A list of chat completion choices.
     * @param usage Usage statistics for the completion request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletion(
            @JsonProperty("id") String id,
            @JsonProperty("object") String object,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,
            @JsonProperty("choices") List<ChatCompletion.Choice> choices,
            @JsonProperty("usage") Usage usage) {
        // @formatter:on

        /**
         * Chat completion choice.
         *
         * @param index The index of the choice in the list of choices.
         * @param message A chat completion message generated by the model.
         * @param finishReason The reason the model stopped generating tokens.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Choice(
                @JsonProperty("index") Integer index,
                @JsonProperty("message") ChatCompletionMessage message,
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason) {
        }
    }

    /**
     * Represents a streamed chunk of a chat completion response returned by model, based
     * on the provided input.
     *
     * @param id A unique identifier for the chat completion. Each chunk has the same ID.
     * @param object The object type, which is always 'chat.completion.chunk'.
     * @param created The Unix timestamp (in seconds) of when the chat completion was
     * created. Each chunk has the same timestamp.
     * @param model The model used for the chat completion.
     * @param choices A list of chat completion choices. Can be more than one if n is
     * greater than 1.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionChunk(
            // @formatter:off
            @JsonProperty("id") String id,
            @JsonProperty("object") String object,
            @JsonProperty("created") Long created,
            @JsonProperty("model") String model,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("choices") List<ChatCompletionChunk.ChunkChoice> choices) {
        // @formatter:on

        /**
         * Chat completion choice.
         *
         * @param index The index of the choice in the list of choices.
         * @param delta A chat completion delta generated by streamed model responses.
         * @param finishReason The reason the model stopped generating tokens.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ChunkChoice(
                // @formatter:off
                @JsonProperty("index") Integer index,
                @JsonProperty("delta") ChatCompletionMessage delta,
                @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason) {
            // @formatter:on
        }
    }

    /**
     * Usage statistics.
     *
     * @param promptTokens     Number of tokens in the prompt.
     * @param totalTokens      Total number of tokens used in the request (prompt + completion).
     * @param completionTokens Number of tokens in the generated completion. Only
     *                         applicable for completion requests.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
            // @formatter:off
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("completion_tokens") Integer completionTokens) {
        // @formatter:on
    }


    /**
     * List of well-known 智普AI chat models.
     * https://github.com/LLM-Red-Team
     *
     * <p>
     * LLMs Free API provides three API endpoints featuring five leading Large Language
     * Models:
     * </p>
     * <ul>
     * <li><b>KIMI</b> - Moonshot AI（Kimi.ai）</li>
     * <li><b>STEP_CHAT</b> - 阶跃星辰 (跃问StepChat)</li>
     * <li><b>QWEN</b> - 阿里通义 (Qwen)</li>
     * <li><b>GLM_4</b> - ZhipuAI (智谱清言) </li>
     * <li><b>METASO</b> - 秘塔AI (metaso) </li>
     * <li><b>EMOHAA</b> - 聆心智能 (Emohaa)</li>
     * </ul>
     */
    public enum ChatModel {

        KIMI("kimi"),
        STEP_CHAT("StepChat"),
        QWEN("qwen"),
        GLM_4("glm-4"),
        METASO("metaso"),
        EMOHAA("emohaa");

        private final String value;

        ChatModel(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

    }

    /**
     * Creates a model response for the given chat conversation.
     * @param chatRequest The chat completion request.
     * @return Entity response with {@link LLMsFreeApi.ChatCompletion} as a body and HTTP status code
     * and headers.
     */
    public ResponseEntity<LLMsFreeApi.ChatCompletion> chatCompletionEntity(LLMsFreeApi.ChatCompletionRequest chatRequest) {

        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

        return this.restClient.post()
                .uri("/v1/chat/completions")
                .body(chatRequest)
                .retrieve()
                .toEntity(LLMsFreeApi.ChatCompletion.class);
    }

    private LLMsFreeApiStreamFunctionCallingHelper chunkMerger = new LLMsFreeApiStreamFunctionCallingHelper();

    /**
     * Creates a streaming chat response for the given chat conversation.
     * @param chatRequest The chat completion request. Must have the stream property set
     * to true.
     * @return Returns a {@link Flux} stream from chat completion chunks.
     */
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {

        Assert.notNull(chatRequest, "The request body can not be null.");
        Assert.isTrue(chatRequest.stream(), "Request must set the steam property to true.");

        AtomicBoolean isInsideTool = new AtomicBoolean(false);

        return this.webClient.post()
                .uri("/v1/chat/completions")
                .body(Mono.just(chatRequest), ChatCompletionRequest.class)
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil(SSE_DONE_PREDICATE)
                .filter(SSE_DONE_PREDICATE.negate())
                .map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
                .map(chunk -> {
                    if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
                        isInsideTool.set(true);
                    }
                    return chunk;
                })
                .windowUntil(chunk -> {
                    if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
                        isInsideTool.set(false);
                        return true;
                    }
                    return !isInsideTool.get();
                })
                .concatMapIterable(window -> {
                    Mono<ChatCompletionChunk> mono1 = window.reduce(new ChatCompletionChunk(null, null, null, null, null,null),
                            (previous, current) -> this.chunkMerger.merge(previous, current));
                    return List.of(mono1);
                })
                .flatMap(mono -> mono);
    }


}
