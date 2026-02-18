package com.example;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Example overlay mimicking an OSRS-style quest dialog box.
 * - Parchment background
 * - Yellow NPC text
 * - Blue right-click options (fake choices)
 * - "Click here to continue" prompt
 */
public class QuestStyleDialogOverlay extends Overlay
{
    private final Client client;

    // Dialog state - update these from your main plugin
    @Getter @Setter private boolean visible = false;
    @Getter @Setter private String npcName = "Wise Old Man";
    @Getter @Setter private List<String> dialogLines = new ArrayList<>(List.of(
            "Greetings, adventurer!",
            "I see you've come far in your journey.",
            "Would you like to hear about the ancient treasures of Gielinor?"
    ));
    @Getter @Setter private int currentLineIndex = 0;

    // Right-click menu options (appear when right-clicking the overlay)
    private final List<String> options = List.of(
            "Yes, tell me more!",
            "No thanks, I'm busy.",
            "Who are you again?",
            "[Exit dialog]"
    );

    @Inject
    public QuestStyleDialogOverlay(Client client)
    {
        this.client = client;

        // Force-set safe defaults right away
        setPosition(OverlayPosition.DETACHED);
        setPreferredLocation(new Point(200, 150));
        setPreferredSize(new Dimension(400, 180));

        // Optional but helpful
        // setMinimumSize(new Dimension(280, 140));

        setLayer(OverlayLayer.ABOVE_WIDGETS);

        String target = npcName != null ? npcName : "Dialog";

        for (String opt : options)
        {
            getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, opt, target));
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!visible || dialogLines == null || dialogLines.isEmpty() || currentLineIndex >= dialogLines.size())
        {
            return null;
        }

        // ────────────────────────────────────────────────────────────
        // 1. Get current bounds if available
        // ────────────────────────────────────────────────────────────
        Rectangle bounds = getBounds();

        // ────────────────────────────────────────────────────────────
        // 2. Decide final position & size – never allow null
        // ────────────────────────────────────────────────────────────
        int x, y, width, height;

        if (bounds != null && bounds.width > 0 && bounds.height > 0)
        {
            // Use real positioned bounds (user dragged/resized it)
            x = bounds.x;
            y = bounds.y;
            width = bounds.width;
            height = bounds.height;
        }
        else
        {
            // Fallback to preferred – but with null-checks and hard defaults
            Point prefLoc = getPreferredLocation();
            Dimension prefSize = getPreferredSize();

            x = (prefLoc != null) ? prefLoc.x : 200;
            y = (prefLoc != null) ? prefLoc.y : 150;

            width  = (prefSize != null && prefSize.width  > 0) ? prefSize.width  : 400;
            height = (prefSize != null && prefSize.height > 0) ? prefSize.height : 180;

            // Enforce minimum size so text doesn't overflow horribly
            width  = Math.max(width,  280);
            height = Math.max(height, 140);
        }

        // Optional: clamp to screen edges (prevents permanently off-screen)
        x = Math.max(20, Math.min(x, client.getCanvas().getWidth()  - width  - 20));
        y = Math.max(20, Math.min(y, client.getCanvas().getHeight() - height - 20));

        // ────────────────────────────────────────────────────────────
        // 3. Draw using safe x/y/width/height
        // ────────────────────────────────────────────────────────────

        drawParchmentBackground(graphics, x, y, width, height);

        // Title
        graphics.setFont(new Font("Serif", Font.BOLD, 18));
        graphics.setColor(Color.YELLOW);
        String title = (npcName != null ? npcName : "Someone") + " says...";
        FontMetrics fm = graphics.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        graphics.drawString(title, x + (width - titleWidth) / 2, y + 30);

        // Current dialog line
        graphics.setFont(new Font("Serif", Font.PLAIN, 16));
        graphics.setColor(new Color(255, 255, 200));
        String currentText = dialogLines.get(currentLineIndex);
        drawWrappedText(graphics, currentText != null ? currentText : "[No text]",
                x + 20, y + 60, width - 40, 22);

        // Continue prompt
        graphics.setFont(new Font("Serif", Font.ITALIC, 14));
        graphics.setColor(Color.CYAN);
        String continueText = "→ Click here to continue ←";
        int contWidth = graphics.getFontMetrics().stringWidth(continueText);
        graphics.drawString(continueText, x + (width - contWidth) / 2, y + height - 25);

        // Tell RuneLite what size we actually used
        return new Dimension(width, height);
    }

    private void drawParchmentBackground(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(new Color(245, 222, 179)); // wheat
        g.fillRect(x, y, w, h);

        GradientPaint gp = new GradientPaint(x, y, new Color(255, 245, 200),
                x + w, y + h, new Color(220, 190, 140));
        g.setPaint(gp);
        g.fillRect(x + 5, y + 5, w - 10, h - 10);

        g.setColor(new Color(139, 69, 19));
        g.setStroke(new BasicStroke(4));
        g.drawRect(x + 4, y + 4, w - 8, h - 8);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight)
    {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words)
        {
            if (fm.stringWidth(line + word) <= maxWidth)
            {
                line.append(word).append(" ");
            }
            else
            {
                g.drawString(line.toString().trim(), x, y);
                y += lineHeight;
                line = new StringBuilder(word + " ");
            }
        }
        if (line.length() > 0)
        {
            g.drawString(line.toString().trim(), x, y);
        }
    }
}