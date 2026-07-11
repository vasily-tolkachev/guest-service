package com.myproject.questservice.adapter.in.rest;

import com.myproject.questservice.adapter.in.rest.dto.ChooseOptionRequest;
import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.application.port.in.QuestUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quests")
@RequiredArgsConstructor
public class QuestController {

    private final QuestUseCase questUseCase;

    @GetMapping
    public List<QuestSummaryView> listQuests() {
        return questUseCase.listQuests();
    }

    @PostMapping("/{questId}/start")
    public StartQuestResponse start(@PathVariable String questId) {
        return questUseCase.start(questId);
    }

    @GetMapping("/sessions/{sessionId}")
    public GameView getSession(@PathVariable String sessionId) {
        return questUseCase.getSession(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/choose")
    public GameView choose(@PathVariable String sessionId, @Valid @RequestBody ChooseOptionRequest request) {
        return questUseCase.choose(sessionId, request.optionId());
    }
}
