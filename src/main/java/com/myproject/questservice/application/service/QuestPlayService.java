package com.myproject.questservice.application.service;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.OptionView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.adapter.out.dsl.compiler.QuestDslCompiler;
import com.myproject.questservice.adapter.out.dsl.parser.QuestDslParserFacade;
import com.myproject.questservice.adapter.out.dsl.validator.QuestDslValidator;
import com.myproject.questservice.application.port.out.QuestSessionRepositoryPort;
import com.myproject.questservice.application.port.out.QuestRepositoryPort;
import com.myproject.questservice.auth.CurrentUserProvider;
import com.myproject.questservice.domain.GameState;
import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.Quest;
import com.myproject.questservice.domain.QuestDefinition;
import com.myproject.questservice.domain.QuestEngine;
import com.myproject.questservice.domain.QuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestPlayService {

    private final QuestRepositoryPort questRepositoryPort;
    private final QuestSessionRepositoryPort sessionStorePort;
    private final CurrentUserProvider currentUserProvider;
    private final QuestDslParserFacade questDslParserFacade;
    private final QuestDslValidator questDslValidator;
    private final QuestDslCompiler questDslCompiler;

    public StartQuestResponse play(String questId) {
        UUID userId = requireCurrentUserId();
        Quest quest = loadQuest(questId);
        QuestSession session = sessionStorePort.findActive(userId, quest.id()).orElse(null);
        GameView view;
        if (session == null) {
            session = sessionStorePort.create(userId, quest.id(), quest.startNodeId());
            QuestEngine engine = new QuestEngine(quest, session.getGameState());
            view = toView(quest, engine, engine.start());
            sessionStorePort.save(session);
        } else {
            GameState gameState = session.getGameState();
            QuestEngine engine = new QuestEngine(quest, gameState);
            Node node = quest.nodes().get(gameState.getCurrentNodeId());
            if (node == null) {
                throw new QuestChangedException("This quest has changed. Your current progress is incompatible.");
            }
            view = toView(quest, engine, node);
        }
        return new StartQuestResponse(session.getId().toString(), view);
    }

    public StartQuestResponse proceed(String sessionId) {
        QuestSession session = findUserSession(sessionId);
        Quest quest = loadQuest(session.getQuestId());
        GameState gameState = session.getGameState();
        ensureCompatible(gameState, quest);
        QuestEngine engine = new QuestEngine(quest, gameState);
        Node node = quest.nodes().get(gameState.getCurrentNodeId());
        if (node == null) {
            throw new QuestChangedException("This quest has changed. Your current progress is incompatible.");
        }
        return new StartQuestResponse(session.getId().toString(), toView(quest, engine, node));
    }

    public GameView chooseOption(String sessionId, String optionId) {
        if (optionId == null || optionId.isBlank()) {
            throw new BadRequestException("optionId is required");
        }
        QuestSession session = findUserSession(sessionId);
        GameState state = session.getGameState();
        Quest quest = loadQuest(session.getQuestId());
        ensureCompatible(state, quest);
        try {
            QuestEngine engine = new QuestEngine(quest, state);
            Node node = engine.choose(optionId);
            sessionStorePort.save(session);
            return toView(quest, engine, node);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    private void ensureCompatible(GameState state, Quest quest) {
        if (!quest.nodes().containsKey(state.getCurrentNodeId())) {
            throw new QuestChangedException("This quest has changed. Your current progress is incompatible.");
        }
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

    private QuestSession findUserSession(String sessionId) {
        UUID userId = requireCurrentUserId();
        UUID parsedSessionId = parseSessionId(sessionId);
        QuestSession session = sessionStorePort.findById(parsedSessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new NotFoundException("Session not found: " + sessionId);
        }
        return session;
    }

    private Quest loadQuest(String questId) {
        QuestDefinition definition = questRepositoryPort.findByQuestId(questId)
                .orElseThrow(() -> new NotFoundException("Quest not found: " + questId));
        QuestAst ast = questDslParserFacade.parse(definition.dsl());
        questDslValidator.validate(ast);
        return questDslCompiler.compile(ast);
    }

    private UUID requireCurrentUserId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new BadRequestException("Unable to resolve current user"));
    }

    private UUID parseSessionId(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid sessionId format");
        }
    }
}
