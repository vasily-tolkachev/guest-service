package com.myproject.questservice.textruntime.application.service.compilation.support;

import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Player;

public final class GameStateCloner {
    private GameStateCloner() {
    }

    public static GameState cloneState(GameState source) {
        Player player = new Player();
        player.getInventory().addAll(source.getPlayer().getInventory());
        GameState copy = new GameState(source.getCurrentLocation(), player);
        copy.getWorldChanges().addAll(source.getWorldChanges());
        copy.getKnownFacts().addAll(source.getKnownFacts());
        copy.getProgressFlags().addAll(source.getProgressFlags());
        copy.getPerformedActions().addAll(source.getPerformedActions());
        copy.getObjectStates().putAll(source.getObjectStates());
        copy.getCharacterStates().putAll(source.getCharacterStates());
        copy.getDialogueNodeByNpc().putAll(source.getDialogueNodeByNpc());
        copy.getRemovedWorldItems().addAll(source.getRemovedWorldItems());
        return copy;
    }
}
