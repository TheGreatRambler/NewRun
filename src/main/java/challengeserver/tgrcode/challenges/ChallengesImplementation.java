package challengeserver.tgrcode.challenges;

import com.cryptomorin.xseries.XMaterial;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import me.jumper251.replay.api.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.*;
import org.bukkit.generator.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.event.entity.*;

public class ChallengesImplementation extends JavaPlugin implements Listener {
	public enum GenerationType {
		DEFAULT,
		// FLOATING_VILLAGES,
		// BIOME_BUNDLE,
		// ERASED_CHUNKS,
		AMPLIFIED,
	}

	public enum GameplayTypes {
		// XRAY,
		// SEE_ONE_CHUNK,
		TNT,
		MANHUNT,
		ACCELERATION,
		DROP,
		DROP_QUANITIES,
		PEARL,
		AIR_TELEPORT,
		MOB_SPAWNS,
		RANDOM_WALK,
		ALWAYS_MOVING,
		SPRINT_SPEED,
		LOOT_MOBS,
		BLOCK_SPEED,
		ONLY_PLACE,
		// ALWAYS_SWIM,
		FLY,
		PIG,   // lol
		HORSE, // lol
		JUMP_BOOST,
		ELYTRA,
		LAVA,
		TNT_FALL,
		DOUBLE_JUMP,
		FIRE_RAIN,
		RANDOM_ORE,
		// GRAVITY,
		EVERY_30_SECONDS,
		RISING_LAVA,  // Does not replay
		RISING_WATER, // Does not replay
		MOUNTAIN_AIR,
		MULTIPLIED_CRAFTING,
		EARN_WALK
	}

	public class ChallengeInfo {
		public GenerationType generationType;
		public HashSet<GameplayTypes> gameplayTypes;
		public ArrayList<Player> players;
		public Long currentTick = 0L;
		public World overworld;
		public World end;
		public World nether;
		public boolean hasCompleted;
	}

	public class WatchingReplayInfo {
		public GenerationType generationType;
		public HashSet<GameplayTypes> gameplayTypes;
		public ArrayList<Player> players;
		public String replayName;
		public Player watchingPlayer;
		public World overworld;
		public World end;
		public World nether;
	}

	private class VoidGenerator extends ChunkGenerator {
		@Override
		public ChunkData generateChunkData(World world, Random random,
			int chunkX, int chunkZ, BiomeGrid biome) {
			ChunkData chunk = createChunkData(world);

			for(int x = 0; x < ChallengesImplementation.CHUNK_SIZE; x++) {
				for(int y = 0; y < ChallengesImplementation.CHUNK_HEIGHT; y++) {
					for(int z = 0; z < ChallengesImplementation.CHUNK_SIZE;
						z++) {
						if(world.getSpawnLocation()
								.getBlock()
								.getRelative(BlockFace.DOWN)
								.getLocation()
								.equals(new Location(world,
									chunkX * ChallengesImplementation.CHUNK_SIZE
										+ x,
									y,
									chunkZ * ChallengesImplementation.CHUNK_SIZE
										+ z))) {
							chunk.setBlock(
								x, y, z, XMaterial.BEDROCK.parseMaterial());

						} else {
							chunk.setBlock(x, y, z, airType);
						}
					}
				}
			}

			return chunk;
		}
	}

	// Actually non negotiable
	public static final HashSet<Material> NonErasableMaterialsWhitelist
		= new HashSet<>(Arrays.asList(new Material[] {
			XMaterial.BEDROCK.parseMaterial(),
			XMaterial.END_PORTAL.parseMaterial(),
			XMaterial.END_PORTAL_FRAME.parseMaterial(),
			XMaterial.END_CRYSTAL.parseMaterial(),
		}));

	// Structures stay maybe
	public static final HashSet<Material>
		NonErasableMaterialsStructuresWhitelist
		= new HashSet<>(Arrays.asList(new Material[] {
			XMaterial.STONE_BRICKS.parseMaterial(),
			XMaterial.CRACKED_STONE_BRICKS.parseMaterial(),
			XMaterial.MOSSY_STONE_BRICKS.parseMaterial(),
			XMaterial.INFESTED_STONE_BRICKS.parseMaterial(),
			XMaterial.SPAWNER.parseMaterial(),
			XMaterial.IRON_BARS.parseMaterial(),
			XMaterial.END_PORTAL_FRAME.parseMaterial(),
			XMaterial.END_PORTAL.parseMaterial(),
			XMaterial.TORCH.parseMaterial(),
			XMaterial.OAK_FENCE.parseMaterial(),
			XMaterial.CHEST.parseMaterial(),
			XMaterial.STONE_BRICK_SLAB.parseMaterial(),
			XMaterial.COBBLESTONE.parseMaterial(),
			XMaterial.STONE_BRICK_STAIRS.parseMaterial(),
			XMaterial.OAK_PLANKS.parseMaterial(),
			XMaterial.LADDER.parseMaterial(),
			XMaterial.SMOOTH_STONE_SLAB.parseMaterial(),
			XMaterial.STONE_BUTTON.parseMaterial(),
			XMaterial.IRON_DOOR.parseMaterial(),
			XMaterial.OAK_DOOR.parseMaterial(),
			XMaterial.COBBLESTONE_STAIRS.parseMaterial(),
			XMaterial.BOOKSHELF.parseMaterial(),
			XMaterial.COBWEB.parseMaterial(),
			XMaterial.MOSSY_COBBLESTONE.parseMaterial(),
			XMaterial.END_CRYSTAL.parseMaterial(),
			XMaterial.OBSIDIAN.parseMaterial(),
			XMaterial.BEDROCK.parseMaterial(),
			XMaterial.NETHER_BRICKS.parseMaterial(),
			XMaterial.NETHER_BRICK_FENCE.parseMaterial(),
			XMaterial.NETHER_BRICK_STAIRS.parseMaterial(),
			XMaterial.NETHER_WART.parseMaterial(),
			XMaterial.CHEST.parseMaterial(),
		}));

