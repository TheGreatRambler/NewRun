package challengeserver.tgrcode.challenges;

import challengeserver.tgrcode.challenges.ChallengesImplementation.ChallengeInfo;
import com.cryptomorin.xseries.XMaterial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.*;
import org.spigotmc.event.entity.EntityDismountEvent;

public class GameplayChanges {
	// This is so the player actually tries to beat the game a bit
	public final HashSet<Material> DisallowedRandomDrops
		= new HashSet<>(Arrays.asList(new Material[] {
			XMaterial.AIR.parseMaterial(),
			XMaterial.BLAZE_POWDER.parseMaterial(),
			XMaterial.BLAZE_ROD.parseMaterial(),
			XMaterial.ENDER_PEARL.parseMaterial(),
			XMaterial.ENDER_EYE.parseMaterial(),
		}));

	public final HashSet<Material> Ores
		= new HashSet<>(Arrays.asList(new Material[] {
			XMaterial.COAL_ORE.parseMaterial(),
			XMaterial.IRON_ORE.parseMaterial(),
			XMaterial.LAPIS_ORE.parseMaterial(),
			XMaterial.GOLD_ORE.parseMaterial(),
			XMaterial.REDSTONE_ORE.parseMaterial(),
			XMaterial.DIAMOND_ORE.parseMaterial(),
			XMaterial.EMERALD_ORE.parseMaterial(),
			XMaterial.NETHER_QUARTZ_ORE.parseMaterial(),
			XMaterial.NETHER_GOLD_ORE.parseMaterial(),
			XMaterial.ANCIENT_DEBRIS.parseMaterial(),
		}));

	public final List<EntityType> EnemyMobs = Arrays.asList(new EntityType[] {
		EntityType.ELDER_GUARDIAN, EntityType.WITHER_SKELETON, EntityType.STRAY,
		EntityType.HUSK, EntityType.ZOMBIE_VILLAGER, EntityType.SKELETON_HORSE,
		EntityType.ZOMBIE_HORSE, EntityType.EVOKER, EntityType.VEX,
		EntityType.VINDICATOR, EntityType.ILLUSIONER, EntityType.CREEPER,
		EntityType.SKELETON, EntityType.SPIDER, EntityType.GIANT,
		EntityType.ZOMBIE, EntityType.SLIME, EntityType.GHAST,
		// EntityType.ZOMBIFIED_PIGLIN,
		EntityType.ENDERMAN, EntityType.CAVE_SPIDER, EntityType.SILVERFISH,
		EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.ENDER_DRAGON,
		EntityType.WITHER, EntityType.BAT, EntityType.WITCH,
		EntityType.ENDERMITE, EntityType.GUARDIAN, EntityType.SHULKER,
		// EntityType.DROWNED, EntityType.PILLAGER, EntityType.RAVAGER,
		// EntityType.HOGLIN,
		// EntityType.PIGLIN,
		// EntityType.ZOGLIN,
	});

	// Passed by the main class
	private Map<String, ChallengeInfo> nameToType;
	private Map<Player, ChallengeInfo> playerToType;
	private Map<Player, Long> playerTicksOnGround;
	private Map<Player, Long> playerTicksRunning;
	private Map<Player, Long> playerTicksSinceMove;
	private Map<World, Integer> currentLiquidHeight;
	private Map<ChallengeInfo, Boolean> isPaused;
	private Map<Player, Long> playerCraftingMultiplier;

	private static Long allowedTicksOnGround    = 8L;
	private static Long allowedTicksWithoutMove = 40L;
	public static int CHUNK_SIZE                = 16;
	public static int CHUNK_HEIGHT              = 256;
	public static int WORLD_HEIGHT              = 64;

	private Material airType;
	private Material compassType;
	private Material lavaType;
	private Material waterType;

	private Random gameplayRandom;

	private JavaPlugin plugin;

	// Challenge specific
	private ArrayList<Block> blocksPlacedByPlayer;
	private int globalCraftingFactor = 1;

