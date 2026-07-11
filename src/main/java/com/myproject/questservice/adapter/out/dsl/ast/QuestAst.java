package com.myproject.questservice.adapter.out.dsl.ast;

import java.util.List;

public record QuestAst(
        String id,
        String title,
        List<NodeAst> nodes
) {
}
