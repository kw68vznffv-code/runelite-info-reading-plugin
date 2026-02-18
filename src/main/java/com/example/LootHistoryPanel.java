package com.example;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

public class LootHistoryPanel extends PluginPanel
{
    public LootHistoryPanel()
    {
        super(true); // true = scrollable

        JLabel title = new JLabel("Loot Collection");
        title.setFont(new Font("Serif", Font.BOLD, 18));
        add(title);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", createOverviewPanel());
        //tabs.addTab("Loot", createLootPanel());
        //tabs.addTab("Monsters", createMonstersPanel());

        add(tabs);
    }

    private JPanel createOverviewPanel()
    {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel("Total value: 1.3b gp"), BorderLayout.NORTH);
        // ...
        return p;
    }

    // etc.
}