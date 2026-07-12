package com.myproject.questservice.application.service;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.GameStateView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSessionView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.application.port.in.QuestUseCase;
import com.myproject.questservice.application.port.out.QuestSessionRepositoryPort;
import com.myproject.questservice.application.port.out.QuestRepositoryPort;
import com.myproject.questservice.auth.CurrentUserProvider;
import com.myproject.questservice.domain.GameState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestApplicationService implements QuestUseCase {

    private final QuestRepositoryPort questRepositoryPort;
    private final QuestSessionRepositoryPort questSessionRepositoryPort;
    private final CurrentUserProvider currentUserProvider;
    private final QuestPlayService questPlayService;
    private final QuestImportService questImportService;

    @Override
    public List<QuestSummaryView> listQuests() {
        return questRepositoryPort.findAll()
                .stream()
                .map(quest -> new QuestSummaryView(quest.questId(), quest.title()))
                .toList();
    }

    @Override
    public List<QuestSessionView> listMySessions() {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new BadRequestException("Unable to resolve current user"));

        return questSessionRepositoryPort.findAllByUser(userId).stream()
                .map(session -> new QuestSessionView(
                        session.getId().toString(),
                        session.getQuestId(),
                        questRepositoryPort.findByQuestId(session.getQuestId()).map(q -> q.title()).orElse(session.getQuestId()),
                        session.getStatus().name(),
                        toStateView(session.getGameState())
                ))
                .toList();
    }

    @Override
    public StartQuestResponse play(String questId) {
        return questPlayService.play(questId);
    }

    @Override
    public StartQuestResponse proceed(String sessionId) {
        return questPlayService.proceed(sessionId);
    }

    @Override
    public GameView chooseOption(String sessionId, String optionId) {
        return questPlayService.chooseOption(sessionId, optionId);
    }

    @Override
    public UploadQuestResponse uploadQuest(String dslText) {
        return questImportService.uploadQuest(dslText);
    }

    private GameStateView toStateView(GameState state) {
        return new GameStateView(
                state.getCurrentNodeId(),
                state.getFacts().stream().sorted().toList(),
                state.getVariables(),
                state.getInventory().stream().sorted().toList(),
                state.getVisitedNodes().stream().sorted().toList(),
                List.copyOf(state.getNavigationHistory())
        );
    }

}