	public static final HashSet<Material> FloatingVillageBlacklist
		= new HashSet<>(Arrays.asList(new Material[] {
			XMaterial.STONE.parseMaterial(),
			XMaterial.GRAVEL.parseMaterial(),
			XMaterial.BEDROCK.parseMaterial(),
			XMaterial.GRANITE.parseMaterial(),
			XMaterial.DIORITE.parseMaterial(),
			XMaterial.ANDESITE.parseMaterial(),
			XMaterial.SANDSTONE.parseMaterial(),
			XMaterial.END_STONE.parseMaterial(),
			XMaterial.NETHERRACK.parseMaterial(),
			XMaterial.GLOWSTONE.parseMaterial(),
			XMaterial.SAND.parseMaterial(),
			XMaterial.RED_SAND.parseMaterial(),
			XMaterial.SNOW.parseMaterial(),
			XMaterial.COAL_ORE.parseMaterial(),
			XMaterial.IRON_ORE.parseMaterial(),
			XMaterial.LAPIS_ORE.parseMaterial(),
			XMaterial.GOLD_ORE.parseMaterial(),
			XMaterial.REDSTONE.parseMaterial(),
			XMaterial.DIAMOND_ORE.parseMaterial(),
			XMaterial.EMERALD_ORE.parseMaterial(),
			XMaterial.WATER.parseMaterial(),
			XMaterial.LAVA.parseMaterial(),
			XMaterial.NETHER_QUARTZ_ORE.parseMaterial(),
			XMaterial.NETHER_GOLD_ORE.parseMaterial(),
			XMaterial.ANCIENT_DEBRIS.parseMaterial(),
			XMaterial.BASALT.parseMaterial(),
			XMaterial.BLACKSTONE.parseMaterial(),
		}));

	public static int CHUNK_SIZE   = 16;
	public static int CHUNK_HEIGHT = 256;
	public static int WORLD_HEIGHT = 64;

	public static DateFormat replayDateFormat
		= new SimpleDateFormat("MM-dd-yyyy_HH-mm-ss");

	// https://mineyourmind.net/forum/threads/how-many-chunks-does-your-character-keep-loaded-when-your-online.23599/
	// At this point, I'm not sure what the server is doing, going to be honest
	private static int CHUNKS_PRELOADED_BY_SERVER = 2600;

	private int worldGenChunksSoFar = -1;

	private int teleportReplayPacketsId = 0;

	// For compatibility
	private Material airType;
	private Material lavaType;
	private Material endPortalType;
	private Material netherPortalType;

	// By hash
	private Map<Integer, ChallengeInfo> allInfos;
	// By world name
	private Map<String, ChallengeInfo> nameToType;
	// By player
	private Map<Player, ChallengeInfo> playerToType;

	private Map<Player, Location> locationStartedFrom;

	private Map<ChallengeInfo, Boolean> isPaused;

	private Map<Player, WatchingReplayInfo> playerToReplayInfo;

	private Map<String, WatchingReplayInfo> nameToReplayInfo;

	private PortalTravelAgent portalTravelAgent;
	private GameplayChanges gameplayChanges;

	private LeaderboardClient leaderboardClient;

