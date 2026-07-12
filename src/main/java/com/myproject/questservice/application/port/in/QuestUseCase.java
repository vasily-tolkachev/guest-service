package com.myproject.questservice.application.port.in;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSessionView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;

import java.util.List;

public interface QuestUseCase {

    List<QuestSummaryView> listQuests();

    List<QuestSessionView> listMySessions();

    StartQuestResponse play(String questId);

    StartQuestResponse proceed(String sessionId);

    StartQuestResponse restart(String sessionId);

    GameView chooseOption(String sessionId, String optionId);

    UploadQuestResponse uploadQuest(String dslText);
}