	public GameplayChanges(JavaPlugin plugin_, Map<String, ChallengeInfo> types,
		Map<Player, ChallengeInfo> playerTypes,
		Map<ChallengeInfo, Boolean> paused) {
		plugin                   = plugin_;
		nameToType               = types;
		playerToType             = playerTypes;
		playerTicksOnGround      = new HashMap<>();
		playerTicksRunning       = new HashMap<>();
		playerTicksSinceMove     = new HashMap<>();
		currentLiquidHeight      = new HashMap<>();
		gameplayRandom           = new Random();
		blocksPlacedByPlayer     = new ArrayList<>();
		isPaused                 = paused;
		playerCraftingMultiplier = new HashMap<>();

		airType     = XMaterial.AIR.parseMaterial();
		compassType = XMaterial.COMPASS.parseMaterial();
		lavaType    = XMaterial.LAVA.parseMaterial();
		waterType   = XMaterial.WATER.parseMaterial();
	}

	private ChallengeInfo getChallengeInfo(String worldName) {
		return nameToType.getOrDefault(worldName, null);
	}

	private ChallengeInfo getChallengeInfo(Player player) {
		return playerToType.getOrDefault(player, null);
	}

	public void cleanUpChallenge(
		ChallengeInfo type, ArrayList<Player> players) {
		// TODO clean up challenges when they're done
	}

	public void onTick() {
		ArrayList<Chunk> handledChunks = new ArrayList<>();
		for(Player player : Bukkit.getOnlinePlayers()) {
			ChallengeInfo type = getChallengeInfo(player);

			if(type != null && !isPaused.get(type)) {
				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.ACCELERATION)) {
					Vector playerVelocity = player.getVelocity();
					double length         = new Vector(
                        playerVelocity.getX(), 0, playerVelocity.getZ())
										.lengthSquared();

					if(player.isOnGround()) {
						if(playerTicksOnGround.getOrDefault(player, 0L)
							> allowedTicksOnGround) {
							playerTicksRunning.put(player, 0L);
						} else {
							playerTicksOnGround.merge(player, 1L, Long::sum);
						}
					} else {
						playerTicksOnGround.put(player, 0L);
					}

					if(length < 0.0001) {
						playerTicksRunning.put(player, 0L);
					}

					Long ticks = playerTicksRunning.get(player);

					if(ticks == 0) {
						player.setWalkSpeed(0.2f);
					} else {
						if(ticks < 400) {
							player.setWalkSpeed(0.2f + (ticks / 500f));
						} else {
							player.setWalkSpeed(1f);
						}
					}

					playerTicksRunning.put(player, ticks + 1);
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.TNT)) {
					if(type.currentTick % 200 == 0) {
						player.getWorld().spawn(
							player.getLocation(), TNTPrimed.class);
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.PEARL)) {
					PlayerInventory inventory = player.getInventory();
					Material enderPearl = XMaterial.ENDER_PEARL.parseMaterial();
					if(inventory != null) {
						if(inventory.getItem(0) == null
							|| inventory.getItem(0).getType() != enderPearl
							|| inventory.getItem(0).getAmount() != 64) {
							player.getInventory().setItem(
								0, new ItemStack(enderPearl, 64));
						}
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.PIG)) {
					PlayerInventory inventory = player.getInventory();
					Material carrotOnAStick
						= XMaterial.CARROT_ON_A_STICK.parseMaterial();
					if(inventory != null) {
						if(inventory.getItem(0).getType() != carrotOnAStick
							|| inventory.getItem(0).getAmount() != 1) {
							player.getInventory().setItem(
								0, new ItemStack(carrotOnAStick, 1));
						}
					}
				}

