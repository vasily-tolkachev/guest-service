package com.myproject.questservice.application.port.in;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;

import java.util.List;

public interface QuestUseCase {

    List<QuestSummaryView> listQuests();

    StartQuestResponse start(String questId);

    GameView getSession(String sessionId);

    GameView choose(String sessionId, String optionId);
}
