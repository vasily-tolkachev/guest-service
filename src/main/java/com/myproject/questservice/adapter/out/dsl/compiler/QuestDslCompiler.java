package com.myproject.questservice.adapter.out.dsl.compiler;

import com.myproject.questservice.adapter.out.dsl.ast.ConditionAst;
import com.myproject.questservice.adapter.out.dsl.ast.EffectAst;
import com.myproject.questservice.adapter.out.dsl.ast.NodeAst;
import com.myproject.questservice.adapter.out.dsl.ast.OptionAst;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.domain.AndCondition;
import com.myproject.questservice.domain.Condition;
import com.myproject.questservice.domain.AddFactEffect;
import com.myproject.questservice.domain.Effect;
import com.myproject.questservice.domain.HasFactCondition;
import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.NotCondition;
import com.myproject.questservice.domain.Option;
import com.myproject.questservice.domain.OrCondition;
import com.myproject.questservice.domain.Quest;
import com.myproject.questservice.domain.RemoveFactEffect;
import com.myproject.questservice.domain.Transition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuestDslCompiler {

    public Quest compile(QuestAst ast) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (NodeAst nodeAst : ast.nodes()) {
            nodes.put(nodeAst.id(), new Node(
                    nodeAst.id(),
                    nodeAst.text(),
                    toDomainOptions(nodeAst)
            ));
        }
        return new Quest(ast.id(), ast.title(), ast.nodes().getFirst().id(), nodes);
    }

    private List<Option> toDomainOptions(NodeAst nodeAst) {
        List<OptionAst> optionAstList = nodeAst.options();
        return java.util.stream.IntStream.range(0, optionAstList.size())
                .mapToObj(index -> {
                    OptionAst optionAst = optionAstList.get(index);
                    return new Option(
                            buildOptionId(nodeAst.id(), index),
                            optionAst.text(),
                            new Transition(
                                    optionAst.targetNodeId(),
                                    toConditions(optionAst.conditions()),
                                    toEffects(optionAst.effects())
                            )
                    );
                })
                .toList();
    }

    private List<Condition> toConditions(List<ConditionAst> conditions) {
        return conditions.stream()
                .map(this::toCondition)
                .toList();
    }

    private Condition toCondition(ConditionAst conditionAst) {
        String name = conditionAst.name().toLowerCase();
        return switch (name) {
            case "hasfact" -> new HasFactCondition(conditionAst.arguments().getFirst());
            case "not" -> new NotCondition(new HasFactCondition(conditionAst.arguments().getFirst()));
            case "and" -> new AndCondition(conditionAst.arguments().stream()
                    .map(HasFactCondition::new)
                    .map(Condition.class::cast)
                    .toList());
            case "or" -> new OrCondition(conditionAst.arguments().stream()
                    .map(HasFactCondition::new)
                    .map(Condition.class::cast)
                    .toList());
            default -> throw new IllegalArgumentException("Unknown condition: " + conditionAst.name());
        };
    }

    private List<Effect> toEffects(List<EffectAst> effects) {
        return effects.stream()
                .map(this::toEffect)
                .toList();
    }

    private Effect toEffect(EffectAst effectAst) {
        String name = effectAst.name().toLowerCase();
        return switch (name) {
            case "addfact" -> new AddFactEffect(effectAst.arguments().getFirst());
            case "removefact" -> new RemoveFactEffect(effectAst.arguments().getFirst());
            default -> throw new IllegalArgumentException("Unknown effect: " + effectAst.name());
        };
    }

    private String buildOptionId(String nodeId, int optionIndex) {
        return nodeId + "_" + (optionIndex + 1);
    }
}
