package com.myproject.questservice.adapter.out.dsl.compiler;

import com.myproject.questservice.adapter.out.dsl.ast.ConditionAst;
import com.myproject.questservice.adapter.out.dsl.ast.EffectAst;
import com.myproject.questservice.adapter.out.dsl.ast.NodeAst;
import com.myproject.questservice.adapter.out.dsl.ast.OptionAst;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.domain.AndCondition;
import com.myproject.questservice.domain.DecrementVariableEffect;
import com.myproject.questservice.domain.Condition;
import com.myproject.questservice.domain.AddFactEffect;
import com.myproject.questservice.domain.Effect;
import com.myproject.questservice.domain.GiveItemEffect;
import com.myproject.questservice.domain.HasFactCondition;
import com.myproject.questservice.domain.HasItemCondition;
import com.myproject.questservice.domain.IncrementVariableEffect;
import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.NotCondition;
import com.myproject.questservice.domain.Option;
import com.myproject.questservice.domain.OrCondition;
import com.myproject.questservice.domain.Quest;
import com.myproject.questservice.domain.RemoveFactEffect;
import com.myproject.questservice.domain.RemoveItemEffect;
import com.myproject.questservice.domain.SetVariableEffect;
import com.myproject.questservice.domain.Transition;
import com.myproject.questservice.domain.VariableEqualsCondition;
import com.myproject.questservice.domain.VariableGreaterCondition;
import com.myproject.questservice.domain.VariableLessCondition;
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
                    nodeAst.title(),
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
            case "hasflag" -> new HasFactCondition(conditionAst.arguments().getFirst());
            case "hasitem" -> new HasItemCondition(conditionAst.arguments().getFirst());
            case "variableequals" -> new VariableEqualsCondition(
                    conditionAst.arguments().get(0),
                    conditionAst.arguments().get(1)
            );
            case "variablegreater" -> new VariableGreaterCondition(
                    conditionAst.arguments().get(0),
                    Integer.parseInt(conditionAst.arguments().get(1))
            );
            case "variableless" -> new VariableLessCondition(
                    conditionAst.arguments().get(0),
                    Integer.parseInt(conditionAst.arguments().get(1))
            );
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
            case "setflag" -> new AddFactEffect(effectAst.arguments().getFirst());
            case "additem" -> new AddFactEffect("item:" + effectAst.arguments().getFirst());
            case "completequest" -> new AddFactEffect("quest_completed:" + effectAst.arguments().getFirst());
            case "removefact" -> new RemoveFactEffect(effectAst.arguments().getFirst());
            case "giveitem" -> new GiveItemEffect(effectAst.arguments().getFirst());
            case "removeitem" -> new RemoveItemEffect(effectAst.arguments().getFirst());
            case "setvariable" -> new SetVariableEffect(effectAst.arguments().get(0), effectAst.arguments().get(1));
            case "incrementvariable" -> new IncrementVariableEffect(
                    effectAst.arguments().get(0),
                    Integer.parseInt(effectAst.arguments().get(1))
            );
            case "decrementvariable" -> new DecrementVariableEffect(
                    effectAst.arguments().get(0),
                    Integer.parseInt(effectAst.arguments().get(1))
            );
            default -> throw new IllegalArgumentException("Unknown effect: " + effectAst.name());
        };
    }

    private String buildOptionId(String nodeId, int optionIndex) {
        return nodeId + "_" + (optionIndex + 1);
    }
}
