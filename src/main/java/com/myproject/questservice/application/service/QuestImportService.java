package com.myproject.questservice.application.service;

import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.adapter.out.dsl.ast.QuestAst;
import com.myproject.questservice.adapter.out.dsl.compiler.QuestDslCompiler;
import com.myproject.questservice.adapter.out.dsl.parser.QuestDslParserFacade;
import com.myproject.questservice.adapter.out.dsl.validator.QuestDslValidator;
import com.myproject.questservice.application.port.out.QuestRepositoryPort;
import com.myproject.questservice.domain.Quest;
import com.myproject.questservice.domain.QuestDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestImportService {

    private final QuestRepositoryPort questRepositoryPort;
    private final QuestDefinitionValidationService questDefinitionValidationService;
    private final QuestDslParserFacade questDslParserFacade;
    private final QuestDslValidator questDslValidator;
    private final QuestDslCompiler questDslCompiler;

    public UploadQuestResponse uploadQuest(String dslText) {
        if (dslText == null || dslText.isBlank()) {
            throw new BadRequestException("DSL text is required");
        }

        QuestAst ast = questDslParserFacade.parse(dslText);
        questDslValidator.validate(ast);
        Quest quest = questDslCompiler.compile(ast);
        questDefinitionValidationService.validate(quest);
        questRepositoryPort.save(new QuestDefinition(quest.id(), quest.title(), dslText));
        return new UploadQuestResponse(quest.id(), quest.title());
    }
}
