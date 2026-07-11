package com.myproject.questservice.application.service;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.OptionView;
import com.myproject.questservice.adapter.in.rest.dto.QuestMapView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.adapter.out.dsl.compiler.QuestDslCompiler;
import com.myproject.questservice.adapter.out.dsl.parser.QuestDslParserFacade;
import com.myproject.questservice.adapter.out.dsl.validator.QuestDslValidator;
import com.myproject.questservice.application.port.in.QuestUseCase;
import com.myproject.questservice.application.port.out.QuestCatalogPort;
import com.myproject.questservice.application.port.out.SessionStorePort;
import com.myproject.questservice.domain.GameState;
import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.Quest;
import com.myproject.questservice.domain.QuestEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestApplicationService implements QuestUseCase {

    private final QuestCatalogPort questCatalogPort;
    private final SessionStorePort sessionStorePort;
    private final QuestDslParserFacade questDslParserFacade;
    private final QuestDslValidator questDslValidator;
    private final QuestDslCompiler questDslCompiler;

    @Override
    public List<QuestSummaryView> listQuests() {
        return questCatalogPort.findAll()
                .stream()
                .map(quest -> new QuestSummaryView(quest.id(), quest.title()))
                .toList();
    }

    @Override
    public StartQuestResponse start(String questId) {
        Quest quest = questCatalogPort.findById(questId)
                .orElseThrow(() -> new NotFoundException("Quest not found: " + questId));
        String sessionId = sessionStorePort.create(quest.id(), quest.startNodeId());
        GameState gameState = sessionStorePort.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Session not found after create: " + sessionId));
        QuestEngine engine = new QuestEngine(quest, gameState);
        GameView view = toView(quest, engine, engine.start());
        return new StartQuestResponse(sessionId, view);
    }

    @Override
    public GameView getSession(String sessionId) {
        GameState state = sessionStorePort.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        Quest quest = questCatalogPort.findById(state.getQuestId())
                .orElseThrow(() -> new NotFoundException("Quest not found: " + state.getQuestId()));
        QuestEngine engine = new QuestEngine(quest, state);
        Node node = quest.nodes().get(state.getCurrentNodeId());
        if (node == null) {
            throw new NotFoundException("Node not found: " + state.getCurrentNodeId());
        }
        return toView(quest, engine, node);
    }

    @Override
    public GameView choose(String sessionId, String optionId) {
        if (optionId == null || optionId.isBlank()) {
            throw new BadRequestException("optionId is required");
        }
        GameState state = sessionStorePort.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        Quest quest = questCatalogPort.findById(state.getQuestId())
                .orElseThrow(() -> new NotFoundException("Quest not found: " + state.getQuestId()));
        try {
            QuestEngine engine = new QuestEngine(quest, state);
            Node node = engine.choose(optionId);
            return toView(quest, engine, node);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    @Override
    public GameView back(String sessionId) {
        GameState state = sessionStorePort.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        Quest quest = questCatalogPort.findById(state.getQuestId())
                .orElseThrow(() -> new NotFoundException("Quest not found: " + state.getQuestId()));
        try {
            QuestEngine engine = new QuestEngine(quest, state);
            Node node = engine.goBack();
            return toView(quest, engine, node);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    @Override
    public QuestMapView getMap(String sessionId) {
        GameState state = sessionStorePort.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        Quest quest = questCatalogPort.findById(state.getQuestId())
                .orElseThrow(() -> new NotFoundException("Quest not found: " + state.getQuestId()));
        QuestEngine engine = new QuestEngine(quest, state);
        Node currentNode = quest.nodes().get(state.getCurrentNodeId());
        if (currentNode == null) {
            throw new NotFoundException("Node not found: " + state.getCurrentNodeId());
        }
        List<String> available = engine.availableOptions(currentNode).stream()
                .map(option -> option.transition().targetNodeId())
                .distinct()
                .sorted()
                .toList();
        return new QuestMapView(
                state.getCurrentNodeId(),
                state.getVisitedNodes().stream().sorted().toList(),
                available
        );
    }

    @Override
    public UploadQuestResponse uploadQuest(String dslText) {
        if (dslText == null || dslText.isBlank()) {
            throw new BadRequestException("DSL text is required");
        }

        QuestAst ast = questDslParserFacade.parse(dslText);
        questDslValidator.validate(ast);
        Quest quest = questDslCompiler.compile(ast);
        questCatalogPort.save(quest);
        return new UploadQuestResponse(quest.id(), quest.title());
    }

    private GameView toView(Quest quest, QuestEngine engine, Node node) {
        GameState state = engine.gameState();
        return new GameView(
                quest.title(),
                node.id(),
                node.title(),
                node.text(),
                engine.availableOptions(node).stream()
                        .map(option -> new OptionView(option.id(), option.text()))
                        .toList(),
                state.getInventory().stream().sorted().toList(),
                Map.copyOf(state.getVariables()),
                state.getVisitedNodes().stream().sorted().toList(),
                engine.canGoBack(),
                engine.isFinished(node)
        );
    }
}
