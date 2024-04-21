package org.springframework.ai.llmsfreeapi.api;

import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LLMsFreeApiStreamFunctionCallingHelper {

    /**
     * Merge the previous and current ChatCompletionChunk into a single one.
     * @param previous the previous ChatCompletionChunk
     * @param current the current ChatCompletionChunk
     * @return the merged ChatCompletionChunk
     */
    public LLMsFreeApi.ChatCompletionChunk merge(LLMsFreeApi.ChatCompletionChunk previous, LLMsFreeApi.ChatCompletionChunk current) {

        if (previous == null) {
            return current;
        }

        String id = (current.id() != null ? current.id() : previous.id());
        Long created = (current.created() != null ? current.created() : previous.created());
        String model = (current.model() != null ? current.model() : previous.model());
        String requestId = (current.requestId() != null ? current.requestId() : previous.requestId());
        String object = (current.object() != null ? current.object() : previous.object());

        LLMsFreeApi.ChatCompletionChunk.ChunkChoice previousChoice0 = (CollectionUtils.isEmpty(previous.choices()) ? null : previous.choices().get(0));
        LLMsFreeApi.ChatCompletionChunk.ChunkChoice currentChoice0 = (CollectionUtils.isEmpty(current.choices()) ? null : current.choices().get(0));

        LLMsFreeApi.ChatCompletionChunk.ChunkChoice choice = merge(previousChoice0, currentChoice0);

        return new LLMsFreeApi.ChatCompletionChunk(id, object, created, model, requestId, List.of(choice));
    }

    private LLMsFreeApi.ChatCompletionChunk.ChunkChoice merge(LLMsFreeApi.ChatCompletionChunk.ChunkChoice previous, LLMsFreeApi.ChatCompletionChunk.ChunkChoice current) {
        if (previous == null) {
            if (current.delta() != null && current.delta().toolCalls() != null) {
                Optional<String> id = current.delta()
                        .toolCalls()
                        .stream()
                        .filter(tool -> tool.id() != null)
                        .map(tool -> tool.id())
                        .findFirst();
                if (!id.isPresent()) {
                    var newId = UUID.randomUUID().toString();

                    var toolCallsWithID = current.delta()
                            .toolCalls()
                            .stream()
                            .map(toolCall -> new LLMsFreeApi.ChatCompletionMessage.ToolCall(newId, "function", toolCall.function()))
                            .toList();

                    var role = current.delta().role() != null ? current.delta().role() : LLMsFreeApi.ChatCompletionMessage.Role.ASSISTANT;
                    current = new LLMsFreeApi.ChatCompletionChunk.ChunkChoice(current.index(), new LLMsFreeApi.ChatCompletionMessage(current.delta().content(),
                            role, current.delta().name(), toolCallsWithID), current.finishReason());
                }
            }
            return current;
        }

        LLMsFreeApi.ChatCompletionFinishReason finishReason = (current.finishReason() != null ? current.finishReason()
                : previous.finishReason());
        Integer index = (current.index() != null ? current.index() : previous.index());

        LLMsFreeApi.ChatCompletionMessage message = merge(previous.delta(), current.delta());

        return new LLMsFreeApi.ChatCompletionChunk.ChunkChoice(index, message, finishReason);
    }

    private LLMsFreeApi.ChatCompletionMessage merge(LLMsFreeApi.ChatCompletionMessage previous, LLMsFreeApi.ChatCompletionMessage current) {
        Object content = (current.content() != null ? current.content()
                : "" + ((previous.content() != null) ? previous.content() : ""));
        LLMsFreeApi.ChatCompletionMessage.Role role = (current.role() != null ? current.role() : previous.role());
        role = (role != null ? role : LLMsFreeApi.ChatCompletionMessage.Role.ASSISTANT); // default to ASSISTANT (if null
        String name = (current.name() != null ? current.name() : previous.name());

        List<LLMsFreeApi.ChatCompletionMessage.ToolCall> toolCalls = new ArrayList<>();
        LLMsFreeApi.ChatCompletionMessage.ToolCall lastPreviousTooCall = null;
        if (previous.toolCalls() != null) {
            lastPreviousTooCall = previous.toolCalls().get(previous.toolCalls().size() - 1);
            if (previous.toolCalls().size() > 1) {
                toolCalls.addAll(previous.toolCalls().subList(0, previous.toolCalls().size() - 1));
            }
        }
        if (current.toolCalls() != null) {
            if (current.toolCalls().size() > 1) {
                throw new IllegalStateException("Currently only one tool call is supported per message!");
            }
            var currentToolCall = current.toolCalls().iterator().next();
            if (currentToolCall.id() != null) {
                if (lastPreviousTooCall != null) {
                    toolCalls.add(lastPreviousTooCall);
                }
                toolCalls.add(currentToolCall);
            }
            else {
                toolCalls.add(merge(lastPreviousTooCall, currentToolCall));
            }
        }
        else {
            if (lastPreviousTooCall != null) {
                toolCalls.add(lastPreviousTooCall);
            }
        }
        return new LLMsFreeApi.ChatCompletionMessage(content, role, name, toolCalls);
    }

    private LLMsFreeApi.ChatCompletionMessage.ToolCall merge(LLMsFreeApi.ChatCompletionMessage.ToolCall previous, LLMsFreeApi.ChatCompletionMessage.ToolCall current) {
        if (previous == null) {
            return current;
        }
        String id = (current.id() != null ? current.id() : previous.id());
        String type = (current.type() != null ? current.type() : previous.type());
        LLMsFreeApi.ChatCompletionMessage.ChatCompletionFunction function = merge(previous.function(), current.function());
        return new LLMsFreeApi.ChatCompletionMessage.ToolCall(id, type, function);
    }

    private LLMsFreeApi.ChatCompletionMessage.ChatCompletionFunction merge(LLMsFreeApi.ChatCompletionMessage.ChatCompletionFunction previous, LLMsFreeApi.ChatCompletionMessage.ChatCompletionFunction current) {
        if (previous == null) {
            return current;
        }
        String name = (current.name() != null ? current.name() : previous.name());
        StringBuilder arguments = new StringBuilder();
        if (previous.arguments() != null) {
            arguments.append(previous.arguments());
        }
        if (current.arguments() != null) {
            arguments.append(current.arguments());
        }
        return new LLMsFreeApi.ChatCompletionMessage.ChatCompletionFunction(name, arguments.toString());
    }

    /**
     * @param chatCompletion the ChatCompletionChunk to check
     * @return true if the ChatCompletionChunk is a streaming tool function call.
     */
    public boolean isStreamingToolFunctionCall(LLMsFreeApi.ChatCompletionChunk chatCompletion) {

        var choices = chatCompletion.choices();
        if (CollectionUtils.isEmpty(choices)) {
            return false;
        }

        var choice = choices.get(0);
        return !CollectionUtils.isEmpty(choice.delta().toolCalls());
    }

    /**
     * @param chatCompletion the ChatCompletionChunk to check
     * @return true if the ChatCompletionChunk is a streaming tool function call and it is
     * the last one.
     */
    public boolean isStreamingToolFunctionCallFinish(LLMsFreeApi.ChatCompletionChunk chatCompletion) {

        var choices = chatCompletion.choices();
        if (CollectionUtils.isEmpty(choices)) {
            return false;
        }

        var choice = choices.get(0);
        return choice.finishReason() == LLMsFreeApi.ChatCompletionFinishReason.TOOL_CALLS;
    }

}
// ---
