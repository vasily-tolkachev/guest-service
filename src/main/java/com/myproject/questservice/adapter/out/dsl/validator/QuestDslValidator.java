package com.myproject.questservice.adapter.out.dsl.validator;

import com.myproject.questservice.adapter.out.dsl.ast.ConditionAst;
import com.myproject.questservice.adapter.out.dsl.ast.EffectAst;
import com.myproject.questservice.adapter.out.dsl.ast.NodeAst;
import com.myproject.questservice.adapter.out.dsl.ast.OptionAst;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.adapter.out.dsl.error.DslError;
import com.myproject.questservice.adapter.out.dsl.error.DslProcessingException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class QuestDslValidator {

    public void validate(QuestAst ast) {
        if (ast.id() == null || ast.id().isBlank()) {
            throw new DslProcessingException(new DslError(
                    "DSL_VALIDATION_ERROR",
                    "Quest id is required.",
                    1,
                    1
            ));
        }
        if (ast.title() == null || ast.title().isBlank()) {
            throw new DslProcessingException(new DslError(
                    "DSL_VALIDATION_ERROR",
                    "Quest title is required.",
                    1,
                    1
            ));
        }
        if (ast.nodes().isEmpty()) {
            throw new DslProcessingException(new DslError(
                    "DSL_VALIDATION_ERROR",
                    "At least one node is required.",
                    1,
                    1
            ));
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeAst node : ast.nodes()) {
            if (!nodeIds.add(node.id())) {
                throw new DslProcessingException(new DslError(
                        "DSL_VALIDATION_ERROR",
                        "Duplicate node id: " + node.id(),
                        node.line(),
                        node.column()
                ));
            }
            if (node.text() == null || node.text().isBlank()) {
                throw new DslProcessingException(new DslError(
                        "DSL_VALIDATION_ERROR",
                        "Node text is required for node: " + node.id(),
                        node.line(),
                        node.column()
                ));
            }
        }

        String startNodeId = ast.nodes().getFirst().id();
        if (!nodeIds.contains(startNodeId)) {
            throw new DslProcessingException(new DslError(
                    "DSL_VALIDATION_ERROR",
                    "Start node not found: " + startNodeId,
                    1,
                    1
            ));
        }

        for (NodeAst node : ast.nodes()) {
            for (OptionAst option : node.options()) {
                if (!nodeIds.contains(option.targetNodeId())) {
                    throw new DslProcessingException(new DslError(
                            "DSL_VALIDATION_ERROR",
                            "Node '" + option.targetNodeId() + "' not found.",
                            option.line(),
                            option.column()
                    ));
                }
                validateConditions(option.conditions());
                validateEffects(option.effects());
            }
        }
    }

    private void validateConditions(java.util.List<ConditionAst> conditions) {
        for (ConditionAst condition : conditions) {
            String name = condition.name().toLowerCase();
            int argsCount = condition.arguments().size();

            if ("hasfact".equals(name) || "not".equals(name)) {
                if (argsCount != 1) {
                    throw new DslProcessingException(new DslError(
                            "DSL_VALIDATION_ERROR",
                            "Condition '" + condition.name() + "' expects exactly 1 argument.",
                            condition.line(),
                            condition.column()
                    ));
                }
                continue;
            }
            if ("and".equals(name) || "or".equals(name)) {
                if (argsCount < 2) {
                    throw new DslProcessingException(new DslError(
                            "DSL_VALIDATION_ERROR",
                            "Condition '" + condition.name() + "' expects at least 2 arguments.",
                            condition.line(),
                            condition.column()
                    ));
                }
                continue;
            }

            throw new DslProcessingException(new DslError(
                    "DSL_VALIDATION_ERROR",
                    "Unknown condition: " + condition.name(),
                    condition.line(),
                    condition.column()
            ));
        }
    }

    private void validateEffects(java.util.List<EffectAst> effects) {
        for (EffectAst effect : effects) {
            String name = effect.name().toLowerCase();
            int argsCount = effect.arguments().size();

            if ("addfact".equals(name) || "removefact".equals(name)) {
                if (argsCount != 1) {
                    throw new DslProcessingException(new DslError(
                            "DSL_VALIDATION_ERROR",
                            "Effect '" + effect.name() + "' expects exactly 1 argument.",
                            effect.line(),
                            effect.column()
                    ));
                }
                continue;
            }

            throw new DslProcessingException(new DslError(
                    "DSL_VALIDATION_ERROR",
                    "Unknown effect: " + effect.name(),
                    effect.line(),
                    effect.column()
            ));
        }
    }
}
