package com.myproject.questservice.adapter.in.rest;

import com.myproject.questservice.adapter.in.rest.dto.ChooseOptionRequest;
import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.application.port.in.QuestUseCase;
import com.myproject.questservice.application.service.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadQuestResponse uploadQuest(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        try {
            String dslText = new String(file.getBytes(), StandardCharsets.UTF_8);
            return questUseCase.uploadQuest(dslText);
        } catch (IOException ex) {
            throw new BadRequestException("Unable to read uploaded file");
        }
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
