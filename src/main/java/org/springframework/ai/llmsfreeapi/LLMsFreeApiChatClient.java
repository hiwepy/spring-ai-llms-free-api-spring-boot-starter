package org.springframework.ai.llmsfreeapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.llmsfreeapi.api.LLMsFreeApi;
import org.springframework.ai.llmsfreeapi.api.LLMsFreeApiChatOptions;
import org.springframework.ai.llmsfreeapi.util.ApiUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LLMsFreeApiChatClient
        extends AbstractFunctionCallSupport<LLMsFreeApi.ChatCompletionMessage, LLMsFreeApi.ChatCompletionRequest, ResponseEntity<LLMsFreeApi.ChatCompletion>>
        implements ChatClient, StreamingChatClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * Default options to be used for all chat requests.
     */
    private LLMsFreeApiChatOptions defaultOptions;
    /**
     * Low-level 智普 API library.
     */
    private final LLMsFreeApi llmsFreeApi;
    private final RetryTemplate retryTemplate;

    public LLMsFreeApiChatClient(LLMsFreeApi llmsFreeApi) {
        this(llmsFreeApi, LLMsFreeApiChatOptions.builder()
                        .withModel(LLMsFreeApi.ChatModel.KIMI.getValue())
                        .withMaxToken(ApiUtils.DEFAULT_MAX_TOKENS)
                        .withDoSample(Boolean.TRUE)
                        .withTemperature(ApiUtils.DEFAULT_TEMPERATURE)
                        .withTopP(ApiUtils.DEFAULT_TOP_P)
                        .build());
    }

    public LLMsFreeApiChatClient(LLMsFreeApi llmsFreeApi, LLMsFreeApiChatOptions options) {
        this(llmsFreeApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    public LLMsFreeApiChatClient(LLMsFreeApi llmsFreeApi, LLMsFreeApiChatOptions options,
                                 FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
        super(functionCallbackContext);
        Assert.notNull(llmsFreeApi, "LLMsFreeApi must not be null");
        Assert.notNull(options, "Options must not be null");
        Assert.notNull(retryTemplate, "RetryTemplate must not be null");
        this.llmsFreeApi = llmsFreeApi;
        this.defaultOptions = options;
        this.retryTemplate = retryTemplate;
    }


    @Override
    public ChatResponse call(Prompt prompt) {

        var request = createRequest(prompt, false);

        return retryTemplate.execute(ctx -> {

            ResponseEntity<LLMsFreeApi.ChatCompletion> completionEntity = this.callWithFunctionSupport(request);

            var chatCompletion = completionEntity.getBody();
            if (chatCompletion == null) {
                log.warn("No chat completion returned for prompt: {}", prompt);
                return new ChatResponse(List.of());
            }

            List<Generation> generations = chatCompletion.choices()
                    .stream()
                    .map(choice -> new Generation(choice.message().content(), toMap(chatCompletion.id(), choice))
                            .withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null)))
                    .toList();

            return new ChatResponse(generations);
        });
    }

    private Map<String, Object> toMap(String id, LLMsFreeApi.ChatCompletion.Choice choice) {
        Map<String, Object> map = new HashMap<>();

        var message = choice.message();
        if (message.role() != null) {
            map.put("role", message.role().name());
        }
        if (choice.finishReason() != null) {
            map.put("finishReason", choice.finishReason().name());
        }
        map.put("id", id);
        return map;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        var request = createRequest(prompt, true);

        return retryTemplate.execute(ctx -> {

            var completionChunks = this.llmsFreeApi.chatCompletionStream(request);

            // For chunked responses, only the first chunk contains the choice role.
            // The rest of the chunks with same ID share the same role.
            ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

            return completionChunks.map(chunk -> toChatCompletion(chunk)).map(chatCompletion -> {

                chatCompletion = handleFunctionCallOrReturn(request, ResponseEntity.of(Optional.of(chatCompletion)))
                        .getBody();

                @SuppressWarnings("null")
                String id = chatCompletion.id();

                List<Generation> generations = chatCompletion.choices().stream().map(choice -> {
                    if (choice.message().role() != null) {
                        roleMap.putIfAbsent(id, choice.message().role().name());
                    }
                    String finish = (choice.finishReason() != null ? choice.finishReason().name() : "");
                    var generation = new Generation(choice.message().content(),
                            Map.of("id", id, "role", roleMap.get(id), "finishReason", finish));
                    if (choice.finishReason() != null) {
                        generation = generation
                                .withGenerationMetadata(ChatGenerationMetadata.from(choice.finishReason().name(), null));
                    }
                    return generation;
                }).toList();
                return new ChatResponse(generations);
            });
        });
    }

    private LLMsFreeApi.ChatCompletion toChatCompletion(LLMsFreeApi.ChatCompletionChunk chunk) {
        List<LLMsFreeApi.ChatCompletion.Choice> choices = chunk.choices()
                .stream()
                .map(cc -> new LLMsFreeApi.ChatCompletion.Choice(cc.index(), cc.delta(), cc.finishReason()))
                .toList();

        return new LLMsFreeApi.ChatCompletion(chunk.id(), "chat.completion", chunk.created(), chunk.model(), choices, chunk.requestId(),null);
    }

    /**
     * Accessible for testing.
     */
    LLMsFreeApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

        Set<String> functionsForThisRequest = new HashSet<>();

        var chatCompletionMessages = prompt.getInstructions()
                .stream()
                .map(m -> new LLMsFreeApi.ChatCompletionMessage(m.getContent(),
                        LLMsFreeApi.ChatCompletionMessage.Role.valueOf(m.getMessageType().name())))
                .toList();

        var request = new LLMsFreeApi.ChatCompletionRequest(null, chatCompletionMessages, stream);

        if (this.defaultOptions != null) {
            Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
                    !IS_RUNTIME_CALL);

            functionsForThisRequest.addAll(defaultEnabledFunctions);

            request = ModelOptionsUtils.merge(request, this.defaultOptions, LLMsFreeApi.ChatCompletionRequest.class);
        }

        if (prompt.getOptions() != null) {
            if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
                var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions, ChatOptions.class,
                        LLMsFreeApiChatOptions.class);

                Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
                        IS_RUNTIME_CALL);
                functionsForThisRequest.addAll(promptEnabledFunctions);

                request = ModelOptionsUtils.merge(updatedRuntimeOptions, request,
                        LLMsFreeApi.ChatCompletionRequest.class);
            }
            else {
                throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
                        + prompt.getOptions().getClass().getSimpleName());
            }
        }

        // Add the enabled functions definitions to the request's tools parameter.
        if (!CollectionUtils.isEmpty(functionsForThisRequest)) {

            request = ModelOptionsUtils.merge(
                    LLMsFreeApiChatOptions.builder().withTools(this.getFunctionTools(functionsForThisRequest)).build(),
                    request, LLMsFreeApi.ChatCompletionRequest.class);
        }

        return request;
    }

    private List<LLMsFreeApi.FunctionTool> getFunctionTools(Set<String> functionNames) {
        return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
            var function = new LLMsFreeApi.FunctionTool.Function(functionCallback.getDescription(),
                    functionCallback.getName(), functionCallback.getInputTypeSchema());
            return new LLMsFreeApi.FunctionTool(function);
        }).toList();
    }

    //
    // Function Calling Support
    //
    @Override
    protected LLMsFreeApi.ChatCompletionRequest doCreateToolResponseRequest(LLMsFreeApi.ChatCompletionRequest previousRequest,
                                                                            LLMsFreeApi.ChatCompletionMessage responseMessage,
                                                                            List<LLMsFreeApi.ChatCompletionMessage> conversationHistory) {

        // Every tool-call item requires a separate function call and a response (TOOL)
        // message.
        for (LLMsFreeApi.ChatCompletionMessage.ToolCall toolCall : responseMessage.toolCalls()) {

            var functionName = toolCall.function().name();
            String functionArguments = toolCall.function().arguments();

            if (!this.functionCallbackRegister.containsKey(functionName)) {
                throw new IllegalStateException("No function callback found for function name: " + functionName);
            }

            String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

            // Add the function response to the conversation.
            conversationHistory
                    .add(new LLMsFreeApi.ChatCompletionMessage(functionResponse, LLMsFreeApi.ChatCompletionMessage.Role.TOOL, functionName, null));
        }

        // Recursively call chatCompletionWithTools until the model doesn't call a
        // functions anymore.
        LLMsFreeApi.ChatCompletionRequest newRequest = new LLMsFreeApi.ChatCompletionRequest(previousRequest.requestId(), conversationHistory, false);
        newRequest = ModelOptionsUtils.merge(newRequest, previousRequest, LLMsFreeApi.ChatCompletionRequest.class);

        return newRequest;
    }

    @Override
    protected List<LLMsFreeApi.ChatCompletionMessage> doGetUserMessages(LLMsFreeApi.ChatCompletionRequest request) {
        return request.messages();
    }

    @SuppressWarnings("null")
    @Override
    protected LLMsFreeApi.ChatCompletionMessage doGetToolResponseMessage(ResponseEntity<LLMsFreeApi.ChatCompletion> chatCompletion) {
        return chatCompletion.getBody().choices().iterator().next().message();
    }

    @Override
    protected ResponseEntity<LLMsFreeApi.ChatCompletion> doChatCompletion(LLMsFreeApi.ChatCompletionRequest request) {
        return this.LLMsFreeApi.chatCompletionEntity(request);
    }

    @Override
    protected boolean isToolFunctionCall(ResponseEntity<LLMsFreeApi.ChatCompletion> chatCompletion) {

        var body = chatCompletion.getBody();
        if (body == null) {
            return false;
        }

        var choices = body.choices();
        if (CollectionUtils.isEmpty(choices)) {
            return false;
        }

        return !CollectionUtils.isEmpty(choices.get(0).message().toolCalls());
    }
}
