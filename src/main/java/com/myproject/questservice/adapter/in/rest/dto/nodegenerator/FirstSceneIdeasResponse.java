package com.myproject.questservice.adapter.in.rest.dto.nodegenerator;

import java.util.List;

public record FirstSceneIdeasResponse(
        List<FirstSceneIdeaView> ideas
) {
}
