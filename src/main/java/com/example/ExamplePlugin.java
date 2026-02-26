package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.loottracker.LootRecordType;



import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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


	private final String TRACKER_VERSION = "v0.2";

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

	private NPCKillTracker tracker;
	@Override
	protected void startUp()
	{
		mainPanel = buildMainPanel();
		tracker = new NPCKillTracker(client, TRACKED_NPC_IDS);

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


	private String raid_status_name( int raid_state ){
		switch (raid_state){
			case 0: return "Lobby";
            case 1: return "Started";
			case 2: return "1st floor";
			case 3: return "2st floor";
			case 4: return "Final boss!";
			case 5: return "Left Chambers Of Xeric.";
			default: return String.valueOf(raid_state);
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		Collection<ItemStack> lootItems = event.getItems();

		log.debug("================================");
		log.debug("=== NpcLootReceived event fired (disable sending..) ===");
		log.debug("================================");
		log.debug("  Event class: {}", event.getClass().getName());
		log.debug("  toString():  {}", event.toString());
		log.debug("  NPC:         {}", npc != null ? npc.getName() + " (id=" + npc.getId() + ")" : "null");
		log.debug("  Items count: {}", event.getItems() != null ? event.getItems().size() : "null");
		log.debug("================================");

		return;
	}
	private int EVENTID_CHAMBERS_OF_XERICS = 1;

	//
	// track last records
	//
	NPCKillDetails lastNPCDetails = null;
	EventDetails eventData = null;

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		log.debug("================================");
		log.debug("=== LootReceived event fired ===");
		log.debug("================================");
		log.debug("  Source name: {}", event.getName());           // e.g. "Great Olm", "Tekton", "Chambers of Xeric"
		log.debug("  Loot type:   {}", event.getType());           // NPC, EVENT, PICKPOCKET, etc.
		log.debug("  Meta data:   {}", event.getMetadata() != null ? event.getMetadata().toString() : "null");           // ??
		log.debug("  Items count: {}", event.getItems() != null ? event.getItems().size() : "null");

		if (event.getItems() != null && !event.getItems().isEmpty())
		{
			log.debug("  Items:");
			for (ItemStack stack : event.getItems())
			{
				int id = stack.getId();
				int unnotedId = itemManager.canonicalize(id);

				int qty = stack.getQuantity();
				ItemComposition comp = itemManager.getItemComposition(id);
				String name = comp != null ? comp.getName() : "Unknown item #" + id;

				log.debug("    - {} {}x{} (id={}, gePrice={}, nePrice={})",
						name, ( id == unnotedId ? "" : "(noted)"), qty, id, comp != null ? comp.getPrice() : "N/A", ( itemManager.getItemPrice(id) ) );
			}
		}
		else
		{
			log.debug("  No items in this event");
		}
		log.debug("================================");


		// Optional: filter to only NPC or EVENT types (raids chests are often EVENT)
		if (event.getType() != LootRecordType.EVENT && event.getType() != LootRecordType.NPC)
		{
			return;
		}

		String sourceName = event.getName(); // e.g., "Olmlet chest" or monster name
		Collection<ItemStack> lootItems = event.getItems();

		if (sourceName == null || lootItems == null || lootItems.isEmpty())
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
			int unnotedId = itemManager.canonicalize(itemId);
			int qty = stack.getQuantity();

			ItemComposition comp = itemManager.getItemComposition(itemId);
			if (comp == null) continue;

			String itemName = comp.getName();
			int isTradable = comp.isTradeable() ? 1 : 0;
			long gePrice = comp.getPrice();
			long geNewPrice = itemManager.getItemPrice(itemId);

			long itemValue = gePrice * qty;
				totalValue += itemValue;

			LootEntry entry = new LootEntry(
					String.valueOf(itemId),
					((itemId != unnotedId) ? 1 : 0),
					isTradable,
					itemName,
					geNewPrice,
					qty,
					sourceName,  // use event.getName() instead of npc.getName()
					(event.getMetadata() == null ? "-1" : event.getMetadata().toString()),  // or event.getNpcId() if available, else -1 for non-NPC
					Instant.now().atZone(ZoneOffset.UTC).format(UTC_FORMATTER)
			);
			lootList.add(entry);
		}

		if (lootList.isEmpty()) return;

		/*
		int inRaid = client.getVarbitValue(RAIDS_CLIENT_ISLEADER);
		int raidState = client.getVarbitValue(RAIDS_CLIENT_PROGRESS);
		//
		//	COX event details data
 		//
		//
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[LootReceived] In Raid? <col=ef20ff>"+inRaid+"</col>(Raid state: <col=ef20ff>"+raidState+"</col>)", null);
		if( inRaid == 1 && raidState == 5 ) {
			int raidPartySize = client.getVarbitValue(RAIDS_CLIENT_PARTYSIZE);
			int totalPts = client.getVarbitValue(RAIDS_CLIENT_PARTYSCORE);  // scaled party size
			//
			eventData = new EventDetails(
					client.getWorld(),
					EVENTID_CHAMBERS_OF_XERICS,
					"Chambers of Xeric",
					coxStartTime,
					coxLastSeenTime,
					raidPartySize,
					personalPoints,
					totalPts
			);
		}
		*/



		LootData data = new LootData(client.getWorld(), playerName, clanName, lootList, eventData, lastNPCDetails);
		String json = gson.toJson(data);

		log.info("Sending loot from {} ({} items, ~{} gp)", sourceName, lootList.size(), totalValue);
		log.info("Sent data: {}", json);

		sendLootAsync(json, totalValue);
		// reset last records
		lastNPCDetails = null;
		eventData = null;

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
		int isNoted;
		int isTradable;
		String itemName;
		long itemPrice;
		int itemQty;
		String monster;
		String monsterId;
		String datetime;

		LootEntry(String itemId, int isNoted, int isTradable, String itemName, long itemPrice, int itemQty,
				  String monster, String monsterId, String datetime)
		{
			this.itemId = itemId;
			this.isNoted = isNoted;
			this.isTradable = isTradable;
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
		int world = -1;
		String playerName;
		String clanName;
		List<LootEntry> lootReceived;
		EventDetails eventDetais = null;
		NPCKillDetails npcDetails = null;

		LootData(int world, String playerName, String clanName, List<LootEntry> lootReceived, EventDetails eventDetais, NPCKillDetails npcDetails)
		{
			this.world = world;
			this.playerName = playerName;
			this.clanName = clanName;
			this.lootReceived = lootReceived;
			this.eventDetais = eventDetais;
			this.npcDetails = npcDetails;
		}

		public LootData(int world, String playerName, String clanName, List<LootEntry> lootReceived) {
			this.world = world;
			this.playerName = playerName;
			this.clanName = clanName;
			this.lootReceived = lootReceived;
		}
	}





	private static class EventDetails {
		int eventId = -1;
		int eventWorld = -1;
		String eventName;
		String eventStartTime;
		String eventEndedTime;
		long eventDuration = 0;
		int teamSize = 1;
		int personalPoints = 0;
		int totalPoints = 0;

		EventDetails(int eventWorld, int eventId, String eventName, Instant eventStartTime, Instant eventEndedTime){
			this.eventId = eventId;
			this.eventWorld = eventWorld;
			this.eventName = eventName;
			// duration
			this.eventStartTime = eventStartTime.toString();
			this.eventEndedTime = eventEndedTime.toString();
			this.eventDuration = Duration.between(eventStartTime, eventEndedTime).toSeconds();
		}

		EventDetails(int eventWorld, int eventId, String eventName, Instant eventStartTime, Instant eventEndedTime, int teamSize, int personalPoints, int totalPoints){
			this.eventId = eventId;
			this.eventWorld = eventWorld;
			this.eventName = eventName;
			// duration
			this.eventStartTime = eventStartTime.toString();
			this.eventEndedTime = eventEndedTime.toString();
			this.eventDuration = Duration.between(eventStartTime, eventEndedTime).toSeconds();
			// details
			this.teamSize = teamSize;
			this.personalPoints = personalPoints;
			this.totalPoints = totalPoints;
		}
	}


	private final int[] TRACKED_NPC_IDS = tracker.TRACKED_NPC_IDS;
	@Subscribe
	public void onNpcSpawned(NpcSpawned event) {
		tracker.track(event);
		NPC npc = (NPC) event.getNpc();
		Set<Integer>  trackableIds = IntStream.of(TRACKED_NPC_IDS).boxed().collect(Collectors.toSet());

		// chat msg if trackable.
		if( trackableIds.contains( npc.getId() ) ){
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff005f>Spawned_"+TRACKER_VERSION+"</col>: "
					+npc.getName()
					+" ("+npc.getId()+")"
					+" <col=ff005f>LVL</col> "+npc.getCombatLevel(), null);
		}

		// debug always
		log.debug( "--- SPAWNED_NPC_ID / Name: {}/{} (lvl: {})", npc.getId(), npc.getName(), npc.getCombatLevel() );
	}

	@Subscribe
	public void onActorDeath(ActorDeath event) {
		NPC npc = (NPC) event.getActor();

		NPCKillDetails details = tracker.killed(event);  // Returns details or null
		if (details != null) {
			String json = details.toJson();  // {"npcId":8063,"name":"Vorkath",...}
			// Export/use json
			log.info(details.toString());  // Or overlay/chat
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff005f>Killed</col>: "
					+details.getName()
					+" ("+details.getNpcId()+")"
					+" <col=ff005f>Time</col>: "+details.getFormattedDuration(), null);
			lastNPCDetails = details;
		}

		// debug always
		log.debug( "--- KILLED_NPC_ID / Name: {}/{}", npc.getId(), npc.getName() );
		log.debug("--- DETAILED ---");
			log.debug("Killed NPC: {}, Name: {}, Actor: {}", npc.getId(), npc.getName(), event.getActor());
			log.debug("NPC String: {}", npc.toString());
			log.debug("details: {}",  (details != null ? details.toString() : "") );
		log.debug("--- OEF KILL ---");
	}


	private static final Pattern KC_PATTERN = Pattern.compile(
			"Your\\s+(.+?)\\s+"                    // name
			+ "(?:kill\\s*count|success\\s*count|KC|kills?|deaths?|completions?)\\s*"
			+ "(?::|is\\s*(?:now|at|:))?\\s*"
			+ "(?:<col=\\w+>)?(\\d+)(?:</col>)?",
			Pattern.CASE_INSENSITIVE
	);
	@Subscribe
	public void onChatMessage(ChatMessage event) {
		ChatMessageType type = event.getType();

		log.debug("GAMEMESSAGE_Detected_Chat TYPE_{}: {}", type, event.getMessage());
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.CONSOLE && type != ChatMessageType.SPAM) {
			return;
		}

		String message = event.getMessage();
		Matcher matcher = KC_PATTERN.matcher(message);
		if (matcher.find()) {
			String bossName = matcher.group(1);
			int newKc = Integer.parseInt(matcher.group(2));

			log.debug("GAMEMESSAGE_Detected_KC update [type={}]: {} ({})", type, bossName, newKc);

			tracker.updateKc(bossName, newKc);
		}
	}







	// Varbit IDs (confirmed stable in RuneLite API as of recent versions)
	/*
	private int IsRaidInProgress = 0;
	private int LastRaidState = 0;
	private int personalPoints = 0;

	private Instant coxStartTime = null;
	private Instant coxLastSeenTime = null;
	private Duration coxDuration = null;

	public static final int RAIDS_CLIENT_ISLEADER = 5423;
	public static final int RAIDS_CLIENT_PARTYSIZE = 5424;
	public static final int RAIDS_CLIENT_PROGRESS = 5425;
	public static final int RAIDS_LOBBY_MINCOMBAT = 5426;
	public static final int RAIDS_LOBBY_MINSKILLTOTAL = 5427;
	public static final int RAIDS_CLIENT_PARTYSCORE = 5431;
	public static final int RAIDS_CLIENT_REWARDINDICATOR = 5456;
	public static final int RAIDS_CHALLENGE_MODE = 6385;
	public static final int RAIDS_CLIENT_CHANNELMEMBER = 5428;
	public static final int RAIDS_CLIENT_INDUNGEON = 5432;
	public static final int RAIDS_LOBBY_PARTYSIZE = 5433;
	public static final int RAIDS_CLIENT_NEEDSREWARDBOOK = 5457;
	public static final int RAIDS_SCALING = 9539;
	public static final int RAIDS_CLIENT_PARTYSIZE_SCALED = 9540;
	public static final int RAIDS_CLIENT_HIGHESTCOMBAT = 12285;

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		// Only care about CoX-related varbits
		int inRaid = client.getVarbitValue(RAIDS_CLIENT_ISLEADER);
		int raidState = client.getVarbitValue(RAIDS_CLIENT_PROGRESS);

		int raidDifficulty = client.getVarbitValue(RAIDS_CHALLENGE_MODE);
		int raidLeadership = client.getVarbitValue(RAIDS_CLIENT_ISLEADER);
		int raidPartySize = client.getVarbitValue(RAIDS_CLIENT_PARTYSIZE);  // scaled party size
		int totalPts = client.getVarbitValue(RAIDS_CLIENT_PARTYSCORE);  // scaled party size

		int minSkillReq = client.getVarbitValue(RAIDS_LOBBY_MINSKILLTOTAL);  // scaled party size
		int minCmbReq = client.getVarbitValue(RAIDS_LOBBY_MINCOMBAT);  // scaled party size
		int highestCmbReq = client.getVarbitValue(RAIDS_CLIENT_HIGHESTCOMBAT);  // scaled party size
		int rewardIndicator = client.getVarbitValue(RAIDS_CLIENT_REWARDINDICATOR);  // scaled party size


		if( IsRaidInProgress != inRaid ){
			// reset if starting
			if( IsRaidInProgress == 0 && inRaid == 1 ){
				personalPoints = 0;
				coxStartTime = Instant.now();
			}

			if( IsRaidInProgress == 0 && LastRaidState == 5 && raidState == 0 ){
				personalPoints = 0;
				coxStartTime = Instant.now();
			}


			personalPoints = client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS);
			//
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff005f>"+raid_status_name(raidState)+"</col>"
					+", CM <col=ef20ff>"+raidDifficulty+"</col>"
					+", Diff <col=ef20ff>"+(minSkillReq+"/"+minCmbReq+"-"+highestCmbReq)+"</col>"
					+", PartySize <col=ef20ff>"+raidPartySize+"</col>"
					+", RewId <col=ef20ff>"+rewardIndicator+"</col>"
					+", Personal <col=ef20ff>"+personalPoints+"</col>"
					+", Total <col=ef20ff>"+totalPts+"</col>"
					+", Time <col=ef20ff>"+(coxDuration!=null?coxDuration.toString():0)+"</col>", null);

			//
			IsRaidInProgress = inRaid;
		} else if( IsRaidInProgress == 1 ) {
			personalPoints = client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS);
			coxLastSeenTime = Instant.now();
		}

		if( LastRaidState != raidState ){
			// started raid
			if(inRaid == 1 && LastRaidState == 0 && raidState == 1){
				coxStartTime = Instant.now();
			} else {
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>[Duration]</col> "+(coxDuration!=null?coxDuration.toSeconds():0)+" s.", null);
			}

			if( coxStartTime != null && coxLastSeenTime != null ) {
				coxDuration = Duration.between(coxStartTime, coxLastSeenTime);
			}
			if( inRaid == 1 && LastRaidState == 3 && raidState == 4 ){
				// completed
			}

			if( inRaid == 1 && LastRaidState < 3 && raidState == 5 ) {
				// left raid
			}

			personalPoints = client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS);
			//
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff005f>"+raid_status_name(raidState)+"</col>"
					+", CM <col=ef20ff>"+raidDifficulty+"</col>"
					+", Diff <col=ef20ff>"+(minSkillReq+"/"+minCmbReq+"-"+highestCmbReq)+"</col>"
					+", PartySize <col=ef20ff>"+raidPartySize+"</col>"
					+", RewId <col=ef20ff>"+rewardIndicator+"</col>"
					+", Personal <col=ef20ff>"+personalPoints+"</col>"
					+", Total <col=ef20ff>"+totalPts+"</col>"
					+", Time <col=ef20ff>"+(coxDuration!=null?coxDuration.toSeconds():0)+"s</col>", null);
			//
			LastRaidState = raidState;
		}

	}
	*/







}