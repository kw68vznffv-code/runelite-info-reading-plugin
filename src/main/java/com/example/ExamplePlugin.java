package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@PluginDescriptor(
		name = "Loot Tracker + Bingo",
		description = "Sends loot to API + shows side panel with Overview / Bingo / Loot Tracker tabs",
		tags = {"loot", "tracker", "bingo", "json", "api"}
)
public class ExamplePlugin extends Plugin
{
	@Inject private Client client;
	@Inject private Notifier notifier;
	@Inject private ItemManager itemManager;
	@Inject private ClientToolbar clientToolbar;

	private NavigationButton navButton;
	private JPanel mainPanel;

	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private static final DateTimeFormatter UTC_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

	private static final String ENDPOINT = "https://osrs-events.skypro.lt/loot-tracker";

	private final ExecutorService httpExecutor = Executors.newSingleThreadExecutor();

	private final HttpClient httpClient = HttpClient.newBuilder()
			.executor(httpExecutor)
			.build();


	// Create a field
	private LootBingoOverlay lootBingoOverlay;

	@Inject private OverlayManager overlayManager;
	private QuestStyleDialogOverlay dialogOverlay;


	// Inject
	@Inject private ChatboxPanelManager chatboxPanelManager;

	// Somewhere (e.g. hotkey, menu entry, big drop detected)
	private void openBingoSelector()
	{
		chatboxPanelManager.openTextMenuInput("Choose bingo tab:")
				.option("Overview", () -> { /* switch tab */ })
				.option("Bingo",    () -> { /* switch tab */ })
				.option("Close",    () -> {})
				.build();
	}

