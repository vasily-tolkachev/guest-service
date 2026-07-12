package com.myproject.questservice.adapter.in.rest;

import com.myproject.questservice.adapter.in.rest.dto.GameView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSessionView;
import com.myproject.questservice.adapter.in.rest.dto.QuestSummaryView;
import com.myproject.questservice.adapter.in.rest.dto.StartQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.application.port.in.QuestUseCase;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.FileReadException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/sessions")
    public List<QuestSessionView> listMySessions() {
        return questUseCase.listMySessions();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadQuestResponse uploadQuest(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().endsWith(".quest")) {
            throw new BadRequestException("Only .quest files are supported");
        }
        try {
            String dslText = new String(file.getBytes(), StandardCharsets.UTF_8);
            return questUseCase.uploadQuest(dslText);
        } catch (IOException ex) {
            throw new FileReadException("Unable to read uploaded file");
        }
    }

    @PostMapping("/{questId}/play")
    public StartQuestResponse play(@PathVariable String questId) {
        return questUseCase.play(questId);
    }

    @PostMapping("/sessions/{sessionId}/proceed")
    public StartQuestResponse proceed(@PathVariable String sessionId) {
        return questUseCase.proceed(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/options/{optionId}")
    public GameView chooseByPath(@PathVariable String sessionId, @PathVariable String optionId) {
        return questUseCase.chooseOption(sessionId, optionId);
    }
}
