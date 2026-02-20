package com.example;

import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.*;

public class LootBingoOverlay extends OverlayPanel
{
    private final ExamplePlugin plugin;
    private final Client client;

    // You can pass data reference, or use getters from plugin

    LootBingoOverlay(ExamplePlugin plugin, Client client, ItemManager itemManager)
    {
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);           // or CENTER, TOP_CENTER, etc.
        setPosition(OverlayPosition.DETACHED);          // allows free dragging
        setPreferredLocation(new Point(120, 120));      // initial position
        setPreferredSize(new Dimension(280, 340));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // You can completely replace this with your own JPanel-like content
        panelComponent.getChildren().clear();

        // Example: simple tab-like content (you can use real JTabbedPane in theory, but it's tricky)
        TitleComponent title = TitleComponent.builder()
                .text("Loot & Bingo")
                .color(Color.CYAN)
                .build();
        panelComponent.getChildren().add(title);

        // Add your tabs / content here (LinesComponent, LayoutableComponent, etc.)
        // or just draw everything manually in render()

        return super.render(graphics);
    }
}