				// Incredibly laggy
				/*
				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.GRAVITY)) {
					for(Chunk chunk : player.getWorld().getLoadedChunks()) {
						if(!handledChunks.contains(chunk)) {
							for(int x = 0; x < CHUNK_SIZE; x++) {
								for(int y = 1; y < CHUNK_HEIGHT; y++) {
									for(int z = 0; z < CHUNK_SIZE; z++) {
										Block blockHere
											= chunk.getBlock(x, y, z);
										Block blockBelow
											= blockHere.getRelative(
												BlockFace.DOWN);

										// Current block must not be liquid and
										// block below must be air
										if(!blockHere.isLiquid()
											&& (blockBelow.getType() == airType
												|| blockBelow.getType()
													   == XMaterial.CAVE_AIR
															  .parseMaterial()))
				{
											// Remove existing block
											blockHere.setType(airType);
											// Spawn falling block
											chunk.getWorld().spawnFallingBlock(
												blockHere.getLocation(),
												airType, (byte)0);
										}
									}
								}
							}

							handledChunks.add(chunk);
						}
					}
				}*/

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.ONLY_PLACE)) {
					// Player is allowed to prepare in a small radius
					Location playerLocation = player.getLocation();
					Location playerSpawn = player.getWorld().getSpawnLocation();
					if(playerSpawn.getBlock() != playerLocation.getBlock()) {
						Block groundBlock
							= playerLocation.getBlock().getRelative(
								BlockFace.DOWN);
						if(!blocksPlacedByPlayer.contains(groundBlock)
							&& groundBlock.getType() != airType
							&& groundBlock.getType()
								   != XMaterial.WATER.parseMaterial()) {
							// Block was not placed by the player
							// Massive damage, 5 hearts a second
							player.damage(0.5);
						}
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.ALWAYS_MOVING)) {
					Long ticksSinceMove
						= playerTicksSinceMove.getOrDefault(player, 0L);
					if(ticksSinceMove > allowedTicksWithoutMove) {
						player.damage(0.4);
					}
					playerTicksSinceMove.merge(player, 1L, Long::sum);
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes
						   .EVERY_30_SECONDS)) {
					final int ticksIn30Seconds = 20 * 30;
					if(type.currentTick % ticksIn30Seconds == 0) {
						Material[] allMaterials = Material.values();
						while(true) {
							Material chosenMaterial
								= allMaterials[gameplayRandom.nextInt(
									allMaterials.length)];
							if(chosenMaterial.isItem()) {
								Inventory inventory = player.getInventory();
								inventory.addItem(
									new ItemStack(chosenMaterial, 1));
								break;
							}
						}
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.SPRINT_SPEED)) {
					float newWalkSpeed = player.getWalkSpeed() - 0.02f;
					if(newWalkSpeed < 0.2f)
						newWalkSpeed = 0.2f;
					player.setWalkSpeed(newWalkSpeed);
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.BLOCK_SPEED)) {
					float newWalkSpeed = player.getWalkSpeed() - 0.01f;
					if(newWalkSpeed < 0.2f)
						newWalkSpeed = 0.2f;
					player.setWalkSpeed(newWalkSpeed);
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.JUMP_BOOST)) {
					final int ticksIn5Minutes = 20 * 60 * 5;
					if(type.currentTick != 0
						&& type.currentTick % ticksIn5Minutes == 0) {
						PotionEffect currentPotionEffect
							= player.getPotionEffect(PotionEffectType.JUMP);
						if(currentPotionEffect == null) {
							player.addPotionEffect(
								new PotionEffect(PotionEffectType.JUMP,
									Integer.MAX_VALUE, 1, false, false));
						} else {
							if(currentPotionEffect.getAmplifier() != 255) {
								player.removePotionEffect(
									PotionEffectType.JUMP);
								player.addPotionEffect(new PotionEffect(
									PotionEffectType.JUMP, Integer.MAX_VALUE,
									currentPotionEffect.getAmplifier() + 1,
									false, false));
							}
						}
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.AIR_TELEPORT)) {
					final int ticksIn5Minutes = 20 * 60 * 5;
					if(type.currentTick != 0
						&& type.currentTick % ticksIn5Minutes == 0) {
						Location newLocation = player.getLocation();
						newLocation.setY(
							player.getWorld().getHighestBlockYAt(newLocation)
							+ 50);
						player.teleport(newLocation);
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.MOB_SPAWNS)) {
					final int ticksIn10Seconds = 20 * 10;
					if(type.currentTick != 0
						&& type.currentTick % ticksIn10Seconds == 0) {
						// Attempts 10 spots in the general vicinity
						for(int i = 0; i < 10; i++) {
							// 10 by 10 by 10 cube around the player
							Location testLocation = player.getLocation().add(
								new Vector(gameplayRandom.nextInt(11) - 5,
									gameplayRandom.nextInt(11) - 5,
									gameplayRandom.nextInt(11) - 5));
							if(testLocation.getBlock().getType() == airType) {
								player.getWorld().spawnEntity(testLocation,
									EnemyMobs.get(gameplayRandom.nextInt(
										EnemyMobs.size())));
								break;
							}
						}
					}
				}

				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.MOUNTAIN_AIR)) {
					final int ticksInSecond = 20;
					if(type.currentTick != 0
						&& type.currentTick % ticksInSecond == 0) {
						if(player.isValid()) {
							// Health does not change at 70
							double y = player.getLocation().getY();

							// At heighest elevation, you get 10 hearts every
							// second
							double health = player.getHealth();
							if(y >= 70.0) {
								health += (y - 70) / (CHUNK_HEIGHT - 70) * 20.0;
								if(health
									> player
										  .getAttribute(
											  Attribute.GENERIC_MAX_HEALTH)
										  .getValue())
									health = 20.0;
								player.setHealth(health);
							} else {
								// Lose half a heart a second at lowest
								// elevation
								player.damage((70 - y) / 70 * 1.0);
							}
						}
					}
				}
			}
		}

		for(ChallengeInfo challengeInfo : nameToType.values()) {
			boolean risingLava = challengeInfo.gameplayTypes.contains(
				ChallengesImplementation.GameplayTypes.RISING_LAVA);
			boolean risingWater = challengeInfo.gameplayTypes.contains(
				ChallengesImplementation.GameplayTypes.RISING_WATER);

			if(risingLava || risingWater) {
				final int ticksToWait = 20 * 60;
				if(challengeInfo.currentTick != 0
					&& challengeInfo.currentTick % ticksToWait == 0) {
					currentLiquidHeight.merge(
						challengeInfo.overworld, 1, Integer::sum);
					currentLiquidHeight.merge(
						challengeInfo.nether, 1, Integer::sum);
					currentLiquidHeight.merge(
						challengeInfo.end, 1, Integer::sum);

					for(Chunk chunk : challengeInfo.overworld.getLoadedChunks())
						onChunkLoad(challengeInfo.overworld, chunk);
					for(Chunk chunk : challengeInfo.nether.getLoadedChunks())
						onChunkLoad(challengeInfo.nether, chunk);
					for(Chunk chunk : challengeInfo.end.getLoadedChunks())
						onChunkLoad(challengeInfo.end, chunk);
				}
			}

			if(challengeInfo.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.FIRE_RAIN)) {
				final int ticksInSecond = 20;
				// A minute before it starts
				final int ticksBeforeStart = 20 * 60 - 1;
				if(challengeInfo.currentTick > ticksBeforeStart) {
					if(challengeInfo.currentTick % ticksInSecond == 0) {
						for(Player player : Bukkit.getOnlinePlayers()) {
							// Attempts 10 spots in the general vicinity
							for(int i = 0; i < 80; i++) {
								// 10 by 10 by 10 cube around the player
								Location testLocation
									= player.getLocation().add(new Vector(
										gameplayRandom.nextInt(41) - 20,
										gameplayRandom.nextInt(41) - 20,
										gameplayRandom.nextInt(41) - 20));
								if(testLocation.getBlock().getType()
									== airType) {
									player.getWorld().spawnParticle(
										Particle.DRIP_LAVA, testLocation, 1);
								}
							}

							for(int i = 0; i < 100; i++) {
								Location testLocation
									= player.getLocation().add(new Vector(
										gameplayRandom.nextInt(81) - 40, 0,
										gameplayRandom.nextInt(81) - 40));
								Block below
									= player.getWorld().getHighestBlockAt(
										testLocation);
								if(below.getType() != waterType) {
									below.getRelative(BlockFace.UP)
										.setType(
											XMaterial.FIRE.parseMaterial());
								}
							}

							// Rain particles from the sky
							for(int i = 0; i < 3000; i++) {
								Location loc = player.getLocation().add(
									new Vector(gameplayRandom.nextInt(41) - 20,
										player.getLocation().getY() + 50,
										gameplayRandom.nextInt(41) - 20));
								player.getWorld().spawnParticle(
									Particle.DRIP_LAVA, loc, 1);
							}
						}
					}

					for(Player player : Bukkit.getOnlinePlayers()) {
						// Apply fire damage if under sky
						if(player.getLocation().getY()
							> player.getWorld()
								  .getHighestBlockAt(player.getLocation())
								  .getY()) {
							player.setFireTicks(20);
						}
					}
				}
			}

			challengeInfo.currentTick++;
		}
	}

	public void onPlayerMove(PlayerMoveEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			/*
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.SEE_ONE_CHUNK)) {
				Chunk oldChunk = event.getFrom().getChunk();
				Chunk newChunk = event.getTo().getChunk();
				if(oldChunk.equals(newChunk)) {
					// Has entered new chunk
					for(int x = 0; x < ChallengesImplementation.CHUNK_SIZE;
						x++) {
						for(int y = 1;
							y < ChallengesImplementation.CHUNK_HEIGHT; y++) {
							for(int z = 0;
								z < ChallengesImplementation.CHUNK_SIZE; z++) {
								// Hide last chunk
								player.sendBlockChange(
									oldChunk.getBlock(x, y, z).getLocation(),
									airType, (byte)0);
								// Show this one
								Block newBlock = newChunk.getBlock(x, y, z);
								player.sendBlockChange(newBlock.getLocation(),
									newBlock.getType(), (byte)0);
							}
						}
					}
				}
			}
			*/

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PEARL)) {
				if(player.getLocation()
						.getBlock()
						.getRelative(BlockFace.DOWN)
						.getType()
					!= airType) {
					Location loc  = event.getTo();
					Location from = event.getFrom();
					loc.setX(from.getX());
					loc.setY(Math.floor(from.getY()));
					loc.setZ(from.getZ());
					/*if(chosenMaterial.isItem()) {
						Inventory inventory = player.getInventory();
						inventory.addItem(new ItemStack(chosenMaterial, 1));
						break;
					}*/
					event.setTo(loc);
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.FLY)) {
				if(player.getLocation()
						.getBlock()
						.getRelative(BlockFace.DOWN)
						.getType()
					!= airType) {
					player.setAllowFlight(true);
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.DOUBLE_JUMP)) {
				if(player.getLocation()
						.getBlock()
						.getRelative(BlockFace.DOWN)
						.getType()
					!= airType) {
					player.setAllowFlight(true);
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.LAVA)) {
				if(!event.getTo().getBlock().equals(
					   event.getFrom().getBlock())) {
					Block highest = player.getWorld().getHighestBlockAt(
						player.getLocation());
					Material lava = XMaterial.LAVA.parseMaterial();
					// TODO lava still repeating
					if(highest.getType() != lava) {
						highest.getRelative(0, 50, 0).setType(lava);
					}
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.EARN_WALK)) {
				if(!event.getTo().getBlock().equals(
					   event.getFrom().getBlock())) {
					Material chosenMaterial = player.getLocation()
												  .getBlock()
												  .getRelative(BlockFace.DOWN)
												  .getType();

					if(chosenMaterial.isItem()) {
						Inventory inventory = player.getInventory();
						inventory.addItem(new ItemStack(chosenMaterial, 1));
					}
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.TNT_FALL)) {
				if(!event.getTo().getBlock().equals(
					   event.getFrom().getBlock())) {
					Location loc = player.getLocation();
					// loc.setY(CHUNK_HEIGHT);
					loc.setY(120);
					Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
						plugin, new Runnable() {
							public void run() {
								player.getWorld().spawn(loc, TNTPrimed.class);
							}
						}, 20L * 15);
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.RANDOM_WALK)) {
				if(!event.getTo().getBlock().equals(
					   event.getFrom().getBlock())) {
					Block groundBlock
						= player.getLocation().getBlock().getRelative(
							BlockFace.DOWN);
					if(groundBlock.getType() != airType) {
						Material[] allMaterials = Material.values();
						while(true) {
							Material chosenMaterial
								= allMaterials[gameplayRandom.nextInt(
									allMaterials.length)];
							if(chosenMaterial.isSolid()) {
								groundBlock.setType(chosenMaterial);
								break;
							}
						}
					}
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.ALWAYS_MOVING)) {
				playerTicksSinceMove.put(player, 0L);
			}
		}
	}

	public void onPlayerInteract(PlayerInteractEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.MANHUNT)) {
				if(event.getAction() == Action.RIGHT_CLICK_AIR
					|| event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					if(event.getItem().getType() == compassType) {
						player.setCompassTarget(
							type.players.get(0).getLocation());
					}
				}
			}
		}
	}

	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PIG)) {
				Entity pig = player.getWorld().spawnEntity(
					player.getLocation(), EntityType.PIG);
				pig.addPassenger(player);
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.HORSE)) {
				Horse horse = (Horse)player.getWorld().spawnEntity(
					player.getLocation(), EntityType.HORSE);
				horse.setTamed(true);
				horse.getInventory().setSaddle(
					new ItemStack(XMaterial.SADDLE.parseMaterial()));
				horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
					.setBaseValue(0.3375);
				horse.getAttribute(Attribute.GENERIC_MAX_HEALTH)
					.setBaseValue(30);
				horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH)
					.setBaseValue(1.0);
				horse.addPassenger(player);
			}
		}
	}

	public void onPlayerStartEvent(Player player) {
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.MANHUNT)) {
				int playerIndex = type.players.indexOf(player);
				if(playerIndex != 0) {
					player.getInventory().setItem(
						0, new ItemStack(compassType, 1));
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.ELYTRA)) {
				player.setGliding(true);
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.FLY)) {
				player.setAllowFlight(true);
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PIG)) {
				Pig pig = (Pig)player.getWorld().spawnEntity(
					player.getLocation(), EntityType.PIG);
				pig.addPassenger(player);
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.HORSE)) {
				Horse horse = (Horse)player.getWorld().spawnEntity(
					player.getLocation(), EntityType.HORSE);
				horse.setTamed(true);
				horse.getInventory().setSaddle(
					new ItemStack(XMaterial.SADDLE.parseMaterial()));
				horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)
					.setBaseValue(0.3375);
				horse.getAttribute(Attribute.GENERIC_MAX_HEALTH)
					.setBaseValue(30);
				horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH)
					.setBaseValue(1.0);
				horse.addPassenger(player);
			}
		}
	}

	public void onChallengeEnd(ChallengeInfo info) {
		// Nothing for now
	}

	public void onPlayerTeleport(PlayerTeleportEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PEARL)) {
				if(event.getCause()
					== PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
					event.setCancelled(true);
					player.setNoDamageTicks(1);
					player.teleport(event.getTo());
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.ELYTRA)) {
				player.setGliding(true);
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.DOUBLE_JUMP)) {
				if(player.getLocation()
						.getBlock()
						.getRelative(BlockFace.DOWN)
						.getType()
					!= airType) {
					player.setAllowFlight(true);
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PIG)) {
				if(player.isInsideVehicle()) {
					if(player.getVehicle() instanceof Pig) {
						event.setCancelled(true);
						Entity mount = player.getVehicle();
						player.teleport(event.getTo());
						mount.teleport(player);
						mount.addPassenger(player);
					}
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.HORSE)) {
				if(player.isInsideVehicle()) {
					if(player.getVehicle() instanceof Horse) {
						event.setCancelled(true);
						Entity mount = player.getVehicle();
						player.teleport(event.getTo());
						mount.teleport(player);
						mount.addPassenger(player);
					}
				}
			}
		}
	}

	public void onBlockBreak(BlockBreakEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);
		Block block                                 = event.getBlock();

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.DROP_QUANITIES)) {
				event.setCancelled(true);

				for(ItemStack stack : block.getDrops(
						event.getPlayer().getInventory().getItemInMainHand())) {
					if(stack.getType() != airType) {
						stack.setAmount(gameplayRandom.nextInt(65));
						block.getWorld().dropItemNaturally(
							block.getLocation(), stack);
					}
				}

				block.setType(airType);
			} else if(type.gameplayTypes.contains(
						  ChallengesImplementation.GameplayTypes.DROP)) {
				event.setCancelled(true);

				// Drop normal items
				for(ItemStack stack : block.getDrops(
						event.getPlayer().getInventory().getItemInMainHand())) {
					block.getWorld().dropItemNaturally(
						block.getLocation(), stack);
				}

				int numberOfItemStacks  = 1 + gameplayRandom.nextInt(3);
				Material[] allMaterials = Material.values();
				for(int i = 0; i < numberOfItemStacks; i++) {
					Material chosenMaterial
						= allMaterials[gameplayRandom.nextInt(
							allMaterials.length)];
					if(chosenMaterial.isItem()
						&& !DisallowedRandomDrops.contains(chosenMaterial)) {
						block.getWorld().dropItemNaturally(block.getLocation(),
							new ItemStack(
								chosenMaterial, gameplayRandom.nextInt(65)));
					}
				}

				block.setType(airType);
			} else if(type.gameplayTypes.contains(
						  ChallengesImplementation.GameplayTypes.RANDOM_ORE)) {
				if(Ores.contains(block.getType())) {
					event.setCancelled(true);

					int numberOfItemStacks  = 1 + gameplayRandom.nextInt(3);
					Material[] allMaterials = Material.values();
					for(int i = 0; i < numberOfItemStacks; i++) {
						Material chosenMaterial
							= allMaterials[gameplayRandom.nextInt(
								allMaterials.length)];
						if(chosenMaterial.isItem()
							&& !DisallowedRandomDrops.contains(
								chosenMaterial)) {
							block.getWorld().dropItemNaturally(
								block.getLocation(),
								new ItemStack(chosenMaterial,
									gameplayRandom.nextInt(65)));
						}
					}

					block.setType(airType);
				}
			}
		}
	}

	public void onPlayerToggleGlide(EntityToggleGlideEvent event) {
		if(event.getEntity() instanceof Player) {
			Player player = (Player)event.getEntity();
			ChallengesImplementation.ChallengeInfo type
				= getChallengeInfo(player);

			if(type != null && !isPaused.get(type)) {
				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.ELYTRA)) {
					event.setCancelled(true);
				}
			}
		}
	}

	public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.DOUBLE_JUMP)) {
				if(!(player.getGameMode() == GameMode.CREATIVE
					   || player.getGameMode() == GameMode.SPECTATOR)) {
					event.setCancelled(true);
					player.setAllowFlight(false);
					player.setVelocity(
						player.getLocation().getDirection().multiply(0.9).setY(
							0.8));
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.FLY)) {
				if(!(player.getGameMode() == GameMode.CREATIVE
					   || player.getGameMode() == GameMode.SPECTATOR)) {
					player.setAllowFlight(false);
				}
			}
		}
	}

	public void onBlockPlace(BlockPlaceEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);
		Block block                                 = event.getBlock();

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.ONLY_PLACE)) {
				blocksPlacedByPlayer.add(block);
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.BLOCK_SPEED)) {
				float newWalkSpeed = player.getWalkSpeed() + 0.3f;
				if(newWalkSpeed > 1f)
					newWalkSpeed = 1f;
				player.setWalkSpeed(newWalkSpeed);
			}
		}
	}

	public void onPlayerChangeBreath(EntityAirChangeEvent event) {
		if(event.getEntity() instanceof Player) {
			Player player = (Player)event.getEntity();
			ChallengesImplementation.ChallengeInfo type
				= getChallengeInfo(player);

			if(type != null && !isPaused.get(type)) {
				/*
				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.ALWAYS_SWIM)) {
					// Reset counter
					event.setAmount(300);
				}
				*/
			}
		}
	}

	public void onPlayerDismount(EntityDismountEvent event) {
		if(event.getEntity() instanceof Player) {
			Player player = (Player)event.getEntity();
			ChallengesImplementation.ChallengeInfo type
				= getChallengeInfo(player);

			if(type != null && !isPaused.get(type)) {
				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.PIG)) {
					event.setCancelled(true);
				}
			}

			if(type != null && !isPaused.get(type)) {
				if(type.gameplayTypes.contains(
					   ChallengesImplementation.GameplayTypes.HORSE)) {
					event.setCancelled(true);
				}
			}
		}
	}

	public void onPlayerSprint(PlayerToggleSprintEvent event) {
		Player player                               = event.getPlayer();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.SPRINT_SPEED)) {
				if(event.isSprinting()) {
					float newWalkSpeed = player.getWalkSpeed() + 0.3f;
					if(newWalkSpeed > 1f)
						newWalkSpeed = 1f;
					player.setWalkSpeed(newWalkSpeed);
				}
			}
		}
	}

	// Used by some challenges
	public void onChunkLoad(World world, Chunk chunk) {
		ChallengeInfo type = getChallengeInfo(world.getName());

		if(type != null && !isPaused.get(type)) {
			boolean risingLava = type.gameplayTypes.contains(
				ChallengesImplementation.GameplayTypes.RISING_LAVA);
			boolean risingWater = type.gameplayTypes.contains(
				ChallengesImplementation.GameplayTypes.RISING_WATER);

			if(risingLava || risingWater) {
				for(int x = 0; x < ChallengesImplementation.CHUNK_SIZE; x++) {
					for(int y = 0;
						y < currentLiquidHeight.getOrDefault(world, 0); y++) {
						for(int z = 0; z < ChallengesImplementation.CHUNK_SIZE;
							z++) {
							Block block            = chunk.getBlock(x, y, z);
							Material blockMaterial = block.getType();

							if(blockMaterial == airType
								|| blockMaterial == waterType) {
								block.setType(
									risingLava ? lavaType : waterType, false);
							}
						}
					}
				}
			}
		}
	}

	public void onDamageByEntity(EntityDamageByEntityEvent event) {
		ChallengesImplementation.ChallengeInfo type
			= getChallengeInfo(event.getEntity().getWorld().getName());

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PIG)) {
				if(event.getEntity() instanceof Pig) {
					for(Entity passenger : event.getEntity().getPassengers()) {
						if(passenger instanceof Player) {
							event.setCancelled(true);
						}
					}
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.HORSE)) {
				if(event.getEntity() instanceof Horse) {
					for(Entity passenger : event.getEntity().getPassengers()) {
						if(passenger instanceof Player) {
							event.setCancelled(true);
						}
					}
				}
			}
		}
	}

	public void onDamage(EntityDamageEvent event) {
		ChallengesImplementation.ChallengeInfo type
			= getChallengeInfo(event.getEntity().getWorld().getName());

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.PIG)) {
				if(event.getEntity() instanceof Pig) {
					for(Entity passenger : event.getEntity().getPassengers()) {
						if(passenger instanceof Player) {
							event.setCancelled(true);
						}
					}
				}
			}

			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.HORSE)) {
				if(event.getEntity() instanceof Horse) {
					for(Entity passenger : event.getEntity().getPassengers()) {
						if(passenger instanceof Player) {
							event.setCancelled(true);
						}
					}
				}
			}
		}
	}

	public void onMobDeath(EntityDeathEvent event) {
		ChallengesImplementation.ChallengeInfo type
			= getChallengeInfo(event.getEntity().getWorld().getName());
		Entity entity = event.getEntity();

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes.LOOT_MOBS)) {
				if(entity.getType() != EntityType.PLAYER) {
					for(ItemStack stack : event.getDrops()) {
						entity.getWorld().dropItemNaturally(
							entity.getLocation(), stack);
					}

					int numberOfItemStacks  = 1 + gameplayRandom.nextInt(3);
					Material[] allMaterials = Material.values();
					for(int i = 0; i < numberOfItemStacks; i++) {
						Material chosenMaterial
							= allMaterials[gameplayRandom.nextInt(
								allMaterials.length)];
						if(chosenMaterial.isItem()
							&& !DisallowedRandomDrops.contains(
								chosenMaterial)) {
							entity.getWorld().dropItemNaturally(
								entity.getLocation(),
								new ItemStack(chosenMaterial,
									gameplayRandom.nextInt(65)));
						}
					}
				}
			}
		}
	}

	public void onCraft(CraftItemEvent event) {
		Player player = (Player)event.getWhoClicked();
		ChallengesImplementation.ChallengeInfo type = getChallengeInfo(player);

		if(type != null && !isPaused.get(type)) {
			if(type.gameplayTypes.contains(
				   ChallengesImplementation.GameplayTypes
					   .MULTIPLIED_CRAFTING)) {
				playerCraftingMultiplier.merge(player, 1L, Long::sum);

				ItemStack oldItemstack = event.getRecipe().getResult();
				oldItemstack.setAmount(
					oldItemstack.getAmount()
					* playerCraftingMultiplier.get(player).intValue());
				event.getInventory().setResult(oldItemstack);
			}
		}
	}
}