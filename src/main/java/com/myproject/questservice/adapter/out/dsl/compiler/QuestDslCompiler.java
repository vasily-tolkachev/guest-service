package com.myproject.questservice.adapter.out.dsl.compiler;

import com.myproject.questservice.adapter.out.dsl.ast.NodeAst;
import com.myproject.questservice.adapter.out.dsl.ast.OptionAst;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.Option;
import com.myproject.questservice.domain.Quest;
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
                            new Transition(optionAst.targetNodeId())
                    );
                })
                .toList();
    }

    private String buildOptionId(String nodeId, int optionIndex) {
        return nodeId + "_" + (optionIndex + 1);
    }
}
