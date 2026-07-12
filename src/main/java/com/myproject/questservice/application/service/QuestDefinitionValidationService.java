package com.myproject.questservice.application.service;

import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.Quest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestDefinitionValidationService {

    public void validate(Quest quest) {
        if (quest.nodes().isEmpty()) {
            throw new BadRequestException("Quest must contain at least one node");
        }
        if (!quest.nodes().containsKey(quest.startNodeId())) {
            throw new BadRequestException("Start node does not exist: " + quest.startNodeId());
        }

        Set<String> nodeIds = quest.nodes().keySet();
        for (Node node : quest.nodes().values()) {
            node.options().forEach(option -> {
                String target = option.transition().targetNodeId();
                if (!nodeIds.contains(target)) {
                    throw new BadRequestException("Missing target node: " + target);
                }
            });
        }
    }
}