	private boolean justLoggedIn = false;

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			justLoggedIn = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!justLoggedIn)
		{
			return;
		}

		// Now the chatbox & most UI should be usable
		justLoggedIn = false;

		// Example: show a choice menu right after login
		// openBingoSelector();
	}

	@Override
	protected void startUp()
	{
		mainPanel = buildMainPanel();

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		if (icon == null)
		{
			// Fallback placeholder icon
			icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icon.createGraphics();
			try
			{
				g.setColor(Color.CYAN.darker());
				g.fillRect(0, 0, 24, 24);
				g.setColor(Color.BLACK);
				g.drawRect(0, 0, 23, 23);
			}
			finally
			{
				g.dispose();
			}
            navButton = NavigationButton.builder()
                    .tooltip("Loot & Bingo Tracker")
                    .icon(icon)
                    .priority(50)
                    .panel((PluginPanel) mainPanel)
                    .build();
        } else {
            navButton = NavigationButton.builder()
                    .tooltip("Loot & Bingo Tracker")
                    .icon(icon)
                    .priority(50)
                    .panel((PluginPanel) mainPanel)
                    .build();
        }

        clientToolbar.addNavigation(navButton);
		//
		lootBingoOverlay = new LootBingoOverlay(this, client, itemManager /* + whatever data you need */);
		// overlayManager.add(lootBingoOverlay);

		dialogOverlay = injector.getInstance(QuestStyleDialogOverlay.class); // or new QuestStyleDialogOverlay(client)
		overlayManager.add(dialogOverlay);
	}

	@Override
	protected void shutDown()
	{
		httpExecutor.shutdown();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		// overlayManager.remove(lootBingoOverlay);
		overlayManager.remove(dialogOverlay);
	}

	private JPanel buildMainPanel()
	{
		// Use anonymous subclass since PluginPanel is abstract
		PluginPanel panel = new PluginPanel()
		{
			// No need to override anything for basic usage
		};

		// Header / title
		JLabel title = new JLabel("Loot & Bingo Tracker", SwingConstants.CENTER);
		title.setFont(new Font("Arial", Font.BOLD, 16));
		title.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
		panel.add(title);

		// Tab buttons panel (horizontal)
		JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
		tabBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		JButton overviewBtn = new JButton("Overview");
		JButton bingoBtn    = new JButton("Bingo");
		JButton lootBtn     = new JButton("Loot Tracker");

		Dimension btnSize = new Dimension(100, 28);
		overviewBtn.setPreferredSize(btnSize);
		bingoBtn.setPreferredSize(btnSize);
		lootBtn.setPreferredSize(btnSize);

		tabBar.add(overviewBtn);
		tabBar.add(bingoBtn);
		tabBar.add(lootBtn);

		panel.add(tabBar);

		// Card layout for content switching
		JPanel cardPanel = new JPanel(new CardLayout());
		cardPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// ── Overview tab ───────────────────────────────────────
		JPanel overview = new JPanel(new BorderLayout(0, 8));
		overview.add(new JLabel("<html><center>Plugin status & summary<br>Coming soon...</center></html>", SwingConstants.CENTER), BorderLayout.CENTER);
		cardPanel.add(overview, "overview");

		// ── Bingo tab ──────────────────────────────────────────
		JPanel bingo = new JPanel(new BorderLayout(0, 8));
		bingo.add(new JLabel("<html><center>Bingo challenges / progress<br>Tasks list goes here...</center></html>", SwingConstants.CENTER), BorderLayout.CENTER);
		cardPanel.add(bingo, "bingo");

		// ── Loot Tracker tab ───────────────────────────────────
		JPanel lootTracker = new JPanel(new BorderLayout(0, 8));
		lootTracker.add(new JLabel("<html><center>Recent drops / stats<br>Loot history table coming soon...</center></html>", SwingConstants.CENTER), BorderLayout.CENTER);
		cardPanel.add(lootTracker, "loot");

		panel.add(cardPanel);

		// Wire up tab buttons
		CardLayout cl = (CardLayout) cardPanel.getLayout();

		overviewBtn.addActionListener(e -> cl.show(cardPanel, "overview"));
		bingoBtn.addActionListener(e -> cl.show(cardPanel, "bingo"));
		lootBtn.addActionListener(e -> cl.show(cardPanel, "loot"));

		// Start on Loot Tracker tab by default
		cl.show(cardPanel, "loot");

		return panel;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		Collection<ItemStack> lootItems = event.getItems();

		if (npc == null || lootItems == null || lootItems.isEmpty())
		{
			return;
		}

		String playerName = client.getLocalPlayer() != null ?
				client.getLocalPlayer().getName() : "Unknown";

		String clanName = getCurrentClanName();

		List<LootEntry> lootList = new ArrayList<>();
		long totalValue = 0;

		for (ItemStack stack : lootItems)
		{
			int itemId = stack.getId();
			int qty = stack.getQuantity();

			ItemComposition comp = itemManager.getItemComposition(itemId);
			if (comp == null) continue;

			String itemName = comp.getName();
			long gePrice = comp.getPrice();           // GE guide price
			long itemValue = gePrice * qty;
			totalValue += itemValue;

			LootEntry entry = new LootEntry(
					String.valueOf(itemId),
					itemName,
					gePrice,
					qty,
					npc.getName() != null ? npc.getName() : "NPC #" + npc.getId(),
					String.valueOf(npc.getId()),
					Instant.now().atZone(ZoneOffset.UTC).format(UTC_FORMATTER)
			);
			lootList.add(entry);
		}

		if (lootList.isEmpty()) return;

		LootData data = new LootData(playerName, clanName, lootList);
		String json = gson.toJson(data);

		log.info("Sending loot ({} items, ~{}k gp)", lootList.size(), totalValue / 1000);
		sendLootAsync(json, totalValue);
	}

	private void sendLootAsync(String json, long totalValue)
	{
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(ENDPOINT))
				.header("Content-Type", "application/json")
				.header("User-Agent", "RuneLite-LootTracker/1.0")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();

		CompletableFuture<HttpResponse<String>> future =
				httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

		future.whenComplete((response, throwable) -> {
			if (throwable != null)
			{
				log.error("HTTP request failed", throwable);
				return;
			}

			int status = response.statusCode();
			String body = response.body();

			log.info("POST → {} | body: {}", status, body.substring(0, Math.min(180, body.length())));

			if (status >= 200 && status < 300)
			{
				if (totalValue >= 1_000_000)
				{
					String msg = String.format("Big drop sent! (%,d gp)", totalValue);
					notifier.notify(msg);
				}
			}
			else
			{
				log.warn("Server rejected loot - HTTP {} → {}", status, body);
			}
		});
	}

	private String getCurrentClanName()
	{
		ClanChannel channel = client.getClanChannel();
		if (channel == null || channel.getName() == null || channel.getName().isBlank())
		{
			return "None";
		}
		return channel.getName();
	}

	// ────────────────────────────────────────────────
	//  Inner data classes (sent as JSON)
	// ────────────────────────────────────────────────

	private static class LootEntry
	{
		String itemId;
		String itemName;
		long itemPrice;
		int itemQty;
		String monster;
		String monsterId;
		String datetime;

		LootEntry(String itemId, String itemName, long itemPrice, int itemQty,
				  String monster, String monsterId, String datetime)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.itemPrice = itemPrice;
			this.itemQty = itemQty;
			this.monster = monster;
			this.monsterId = monsterId;
			this.datetime = datetime;
		}
	}

	private static class LootData
	{
		String playerName;
		String clanName;
		List<LootEntry> lootReceived;

		LootData(String playerName, String clanName, List<LootEntry> lootReceived)
		{
			this.playerName = playerName;
			this.clanName = clanName;
			this.lootReceived = lootReceived;
		}
	}
}