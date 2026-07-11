package com.myproject.questservice.application.port.in;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestMapView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;

import java.util.List;

public interface QuestUseCase {

    List<QuestSummaryView> listQuests();

    StartQuestResponse start(String questId);

    GameView getSession(String sessionId);

    GameView choose(String sessionId, String optionId);

    GameView back(String sessionId);

    QuestMapView getMap(String sessionId);

    UploadQuestResponse uploadQuest(String dslText);
}
