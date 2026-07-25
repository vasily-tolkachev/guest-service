package com.myproject.questservice.textruntime.adapter.out.stub;

import com.myproject.questservice.textruntime.application.port.out.SceneGenerator;
import com.myproject.questservice.textruntime.application.service.compilation.model.Scene;
import com.myproject.questservice.textruntime.application.service.compilation.model.SceneGenerationRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StubSceneGenerator implements SceneGenerator {
    @Override
    public Scene generate(SceneGenerationRequest request) {
        List<String> actions = new ArrayList<>();
        actions.addAll(request.exits().stream().map(exit -> "Перейти: " + exit).toList());
        actions.addAll(request.availableActions().stream().map(action -> "Действие: " + action).toList());
        actions.addAll(request.visibleItems().stream().map(item -> "Взять: " + item).toList());
        actions.addAll(request.visibleNpcs().stream().map(npc -> "Поговорить: " + npc).toList());

        String title = "Сцена " + request.key().locationId();
        StringBuilder description = new StringBuilder(request.locationDescription() == null ? "" : request.locationDescription());
        if (!request.knownFacts().isEmpty()) {
            description.append("\n\nФакты: ").append(String.join(", ", request.knownFacts()));
        }
        if (!request.progressFlags().isEmpty()) {
            description.append("\nФлаги: ").append(String.join(", ", request.progressFlags()));
        }
        return new Scene(request.key(), title, description.toString(), List.copyOf(actions));
    }
}
