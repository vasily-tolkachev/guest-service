package com.myproject.questservice.textruntime.application.service.compilation;

import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.textruntime.application.port.out.RuntimeQuestCatalogPort;
import com.myproject.questservice.textruntime.application.port.out.SceneGenerator;
import com.myproject.questservice.textruntime.application.port.out.SceneRepository;
import com.myproject.questservice.textruntime.application.service.compilation.model.SceneGenerationRequest;
import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Player;
import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
import org.springframework.stereotype.Service;

@Service
public class QuestCompiler {
    private final RuntimeQuestCatalogPort questCatalogPort;
    private final SceneGenerator sceneGenerator;
    private final SceneRepository sceneRepository;
    private final QuestExplorer questExplorer;

    public QuestCompiler(
            RuntimeQuestCatalogPort questCatalogPort,
            SceneGenerator sceneGenerator,
            SceneRepository sceneRepository,
            QuestExplorer questExplorer
    ) {
        this.questCatalogPort = questCatalogPort;
        this.sceneGenerator = sceneGenerator;
        this.sceneRepository = sceneRepository;
        this.questExplorer = questExplorer;
    }

    public CompilationResult compile(String questId) {
        RuntimeQuestDefinition definition = questCatalogPort.findById(normalizeQuestId(questId))
                .orElseThrow(() -> new NotFoundException("Runtime quest not found"));
        GameState start = new GameState(definition.startLocationId(), new Player());
        int count = questExplorer.explore(definition.id(), definition.world(), start, this::generateAndSave);
        return new CompilationResult(definition.id(), count);
    }

    private void generateAndSave(SceneGenerationRequest request) {
        sceneRepository.save(sceneGenerator.generate(request));
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    public record CompilationResult(String questId, int scenes) {
    }
}
