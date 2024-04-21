package org.springframework.ai.llmsfreeapi.aot;

import org.springframework.ai.llmsfreeapi.api.LLMsFreeApi;
import org.springframework.ai.llmsfreeapi.api.LLMsFreeApiChatOptions;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

public class LLMsFreeApiRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        var mcs = MemberCategory.values();
        for (var tr : findJsonAnnotatedClassesInPackage(LLMsFreeApi.class)) {
            hints.reflection().registerType(tr, mcs);
        }
        for (var tr : findJsonAnnotatedClassesInPackage(LLMsFreeApiChatOptions.class)) {
            hints.reflection().registerType(tr, mcs);
        }
    }

}