	@Override
	public void onEnable() {
		nameToType          = new HashMap<>();
		playerToType        = new HashMap<>();
		allInfos            = new HashMap<>();
		isPaused            = new HashMap<>();
		portalTravelAgent   = new PortalTravelAgent();
		locationStartedFrom = new HashMap<>();
		playerToReplayInfo  = new HashMap<>();
		nameToReplayInfo    = new HashMap<>();

		airType          = XMaterial.AIR.parseMaterial();
		lavaType         = XMaterial.LAVA.parseMaterial();
		endPortalType    = XMaterial.END_PORTAL.parseMaterial();
		netherPortalType = XMaterial.NETHER_PORTAL.parseMaterial();

		gameplayChanges
			= new GameplayChanges(this, nameToType, playerToType, isPaused);

		getCommand("startchallenge").setTabCompleter(this);
		getServer().getPluginManager().registerEvents(this, this);

		Bukkit.getServer().getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				gameplayChanges.onTick();
			}
		}, 20L, 1);

		try {
			leaderboardClient = new LeaderboardClient();
			if(!leaderboardClient.isOpen()) {
				getLogger().log(Level.WARNING,
					"Connection to leaderboard was not established");
				leaderboardClient = null;
			} else {
				getLogger().log(
					Level.INFO, "Connection to leaderboard was established");
			}
		} catch(Exception e) {
			getLogger().log(
				Level.WARNING, "Exception while connecting to leaderboard");
			leaderboardClient = null;
		}
	}

	@Override
	public void onDisable() {
		if(leaderboardClient != null) {
			try {
				leaderboardClient.closeBlocking();
			} catch(InterruptedException e) {
				getLogger().log(Level.WARNING,
					"Exception while disconnecting from leaderboard");
			}
		}
	}

	private ChallengeInfo getChallengeInfo(String worldName) {
		return nameToType.getOrDefault(worldName, null);
	}

	private ChallengeInfo getChallengeInfo(Player player) {
		return playerToType.getOrDefault(player, null);
	}

	private WatchingReplayInfo getReplayInfo(Player player) {
		return playerToReplayInfo.getOrDefault(player, null);
	}

	private WatchingReplayInfo getReplayInfo(String worldName) {
		return nameToReplayInfo.getOrDefault(worldName, null);
	}

	public Integer startChallenge(String baseName, GenerationType genType,
		HashSet<GameplayTypes> gameplayTypes, long seed,
		ArrayList<Player> players, boolean pregenChunks) {
		if(Bukkit.getWorld(baseName) == null
			&& Bukkit.getWorld(baseName + "_the_end") == null
			&& Bukkit.getWorld(baseName + "_nether") == null) {
			String name;

			worldGenChunksSoFar = 0;

			ChallengeInfo challengeInfo  = new ChallengeInfo();
			challengeInfo.generationType = genType;
			challengeInfo.gameplayTypes  = gameplayTypes;
			challengeInfo.players        = players;

			allInfos.put(challengeInfo.hashCode(), challengeInfo);
			isPaused.put(challengeInfo, false);

			for(Player player : players) {
				playerToType.put(player, challengeInfo);
			}

			name = baseName;
			deleteWorldIfPossible(Bukkit.getWorld(name));
			WorldCreator worldCreator = new WorldCreator(name);
			worldCreator.seed(seed);

			// if(genType == GenerationType.FLOATING_VILLAGES) {
			//	worldCreator.generator(
			//		"WorldGenHeight:" + Integer.toString(WORLD_HEIGHT));
			//}

			// if(genType == GenerationType.BIOME_BUNDLE) {
			//	worldCreator.generator("OpenTerrainGenerator");
			//}

			if(genType == GenerationType.AMPLIFIED) {
				worldCreator.type(WorldType.AMPLIFIED);
			}

			if(challengeInfo.gameplayTypes.contains(
				   GameplayTypes.EVERY_30_SECONDS)) {
				worldCreator.generator(new VoidGenerator());
			}

			nameToType.put(name, challengeInfo);
			World overworld = Bukkit.getServer().createWorld(worldCreator);

			// Neither the end nor nether are really affected by these changes

			name = baseName + "_the_end";
			deleteWorldIfPossible(Bukkit.getWorld(name));
			worldCreator = new WorldCreator(name);
			worldCreator.environment(World.Environment.THE_END);
			worldCreator.seed(seed);

			// if(genType == GenerationType.FLOATING_VILLAGES) {
			//	worldCreator.generator(
			//		"WorldGenHeight:" + Integer.toString(WORLD_HEIGHT));
			//}

			if(challengeInfo.gameplayTypes.contains(
				   GameplayTypes.EVERY_30_SECONDS)) {
				worldCreator.generator(new VoidGenerator());
			}

			nameToType.put(name, challengeInfo);
			World end = Bukkit.getServer().createWorld(worldCreator);

			name = baseName + "_nether";
			deleteWorldIfPossible(Bukkit.getWorld(name));
			worldCreator = new WorldCreator(name);
			worldCreator.environment(World.Environment.NETHER);
			worldCreator.seed(seed);

			// if(genType == GenerationType.FLOATING_VILLAGES) {
			//	worldCreator.generator(
			//		"WorldGenHeight:" + Integer.toString(WORLD_HEIGHT));
			//}

			if(challengeInfo.gameplayTypes.contains(
				   GameplayTypes.EVERY_30_SECONDS)) {
				worldCreator.generator(new VoidGenerator());
			}

			nameToType.put(name, challengeInfo);
			World nether = Bukkit.getServer().createWorld(worldCreator);

			challengeInfo.overworld = overworld;
			challengeInfo.nether    = nether;
			challengeInfo.end       = end;

			if(pregenChunks) {
				// This should only be run if no players are present
				for(int x = -19; x < 19; x++) {
					for(int z = -19; z < 19; z++) {
						overworld.getChunkAt(x, z);
						end.getChunkAt(x, z);
						nether.getChunkAt(x, z);
					}
				}
			} else {
				/*
				if(genType == GenerationType.ERASED_CHUNKS) {
					// Generate entire nether
					nether.getChunkAt(0, 0);
					end.getChunkAt(0, 0);
				}
				*/
			}

			worldGenChunksSoFar = -1;

			// unloadWorld(overworld);
			// unloadWorld(end);
			// unloadWorld(nether);

			Location spawnLoc = overworld.getSpawnLocation();
			for(Player player : players) {
				locationStartedFrom.put(player, player.getLocation());
				player.setGameMode(GameMode.SURVIVAL);
				player.getInventory().clear();
				player.setExp(0);
				player.setLevel(0);
				player.setHealth(20);
				player.setFoodLevel(20);
				player.teleport(spawnLoc);
				gameplayChanges.onPlayerStartEvent(player);

				if(leaderboardClient != null) {
					leaderboardClient.markPlayerPlaying(
						player.getUniqueId().toString());
				}
			}

			// Start recording a replay for every involved player
			Player[] playerArr = new Player[players.size()];
			playerArr          = players.toArray(playerArr);
			ReplayAPI.getInstance().recordReplay(
				"challenge-" + challengeInfo.hashCode(), players.get(0),
				playerArr);

			return challengeInfo.hashCode();
		} else {
			return -1;
		}
	}

	String stopChallenge(ChallengeInfo info) {
		// https://stackoverflow.com/a/24997793/9329945
		nameToType.values().removeIf(info::equals);
		playerToType.values().removeIf(info::equals);
		allInfos.remove(info.hashCode());
		isPaused.remove(info);

		// Signal to gameplay changes if anything needs to be done
		gameplayChanges.onChallengeEnd(info);

		for(Player player : info.players) {
			player.setGameMode(GameMode.CREATIVE);
			player.teleport(locationStartedFrom.get(player));
			locationStartedFrom.remove(player);

			if(leaderboardClient != null) {
				leaderboardClient.markPlayerDone(
					player.getUniqueId().toString());
			}
		}

		String replayName
			= "challenge-" + UUID.randomUUID().toString().replace("-", "");
		String oldName = "challenge-" + Integer.toString(info.hashCode());
		String replayPath
			= "plugins/AdvancedReplay/replays/" + oldName + ".replay";
		ReplayAPI.getInstance().stopReplay(oldName, true);

		// Create replay with info required to create world
		DataOutputStream dataOutputStream = null;
		String outputReplayName           = replayName + ".newrun";

		new File("replays").mkdirs();

		try {
			dataOutputStream = new DataOutputStream(
				new FileOutputStream(new File("replays", outputReplayName)));
		} catch(FileNotFoundException e) {
			getLogger().log(Level.WARNING,
				"Writing replay encountered FileNotFoundException");
		}

		long overworldSeed = info.overworld.getSeed();
		long netherSeed    = info.nether.getSeed();
		long endSeed       = info.end.getSeed();

		String overworldName = info.overworld.getName();
		String netherName    = info.nether.getName();
		String endName       = info.end.getName();

		deleteWorldIfPossible(info.overworld);
		deleteWorldIfPossible(info.nether);
		deleteWorldIfPossible(info.end);

		try {
			// Replay hash
			dataOutputStream.writeUTF(replayName);
			// Date uploaded
			dataOutputStream.writeLong(System.currentTimeMillis());
			// Replay version
			dataOutputStream.writeInt(1);
			// Minecraft version
			dataOutputStream.writeUTF(getMinecraftVersion());
			// Whether the player has completed the challenge
			dataOutputStream.writeBoolean(info.hasCompleted);
			// Tick length of the challenge
			dataOutputStream.writeLong(info.currentTick);
			// names
			dataOutputStream.writeUTF(overworldName);
			dataOutputStream.writeUTF(netherName);
			dataOutputStream.writeUTF(endName);
			// Seeds
			dataOutputStream.writeLong(overworldSeed);
			dataOutputStream.writeLong(netherSeed);
			dataOutputStream.writeLong(endSeed);
			// Starting dimension, overworld for now
			dataOutputStream.writeInt(0);
			// Generation type, usually doesnt result in difference in replay
			dataOutputStream.writeUTF(info.generationType.name());
			// Gameplay types
			dataOutputStream.writeInt(info.gameplayTypes.size());
			for(GameplayTypes type : info.gameplayTypes) {
				dataOutputStream.writeUTF(type.name());
			}
			// Player UUIDs
			dataOutputStream.writeInt(info.players.size());
			for(Player player : info.players) {
				dataOutputStream.writeUTF(player.getUniqueId().toString());
			}

			getLogger().log(Level.INFO, "Replay metadata written");

			FileInputStream replayStream = new FileInputStream(replayPath);
			IOUtils.copy(replayStream, dataOutputStream);
			replayStream.close();

			getLogger().log(Level.INFO, "Replay written");

			// Remove original replay
			new File(replayPath).delete();

			dataOutputStream.close();

			// Send to leaderboard server
			if(leaderboardClient != null) {
				getLogger().log(Level.INFO, "Send replay to server");
				leaderboardClient.sendReplay(outputReplayName);
			}
		} catch(IOException e) {
			getLogger().log(Level.WARNING,
				"Error writing new replay file, " + e.getMessage());
			outputReplayName = null;
		}

		for(Player player : info.players) {
			player.sendMessage("Replay name is " + outputReplayName);
		}

		return outputReplayName;
	}

	private void playReplay(
		String replayName, boolean onlyPrintInfo, Player player) {
		// Create replay with info required to create world
		DataInputStream dataInputStream = null;
		try {
			dataInputStream = new DataInputStream(new FileInputStream(
				new File("replays", replayName + ".newrun")));
		} catch(FileNotFoundException e) {
			getLogger().log(Level.WARNING,
				"Writing replay encountered FileNotFoundException");
		}

		try {
			String name             = dataInputStream.readUTF();
			long uploaded           = dataInputStream.readLong();
			int replayVersion       = dataInputStream.readInt();
			String minecraftVersion = dataInputStream.readUTF();
			boolean hasCompleted    = dataInputStream.readBoolean();
			long currentTick        = dataInputStream.readLong();
			String overworldName    = dataInputStream.readUTF();
			String netherName       = dataInputStream.readUTF();
			String endName          = dataInputStream.readUTF();
			// Seeds
			long overworldSeed = dataInputStream.readLong();
			long netherSeed    = dataInputStream.readLong();
			long endSeed       = dataInputStream.readLong();
			// Starting dimension, overworld for now
			int startingDimension = dataInputStream.readInt();
			// Generation type, usually doesnt result in difference in replay
			GenerationType generationType
				= GenerationType.valueOf(dataInputStream.readUTF());

			player.sendMessage(ChatColor.DARK_GRAY + "name: " + name);
			player.sendMessage(ChatColor.DARK_GRAY + "uploaded: "
							   + replayDateFormat.format(new Date(uploaded)));
			player.sendMessage(ChatColor.DARK_GRAY + "replayVersion: "
							   + Integer.toString(replayVersion));
			player.sendMessage(
				ChatColor.DARK_GRAY + "minecraftVersion: " + minecraftVersion);
			player.sendMessage(ChatColor.DARK_GRAY + "hasCompleted: "
							   + (hasCompleted ? "TRUE" : "FALSE"));
			player.sendMessage(ChatColor.DARK_GRAY
							   + "currentTick: " + Long.toString(currentTick));
			player.sendMessage(
				ChatColor.DARK_GRAY + "overworldName: " + overworldName);
			player.sendMessage(
				ChatColor.DARK_GRAY + "netherName: " + netherName);
			player.sendMessage(ChatColor.DARK_GRAY + "endName: " + endName);
			player.sendMessage(
				ChatColor.DARK_GRAY + "overworldSeed: " + overworldSeed);
			player.sendMessage(
				ChatColor.DARK_GRAY + "netherSeed: " + netherSeed);
			player.sendMessage(ChatColor.DARK_GRAY + "endSeed: " + endSeed);
			player.sendMessage(ChatColor.DARK_GRAY
							   + "startingDimension: " + startingDimension);
			player.sendMessage(ChatColor.DARK_GRAY
							   + "generationType: " + generationType.name());

			// Gameplay types
			HashSet<GameplayTypes> gameplayTypes = new HashSet<>();
			int gameplayTypesSize                = dataInputStream.readInt();
			player.sendMessage(ChatColor.DARK_GRAY + "gameplayTypes: [");
			for(int i = 0; i < gameplayTypesSize; i++) {
				GameplayTypes type
					= GameplayTypes.valueOf(dataInputStream.readUTF());
				gameplayTypes.add(type);
				player.sendMessage(ChatColor.DARK_GRAY + "    " + type.name());
			}
			player.sendMessage(ChatColor.DARK_GRAY + "]");
			// Player UUIDs
			ArrayList<Player> players = new ArrayList<>();
			int playersSize           = dataInputStream.readInt();
			player.sendMessage(ChatColor.DARK_GRAY + "players: [");
			for(int i = 0; i < playersSize; i++) {
				Player p = Bukkit.getPlayer(
					UUID.fromString(dataInputStream.readUTF()));
				players.add(p);
				player.sendMessage(ChatColor.DARK_GRAY + "    " + p.getName());
			}
			player.sendMessage(ChatColor.DARK_GRAY + "]");

			if(!onlyPrintInfo) {
				String replayPath = "plugins/AdvancedReplay/replays/"
									+ replayName + ".replay";
				FileOutputStream replayStream
					= new FileOutputStream(replayPath);
				IOUtils.copy(dataInputStream, replayStream);
				replayStream.close();

				WorldCreator worldCreator = new WorldCreator(overworldName);
				worldCreator.seed(overworldSeed);
				if(generationType == GenerationType.AMPLIFIED) {
					worldCreator.type(WorldType.AMPLIFIED);
				}
				World overworld = Bukkit.getServer().createWorld(worldCreator);
				// overworld.setDifficulty(Difficulty.PEACEFUL);
				// overworld.setAnimalSpawnLimit(0);
				// overworld.setAmbientSpawnLimit(0);
				// overworld.setMonsterSpawnLimit(0);
				// overworld.setWaterAnimalSpawnLimit(0);
				// overworld.setSpawnFlags(false, false);

				worldCreator = new WorldCreator(endName);
				worldCreator.environment(World.Environment.THE_END);
				worldCreator.seed(endSeed);
				World end = Bukkit.getServer().createWorld(worldCreator);
				// end.setDifficulty(Difficulty.PEACEFUL);
				// end.setAnimalSpawnLimit(0);
				// end.setAmbientSpawnLimit(0);
				// end.setMonsterSpawnLimit(0);
				// end.setWaterAnimalSpawnLimit(0);
				// end.setSpawnFlags(false, false);

				worldCreator = new WorldCreator(netherName);
				worldCreator.environment(World.Environment.NETHER);
				worldCreator.seed(netherSeed);
				World nether = Bukkit.getServer().createWorld(worldCreator);
				// nether.setDifficulty(Difficulty.PEACEFUL);
				// nether.setAnimalSpawnLimit(0);
				// nether.setAmbientSpawnLimit(0);
				// nether.setMonsterSpawnLimit(0);
				// nether.setWaterAnimalSpawnLimit(0);
				// nether.setSpawnFlags(false, false);

				locationStartedFrom.put(player, player.getLocation());

				if(startingDimension == 0) {
					Location spawnLoc = overworld.getSpawnLocation();
					player.setGameMode(GameMode.CREATIVE);
					player.teleport(spawnLoc);
				} else if(startingDimension == 1) {
					Location spawnLoc = nether.getSpawnLocation();
					player.setGameMode(GameMode.CREATIVE);
					player.teleport(spawnLoc);
				} else if(startingDimension == 2) {
					Location spawnLoc = end.getSpawnLocation();
					player.setGameMode(GameMode.CREATIVE);
					player.teleport(spawnLoc);
				}

				WatchingReplayInfo replayInfo = new WatchingReplayInfo();
				replayInfo.generationType     = generationType;
				replayInfo.gameplayTypes      = gameplayTypes;
				replayInfo.players            = players;
				replayInfo.watchingPlayer     = player;
				replayInfo.overworld          = overworld;
				replayInfo.nether             = nether;
				replayInfo.end                = end;
				replayInfo.replayName         = replayName;
				playerToReplayInfo.put(player, replayInfo);
				nameToReplayInfo.put(overworldName, replayInfo);
				nameToReplayInfo.put(netherName, replayInfo);
				nameToReplayInfo.put(endName, replayInfo);

				// Play this replay
				ReplayAPI.getInstance().playReplay(replayName, player);
			}
		} catch(IOException e) {
			getLogger().log(Level.WARNING, "Error reading replay file");
		}
	}

	private void leaveReplay(WatchingReplayInfo replayInfo) {
		playerToReplayInfo.values().removeIf(replayInfo::equals);
		nameToReplayInfo.values().removeIf(replayInfo::equals);

		Player player      = replayInfo.watchingPlayer;
		boolean leftReplay = ReplayAPI.getInstance().leaveReplay(player);

		if(leftReplay) {
			Listener finishEventListener = new Listener() {};
			Bukkit.getPluginManager().registerEvent(
				ReplaySessionFinishEvent.class, finishEventListener,
				EventPriority.NORMAL, (listener, event) -> {
					HandlerList.unregisterAll(finishEventListener);

					player.teleport(locationStartedFrom.get(player));
					locationStartedFrom.remove(player);

					String replayPath = "plugins/AdvancedReplay/replays/"
										+ replayInfo.replayName + ".replay";
					new File(replayPath).delete();

					deleteWorldIfPossible(replayInfo.overworld);
					deleteWorldIfPossible(replayInfo.nether);
					deleteWorldIfPossible(replayInfo.end);
				}, this);
		} else {
			// Player was not in a replay when this was called, ignore the event
			player.teleport(locationStartedFrom.get(player));
			locationStartedFrom.remove(player);

			String replayPath = "plugins/AdvancedReplay/replays/"
								+ replayInfo.replayName + ".replay";
			new File(replayPath).delete();

			deleteWorldIfPossible(replayInfo.overworld);
			deleteWorldIfPossible(replayInfo.nether);
			deleteWorldIfPossible(replayInfo.end);
		}
	}

	public void pauseChallenge(ChallengeInfo info) {
		isPaused.put(info, true);
	}

	public void unpauseChallenge(ChallengeInfo info) {
		isPaused.put(info, false);
	}

	private void unloadWorld(World world) {
		if(world != null) {
			if(Bukkit.getServer().unloadWorld(world, true)) {
				getLogger().info("Was able to unload world " + world.getName());
			} else {
				getLogger().info(
					"Was not able to unload world " + world.getName());
			}
		}
	}

	private void deleteWorldIfPossible(World world) {
		unloadWorld(world);

		if(world != null) {
			try {
				FileUtils.deleteDirectory(world.getWorldFolder());
				getLogger().info("Was able to delete world " + world.getName());
			} catch(IOException e) {
				getLogger().info("Was not able to delete world "
								 + world.getName() + ", IOException");
			}
		}
	}

	private static String getMinecraftVersion() {
		return Bukkit.getBukkitVersion().split("-")[0];
	}

	@Override
	public boolean onCommand(
		CommandSender sender, Command command, String label, String[] args) {
		if(sender instanceof Player) {
			Player player = (Player)sender;
			if(command.getName().equalsIgnoreCase("startchallenge")) {
				if(args.length == 0) {
					return false;
				} else {
					String[] ChallengeInfos = args[0].split(":", -1);
					GenerationType type;
					try {
						if(args.length == 0) {
							return false;
						} else {
							type = GenerationType.valueOf(ChallengeInfos[0]);
						}
					} catch(IllegalArgumentException e) {
						return false;
					}

					HashSet<GameplayTypes> gameplayTypes = new HashSet<>();
					for(int i = 1; i < ChallengeInfos.length; i++) {
						try {
							gameplayTypes.add(
								GameplayTypes.valueOf(ChallengeInfos[i]));
						} catch(IllegalArgumentException e) {
							return false;
						}
					}

					long seed;
					// get seed
					if(args.length < 2) {
						seed = (new Random()).nextLong();
					} else {
						if(args[1].equals("r")) {
							seed = (new Random()).nextLong();
						} else {
							if(args[1].length() >= 20) {
								seed = args[1].hashCode();
							}
							try {
								seed = Integer.parseInt(args[1]);
							} catch(NumberFormatException e) {
								// Number is ill formed
								seed = args[1].hashCode();
							}
						}
					}

					ArrayList<Player> playersInChallenge = new ArrayList<>();
					if(args.length < 3) {
						playersInChallenge.add(player);
					} else {
						for(int i = 2; i < args.length; i++) {
							Player playerToAdd = Bukkit.getPlayer(args[i]);
							if(playerToAdd != null) {
								playersInChallenge.add(playerToAdd);
							}
						}
					}

					int baseNum = 0;
					while(Bukkit.getWorld(Integer.toString(baseNum)) != null) {
						baseNum++;
					}

					for(Player playerToTeleport : playersInChallenge) {
						playerToTeleport.sendMessage(
							ChatColor.WHITE
							+ "Your world is generating and you will be teleported shortly");
					}

					int challengeId = startChallenge(Integer.toString(baseNum),
						type, gameplayTypes, seed, playersInChallenge, false);

					for(Player p : playersInChallenge) {
						p.sendMessage(
							"Challenge ID is " + Integer.toString(challengeId));
					}
				}
			}

			if(command.getName().equalsIgnoreCase("stopchallenge")) {
				if(args.length == 0) {
					// Choose current challenge of sender
					ChallengeInfo thisChallenge = getChallengeInfo(player);
					if(thisChallenge != null) {
						thisChallenge.hasCompleted = false;
						stopChallenge(thisChallenge);
					} else {
						return false;
					}
				} else if(args.length == 1) {
					// Choose challenge based on ID passed by argument
					Integer index;
					try {
						index = Integer.parseInt(args[0]);
					} catch(NumberFormatException e) {
						return false;
					}

					ChallengeInfo info = allInfos.get(index);
					if(info != null) {
						info.hasCompleted = false;
						stopChallenge(info);
					} else {
						return false;
					}
				}
			}

			if(command.getName().equalsIgnoreCase("pausechallenge")) {
				if(args.length == 0) {
					// Choose current challenge of sender
					ChallengeInfo thisChallenge = getChallengeInfo(player);
					if(thisChallenge != null) {
						pauseChallenge(thisChallenge);
					} else {
						return false;
					}
				} else if(args.length == 1) {
					// Choose challenge based on ID passed by argument
					Integer index;
					try {
						index = Integer.parseInt(args[0]);
					} catch(NumberFormatException e) {
						return false;
					}

					ChallengeInfo info = allInfos.get(index);
					if(info != null) {
						pauseChallenge(info);
					} else {
						return false;
					}
				}
			}

			if(command.getName().equalsIgnoreCase("unpausechallenge")) {
				if(args.length == 0) {
					// Choose current challenge of sender
					ChallengeInfo thisChallenge = getChallengeInfo(player);
					if(thisChallenge != null) {
						unpauseChallenge(thisChallenge);
					} else {
						return false;
					}
				} else if(args.length == 1) {
					// Choose challenge based on ID passed by argument
					Integer index;
					try {
						index = Integer.parseInt(args[0]);
					} catch(NumberFormatException e) {
						return false;
					}

					ChallengeInfo info = allInfos.get(index);
					if(info != null) {
						unpauseChallenge(info);
					} else {
						return false;
					}
				}
			}

			if(command.getName().equalsIgnoreCase("inforeplay")) {
				if(args.length != 1) {
					return false;
				} else if(args.length == 1) {
					if(new File("replays", args[0] + ".newrun").exists()) {
						playReplay(args[0], true, player);
					} else {
						return false;
					}
				}
			}

			if(command.getName().equalsIgnoreCase("playreplay")) {
				if(args.length != 1) {
					return false;
				} else if(args.length == 1) {
					if(new File("replays", args[0] + ".newrun").exists()) {
						playReplay(args[0], false, player);
					} else {
						return false;
					}
				}
			}

			if(command.getName().equalsIgnoreCase("downloadreplay")) {
				if(args.length != 1) {
					return false;
				} else if(args.length == 1) {
					if(leaderboardClient != null) {
						if(leaderboardClient.downloadReplay(args[0])) {
							playReplay(args[0], true, player);
						} else {
							player.sendMessage("Could not download replay");
						}
					} else {
						player.sendMessage(
							"Not connected to the leaderboard, please restart the server with an internet connection");
					}
				}
			}

			if(command.getName().equalsIgnoreCase("leavereplay")) {
				WatchingReplayInfo replayInfo = getReplayInfo(player);

				if(replayInfo == null) {
					return false;
				} else {
					leaveReplay(replayInfo);
				}
			}
		}
		return true;
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();

		ChallengeInfo info = getChallengeInfo(player);

		if(info != null) {
			World overWorld   = info.overworld;
			Location bedSpawn = player.getBedSpawnLocation();

			if(bedSpawn != null
				&& (bedSpawn.getWorld() == overWorld
					|| bedSpawn.getWorld() == info.nether
					|| bedSpawn.getWorld() == info.end)) {
				event.setRespawnLocation(player.getBedSpawnLocation());
			} else {
				event.setRespawnLocation(overWorld.getSpawnLocation());
			}
		}
	}

	@Override
	public List<String> onTabComplete(
		CommandSender sender, Command command, String label, String[] args) {
		List<String> autocompleteElements = new ArrayList<>();

		if(command.getName().equalsIgnoreCase("startchallenge")) {
			// Autocomplete challenge name
			if(args.length == 1) {
				String[] typeArgs = args[0].split(":", -1);
				if(typeArgs.length == 1) {
					for(GenerationType generationType :
						GenerationType.values()) {
						if(generationType.name().toLowerCase().startsWith(
							   typeArgs[0].toLowerCase())) {
							autocompleteElements.add(generationType.name());
						}
					}
				} else {
					String currentArg = typeArgs[typeArgs.length - 1];
					String leadingUpTo
						= args[0].substring(0, args[0].lastIndexOf(":") + 1);
					for(GameplayTypes gameplayType : GameplayTypes.values()) {
						if(gameplayType.name().toLowerCase().startsWith(
							   currentArg.toLowerCase())) {
							autocompleteElements.add(
								leadingUpTo + gameplayType.name());
						}
					}
				}
			}

			// Seed can be "r" for random or anything else
			if(args.length == 2) {
				autocompleteElements.add("r");
			}

			// Autocomplete player name
			if(args.length > 2) {
				for(Player player : Bukkit.getOnlinePlayers()) {
					if(player.getName().toLowerCase().startsWith(
						   args[args.length - 1].toLowerCase())) {
						autocompleteElements.add(player.getName());
					}
				}
			}
		}

		if(command.getName().equalsIgnoreCase("stopchallenge")) {
			if(args.length == 1) {
				for(int key : allInfos.keySet()) {
					autocompleteElements.add(Integer.toString(key));
				}
			}
		}

		if(command.getName().equalsIgnoreCase("inforeplay")) {
			if(args.length == 1) {
				List<File> files = (List<File>)FileUtils.listFiles(
					new File("replays"), new String[] { "newrun" }, true);
				for(File file : files) {
					autocompleteElements.add(
						file.getName().replace(".newrun", ""));
				}
			}
		}

		if(command.getName().equalsIgnoreCase("playreplay")) {
			if(args.length == 1) {
				List<File> files = (List<File>)FileUtils.listFiles(
					new File("replays"), new String[] { "newrun" }, true);
				for(File file : files) {
					autocompleteElements.add(
						file.getName().replace(".newrun", ""));
				}
			}
		}

		return autocompleteElements;
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		World world = event.getWorld();

		ChallengeInfo type = getChallengeInfo(event.getWorld().getName());

		Chunk chunk = event.getChunk();

		if(type != null && !isPaused.get(type)) {
			/*
			if(type.gameplayTypes.contains(GameplayTypes.XRAY)) {
				for(Player player : Bukkit.getOnlinePlayers()) {
					for(int x = 0; x < CHUNK_SIZE; x++) {
						for(int y = 1; y < CHUNK_HEIGHT; y++) {
							for(int z = 0; z < CHUNK_SIZE; z++) {
								Block blockHere = chunk.getBlock(x, y, z);
								if(blockHere.getType()
										== XMaterial.STONE.parseMaterial()
									|| blockHere.getType()
										   == XMaterial.NETHERRACK
												  .parseMaterial()) {
									player.sendBlockChange(
										blockHere.getLocation(),
										airType.createBlockData());
								}
							}
						}
					}
				}
			}
			*/

			/*
			if(type.gameplayTypes.contains(GameplayTypes.SEE_ONE_CHUNK)) {
				for(Player player : Bukkit.getOnlinePlayers()) {
					if(player.getLocation().getChunk() != chunk) {
						for(int x = 0; x < CHUNK_SIZE; x++) {
							for(int y = 1; y < CHUNK_HEIGHT; y++) {
								for(int z = 0; z < CHUNK_SIZE; z++) {
									Block blockHere = chunk.getBlock(x, y, z);
									player.sendBlockChange(
										blockHere.getLocation(),
										airType.createBlockData());
								}
							}
						}
					}
				}
			}
			*/

			/*
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.ALWAYS_SWIM)) {
				// Probably will never work
				for(int x = 0; x < ChallengesImplementation.CHUNK_SIZE; x++) {
						for(int y = 0; y <
				ChallengesImplementation.CHUNK_HEIGHT; y++) { for(int z = 0; z <
				ChallengesImplementation.CHUNK_SIZE; z++) { Block block =
				chunk.getBlock(x, y, z); if(block.getType() == airType) {
												Waterlogged blockData
														=
				(Waterlogged)block.getBlockData();
				blockData.setWaterlogged(true); block.setBlockData(blockData);
										}
								}
						}
				}
			}
			*/
		}

		gameplayChanges.onChunkLoad(world, chunk);

		if(event.isNewChunk()
			&& getReplayInfo(event.getWorld().getName()) != null) {
			for(Entity entity : chunk.getEntities()) {
				entity.remove();
			}
		}
	}

	@EventHandler
	public void onWorldInit(WorldInitEvent event) {
		event.getWorld().setKeepSpawnInMemory(false);
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onChunkPopulate(ChunkPopulateEvent event) {
		World world        = event.getWorld();
		ChallengeInfo type = getChallengeInfo(world.getName());
		Chunk chunk        = event.getChunk();

		if(worldGenChunksSoFar != -1) {
			worldGenChunksSoFar++;

			for(Player player : Bukkit.getOnlinePlayers()) {
				player.sendMessage(
					ChatColor.WHITE
					+ Integer.toString(Math.floorDiv(
						worldGenChunksSoFar * 100, CHUNKS_PRELOADED_BY_SERVER))
					+ "% of chunks generated");
			}

			if(worldGenChunksSoFar == CHUNKS_PRELOADED_BY_SERVER) {
				// Done generating chunks
				worldGenChunksSoFar = -1;
			}
		}

		if(type != null && !isPaused.get(type)) {
			/*
			if(type.generationType == GenerationType.ERASED_CHUNKS) {
				Arrays.asList(chunk.getTileEntities())
					.stream()
					.forEach(blockState -> blockState.setRawData((byte)0));

				// World spawn chunk is safe
				if(!world.getSpawnLocation().getBlock().getChunk().equals(
					   chunk)) {
					// May seed in the future
					// 2% chance
					if(Math.random() < 0.99f) {
						for(int x = 0; x < ChallengesImplementation.CHUNK_SIZE;
							x++) {
							for(int y = 0;
								y < ChallengesImplementation.CHUNK_HEIGHT;
								y++) {
								for(int z = 0;
									z < ChallengesImplementation.CHUNK_SIZE;
									z++) {
									// Erase the chunk, unless it's an important
									// block
									Block block = chunk.getBlock(x, y, z);
									Material blockMaterial = block.getType();

									boolean notAir = blockMaterial != airType;
									boolean notNonErasableMaterial
										= !NonErasableMaterialsWhitelist
											   .contains(blockMaterial);
									boolean notNonErasableMaterialStructure
										=
			!NonErasableMaterialsStructuresWhitelist .contains(blockMaterial);
									// All blocks below 6 are bedrock
									// All blocks above 120 are nether roof
									if(y < 6 || y > 120
										|| (notAir && notNonErasableMaterial
											&& notNonErasableMaterialStructure))
			{ block.setType(airType, false);
									}
								}
							}
						}
					}
				}
			}*/

			/*
			if(type.generationType == GenerationType.FLOATING_VILLAGES) {
				Arrays.asList(chunk.getTileEntities())
					.stream()
					.forEach(blockState -> blockState.setRawData((byte)0));

				// World spawn chunk is safe
				if(!world.getSpawnLocation().getBlock().getChunk().equals(
					   chunk)) {
					for(int x = 0; x < ChallengesImplementation.CHUNK_SIZE;
						x++) {
						for(int y = 0;
							y < ChallengesImplementation.WORLD_HEIGHT; y++) {
							for(int z = 0;
								z < ChallengesImplementation.CHUNK_SIZE; z++) {
								Block block = chunk.getBlock(x, y, z);
								if(y != 0) {
									Material m = block.getType();
									if(FloatingVillageBlacklist.contains(m)) {
										block.setType(airType, false);
									}
								} else {
									block.setType(lavaType, false);
								}
							}
						}
					}
				}
			}*/
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		gameplayChanges.onPlayerMove(event);
	}

	@EventHandler
	public void onEntityPortal(EntityPortalEvent event) {
		// An attempt to replicate the original portal behavior
		Entity entity = event.getEntity();

		String baseWorldName = entity.getWorld().getName().split("_")[0];

		World overWorld   = Bukkit.getWorld(baseWorldName);
		World endWorld    = Bukkit.getWorld(baseWorldName + "_the_end");
		World netherWorld = Bukkit.getWorld(baseWorldName + "_nether");
		// Might not be vanilla behavior, but don't create portals if not
		// present
		if(entity.getWorld() == overWorld) {
			// May be traveling to either nether or end
			Material type = entity.getLocation().getBlock().getType();
			if(type == endPortalType) {
				// Traveling to end
				Location loc = new Location(endWorld, 100, 50, 0);
				// Only teleport if portal present
				if(loc.subtract(0, 1, 0).getBlock().getType()
					== XMaterial.OBSIDIAN.parseMaterial()) {
					event.setTo(loc);
				} else {
					event.setCancelled(true);
				}
			} else if(type == netherPortalType) {
				// Traveling to nether
				portalTravelAgent.setCurrentWorld(netherWorld);
				Location location
					= new Location(overWorld, event.getFrom().getBlockX() / 8,
						event.getFrom().getBlockY(),
						event.getFrom().getBlockZ() / 8);

				Location to = portalTravelAgent.findPortal(location);

				if(to == location || to == null) {
					event.setCancelled(true);
				} else {
					event.setTo(to);
				}
			}
		} else if(entity.getWorld() == netherWorld) {
			// Has to be traveling from nether to overworld
			portalTravelAgent.setCurrentWorld(overWorld);
			Location location = new Location(overWorld,
				event.getFrom().getBlockX() * 8, event.getFrom().getBlockY(),
				event.getFrom().getBlockZ() * 8);

			Location to = portalTravelAgent.findPortal(location);

			if(to == location || to == null) {
				event.setCancelled(true);
			} else {
				event.setTo(to);
			}
		} else if(entity.getWorld() == endWorld) {
			// Has to be traveling from end to overworld
			event.setTo(overWorld.getSpawnLocation());
		}
	}

	@EventHandler
	public void onPlayerPortal(PlayerPortalEvent event) {
		// An attempt to replicate the original portal behavior
		Player player = event.getPlayer();

		String baseWorldName = player.getWorld().getName().split("_")[0];

		World overWorld   = Bukkit.getWorld(baseWorldName);
		World endWorld    = Bukkit.getWorld(baseWorldName + "_the_end");
		World netherWorld = Bukkit.getWorld(baseWorldName + "_nether");

		if(event.getCause() == TeleportCause.NETHER_PORTAL) {
			Location location;
			if(player.getWorld() == overWorld) {
				portalTravelAgent.setCurrentWorld(netherWorld);
				location
					= new Location(netherWorld, event.getFrom().getBlockX() / 8,
						event.getFrom().getBlockY(),
						event.getFrom().getBlockZ() / 8);
			} else {
				portalTravelAgent.setCurrentWorld(overWorld);
				location
					= new Location(overWorld, event.getFrom().getBlockX() * 8,
						event.getFrom().getBlockY(),
						event.getFrom().getBlockZ() * 8);
			}

			event.setTo(portalTravelAgent.findOrCreate(location));
		}

		if(event.getCause() == TeleportCause.END_PORTAL) {
			if(player.getWorld() == overWorld) {
				Location loc = new Location(endWorld, 100, 50,
					0); // This is the vanilla location for
						// obsidian platform.
				Block block = loc.getBlock();
				for(int x = block.getX() - 2; x <= block.getX() + 2; x++) {
					for(int z = block.getZ() - 2; z <= block.getZ() + 2; z++) {
						Block platformBlock
							= new Location(endWorld, x, block.getY() - 2, z)
								  .getBlock();
						platformBlock.setType(
							XMaterial.OBSIDIAN.parseMaterial(), false);

						portalTravelAgent.addBlockLocation(platformBlock);
						for(int yMod = 1; yMod <= 3; yMod++) {
							Block airBlock = new Location(
								endWorld, x, platformBlock.getY() + yMod, z)
												 .getBlock();
							airBlock.setType(
								XMaterial.AIR.parseMaterial(), false);

							portalTravelAgent.addBlockLocation(airBlock);
						}
					}
				}
				event.setTo(loc);
			} else if(player.getWorld() == endWorld) {
				if(getChallengeInfo(player) != null) {
					// The player has WON, send challenge complete event
					Location defaultTargetLocation
						= player.getBedSpawnLocation() != null
							  ? player.getBedSpawnLocation()
							  : overWorld.getSpawnLocation();
					ChallengeInfo info = getChallengeInfo(player);

					ChallengeCompleteEvent completeEvent
						= new ChallengeCompleteEvent(
							player, defaultTargetLocation);
					getServer().getPluginManager().callEvent(completeEvent);
					if(!completeEvent.getCanceled()) {
						event.setTo(completeEvent.getTargetLocation());
					}

					info.hasCompleted = true;
					stopChallenge(info);

					event.setCancelled(true);
				} else if(getReplayInfo(player) != null) {
					event.setTo(
						getReplayInfo(player).overworld.getSpawnLocation());
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onPlayerTeleport(PlayerTeleportEvent event) {
		gameplayChanges.onPlayerTeleport(event);

		// To help out AdvancedReplay
		Player player = event.getPlayer();
		if(getChallengeInfo(player) != null) {
			teleportReplayPacketsId
				= getServer()
					  .getScheduler()
					  .runTaskTimer(this,
						  new Runnable() {
							  public void run() {
								  if(portalTravelAgent
										  .haveReplayBlockLocations()) {
									  ArrayList<Location> replayBlockLocations
										  = portalTravelAgent
												.getReplayBlockLocations();
									  ArrayList<BlockState> replayBlockState
										  = portalTravelAgent
												.getReplayBlockStates();

									  for(int i = 0;
										  i < replayBlockLocations.size();
										  i++) {
										  // Both are handled separately to
										  // prevent desync. In this specific
										  // case, the obsidian platform of the
										  // end
										  player.sendBlockChange(
											  replayBlockLocations.get(i),
											  replayBlockState.get(i).getType(),
											  replayBlockState.get(i)
												  .getRawData());
									  }

									  portalTravelAgent.clearBlockLocations();
									  Bukkit.getScheduler().cancelTask(
										  teleportReplayPacketsId);
								  }
							  }
						  },
						  2L, 1L)
					  .getTaskId();
		}
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		gameplayChanges.onPlayerInteract(event);
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		gameplayChanges.onBlockBreak(event);
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerToggleGlide(EntityToggleGlideEvent event) {
		gameplayChanges.onPlayerToggleGlide(event);
	}

	@EventHandler()
	public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
		gameplayChanges.onPlayerToggleFlight(event);
	}

	@EventHandler()
	public void onBlockPlace(BlockPlaceEvent event) {
		gameplayChanges.onBlockPlace(event);
	}

	@EventHandler
	public void onPlayerChangeBreath(EntityAirChangeEvent event) {
		gameplayChanges.onPlayerChangeBreath(event);
	}

	@EventHandler
	public void onPlayerDismount(EntityDismountEvent event) {
		gameplayChanges.onPlayerDismount(event);
	}

	@EventHandler
	public void onPlayerSprint(PlayerToggleSprintEvent event) {
		gameplayChanges.onPlayerSprint(event);
	}

	@EventHandler
	public void onDamageByEntity(EntityDamageByEntityEvent event) {
		gameplayChanges.onDamageByEntity(event);
	}

	@EventHandler
	public void onDamage(EntityDamageEvent event) {
		gameplayChanges.onDamage(event);
	}

	@EventHandler
	public void onMobDeath(EntityDeathEvent event) {
		gameplayChanges.onMobDeath(event);
	}

	@EventHandler
	public void onCraft(CraftItemEvent event) {
		gameplayChanges.onCraft(event);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if(getReplayInfo(event.getEntity().getWorld().getName()) != null) {
			if(event.getSpawnReason()
				== CreatureSpawnEvent.SpawnReason.NATURAL) {
				event.setCancelled(true);
			}
		}
	}
}