package com.myproject.questservice.application.service;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.application.port.in.QuestUseCase;
import com.myproject.questservice.application.port.out.QuestRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestApplicationService implements QuestUseCase {

    private final QuestRepositoryPort questRepositoryPort;
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
    public StartQuestResponse play(String questId) {
        return questPlayService.play(questId);
    }

    @Override
    public GameView chooseOption(String sessionId, String optionId) {
        return questPlayService.chooseOption(sessionId, optionId);
    }

    @Override
    public UploadQuestResponse uploadQuest(String dslText) {
        return questImportService.uploadQuest(dslText);
    }

}
