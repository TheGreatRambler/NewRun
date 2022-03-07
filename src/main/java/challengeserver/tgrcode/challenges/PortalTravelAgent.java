package challengeserver.tgrcode.challenges;

import com.cryptomorin.xseries.XBlock;
import com.cryptomorin.xseries.XMaterial;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;

// Modified version of the file here
// https://github.com/GlowstoneMC/Glowstone/blob/8accf54501a046c4ec6e5571b4d9f6b75b1b20ee/src/main/java/net/glowstone/util/GlowTravelAgent.java
public class PortalTravelAgent {
	private int searchRadius               = 128;
	private static boolean canCreatePortal = true;
	private World world;

	private ArrayList<Location> portalLocations;

	private ArrayList<Location> replayBlockLocation;
	private ArrayList<BlockState> replayBlockStates;

	public PortalTravelAgent() {
		portalLocations     = new ArrayList<>();
		replayBlockLocation = new ArrayList<>();
		replayBlockStates   = new ArrayList<>();
	}

	public void setCurrentWorld(World world) {
		this.world = world;
	}

	public void addPortalLocation(Location loc) {
		portalLocations.add(loc);
	}

	public Boolean haveReplayBlockLocations() {
		return replayBlockLocation.size() != 0;
	}

	public ArrayList<Location> getReplayBlockLocations() {
		return replayBlockLocation;
	}

	public ArrayList<BlockState> getReplayBlockStates() {
		return replayBlockStates;
	}

	public void addBlockLocation(Block block, int subId) {
		replayBlockLocation.add(block.getLocation());
		replayBlockStates.add(block.getState());
	}

	public void addBlockLocation(Block block) {
		addBlockLocation(block, 0);
	}

	public void clearBlockLocations() {
		replayBlockLocation.clear();
		replayBlockStates.clear();
	}

	public Location findPortal(Location destination) {
		Location minLoc    = null;
		double minDistance = Double.MAX_VALUE;

		final int maxY
			= world.getEnvironment() == World.Environment.NETHER ? 127 : 255;
		final int blockX = destination.getBlockX();
		final int blockZ = destination.getBlockZ();

		final Material netherPortalMaterial
			= XMaterial.NETHER_PORTAL.parseMaterial();

		// Clean up portal locations
		portalLocations.removeIf(loc -> {
			final Block toCompare = loc.getBlock();
			if(toCompare.getWorld() != destination.getWorld()) {
				return false;
			} else {
				return toCompare.getType() != netherPortalMaterial;
			}
		});

		for(Location portalLocation : portalLocations) {
			double x = portalLocation.getX();
			double y = portalLocation.getY();
			double z = portalLocation.getZ();
			// Check if portal is sufficiently close
			if(x >= (blockX - searchRadius) && x < (blockX + searchRadius)
				&& z >= (blockZ - searchRadius) && z < (blockZ + searchRadius)
				&& y >= 0 && y <= maxY
				&& portalLocation.getWorld() == destination.getWorld()) {

				double distance = portalLocation.clone()
									  .subtract(destination)
									  .toVector()
									  .length();
				if(distance < minDistance) {
					minDistance = distance;
					minLoc      = portalLocation;
				}
			}
		}

		if(minLoc == null) {
			// Revert to slow method
			for(int x = blockX - searchRadius; x < (blockX + searchRadius);
				x++) {
				for(int z = blockZ - searchRadius; z < (blockZ + searchRadius);
					z++) {
					for(int y = maxY; y >= 0; y--) {
						final Block toCompare = world.getBlockAt(x, y, z);

						if(toCompare.getType() == netherPortalMaterial
							&& toCompare.getRelative(BlockFace.DOWN).getType()
								   != netherPortalMaterial) {
							portalLocations.add(toCompare.getLocation());

							final Location portalLocation
								= toCompare.getLocation();
							double distance = portalLocation.clone()
												  .subtract(destination)
												  .toVector()
												  .length();
							if(distance < minDistance) {
								minDistance = distance;
								minLoc      = portalLocation;
							}
						}
					}
				}
			}
		}

		return minLoc;
	}

	public Location findOrCreate(Location location) {
		Location found = findPortal(location);
		if(found == null) {
			if(canCreatePortal && createPortal(location)) {
				found = findPortal(location);
			} else {
				found = location;
			}
		}

		return found;
	}

	public boolean getCanCreatePortal() {
		return canCreatePortal;
	}

	public boolean createPortal(Location destination) {
		if(world.getEnvironment() == World.Environment.THE_END) {
			return false;
		}

		double distance = -1.0D;
		// requested location data
		final int reqX = destination.getBlockX();
		final int reqY = destination.getBlockY();
		final int reqZ = destination.getBlockZ();
		// actual portal location
		int actualX = reqX;
		int actualY = reqY;
		int actualZ = reqZ;
		// axis of the portal. 0 -> X axis, 1, -1 -> Z axis
		int axis   = 0;
		int random = ThreadLocalRandom.current().nextInt(4);
		int worldHeight
			= world.getEnvironment() == World.Environment.NETHER ? 128 : 256;

		// try to find a suitable area for building the portal in range between
		// x - 16, z - 16 and x + 16, z + 16.

		// first attempt: find a portal spot that not only guarantees space for
		// the portal, but also space around it
		// iteration from x - 16 to x + 16
		for(int tempX = reqX - 16; tempX <= reqX + 16; ++tempX) {
			// calculation of xOffset from the requested location
			double offsetX = (double)tempX + 0.5D - reqX;
			// iteration from z - 16 to z + 16
			for(int tempZ = reqZ - 16; tempZ <= reqZ + 16; ++tempZ) {
				// calculation of zOffset from the requested location
				double offsetZ = (double)tempZ + 0.5D - reqZ;
			// search for suitable build location
			yLoop:
				for(int tempY = worldHeight - 1; tempY >= 0; --tempY) {
					// new Location to test suitability
					Location location
						= new Location(world, tempX, tempY, tempZ);

					// Continue if block is not air
					if(location.getBlock().getType()
						!= XMaterial.AIR.parseMaterial()) {
						continue;
					}
					// Move to the ground
					while(tempY > 0
						  && location.getBlock()
									 .getRelative(BlockFace.DOWN)
									 .getType()
								 == XMaterial.AIR.parseMaterial()) {
						--tempY;
						location.subtract(0, 1, 0);
					}

					// try portals in all four directions
					for(int riv = random; riv < random + 4; ++riv) {
						// coefficients to easily check whether the environment
						// is "save" (solid blocks at the ground, air around the
						// portal)
						int coEff1 = riv % 2;
						int coEff2 = 1 - coEff1;
						if(riv % 4 >= 2) {
							coEff1 = -coEff1;
							coEff2 = -coEff2;
						}

						// guarantee space before portal
						for(int safeSpace1 = 0; safeSpace1 < 3; ++safeSpace1) {
							// guarantee portal width
							for(int safeSpace2 = -1; safeSpace2 < 3;
								++safeSpace2) {
								// -1 is ground, 0-3 room above -> nether portal
								// is 5 blocks high
								for(int height = -1; height < 4; ++height) {
									location.setX(tempX + safeSpace2 * coEff1
												  + safeSpace1 * coEff2);
									location.setY(tempY + height);
									location.setZ(tempZ + safeSpace2 * coEff2
												  - safeSpace1 * coEff1);
									Material material
										= location.getBlock().getType();
									if(height < 0 && !material.isSolid()
										|| height >= 0
											   && material
													  != XMaterial.AIR
															 .parseMaterial()) {
										continue yLoop;
									}
								}
							}
						}
						// calculate yOffset from requested location
						double offsetY = (double)tempY + 0.5D - reqY;
						// calculate portal distance from requested location
						double newDist = offsetX * offsetX + offsetY * offsetY
										 + offsetZ * offsetZ;
						// if the new portal location is closer to the requested
						// one, yield it for portal placement
						if(distance < 0.0D || newDist < distance) {
							distance = newDist;
							actualX  = tempX;
							actualY  = tempY;
							actualZ  = tempZ;
							axis     = riv % 4;
						}
					}
				}
			}
		}

		// If no portal spot is found this way, try to find a position where at
		// least portal width and height are guaranteed
		if(distance < 0.0D) {
			// iteration from x - 16 to x + 16
			for(int tempX = reqX - 16; tempX <= reqX + 16; ++tempX) {
				// calculation of xOffset from the requested location
				double offsetX = (double)tempX + 0.5D - reqX;
				// iteration from z - 16 to z + 16
				for(int tempZ = reqZ - 16; tempZ <= reqZ + 16; ++tempZ) {
					// calculation of zOffset from the requested location
					double offsetZ = (double)tempZ + 0.5D - reqZ;
				// search for suitable build location
				yLoop2:
					for(int tempY = worldHeight - 1; tempY >= 0; --tempY) {
						// new Location to test suitability
						Location location
							= new Location(world, tempX, tempY, tempZ);

						// Continue if block is not air
						if(location.getBlock().getType()
							!= XMaterial.AIR.parseMaterial()) {
							continue;
						}
						// if block is air, move to the ground
						while(tempY > 0
							  && location.getBlock()
										 .getRelative(BlockFace.DOWN)
										 .getType()
									 == XMaterial.AIR.parseMaterial()) {
							location.subtract(0, 1, 0);
							--tempY;
						}

						// try portals in x and z direction, just needs to be
						// done 2 times as we don't require extra space around
						// the portal
						for(int riv = random; riv < random + 2; ++riv) {
							// coefficients for easier checking
							int coEff1 = riv % 2;
							int coEff2 = 1 - coEff1;

							// just guarantee portal width
							for(int safeSpace = -1; safeSpace < 3;
								++safeSpace) {
								// -1 is ground, 0-3 room above -> nether portal
								// is 5 blocks high
								for(int height = -1; height < 4; ++height) {
									location.setX(tempX + safeSpace * coEff1);
									location.setY(tempY + height);
									location.setZ(tempZ + safeSpace * coEff2);

									Material material
										= location.getBlock().getType();
									if(height < 0 && !material.isSolid()
										|| height >= 0
											   && material
													  != XMaterial.AIR
															 .parseMaterial()) {
										continue yLoop2;
									}
								}
							}

							// calculate yOffset from requested location
							double offsetY = (double)tempY + 0.5D - reqY;
							// calculate portal distance from requested location
							double newDist = offsetX * offsetX
											 + offsetY * offsetY
											 + offsetZ * offsetZ;

							// if the new portal location is closer to the
							// requested one, yield it for portal placement
							if(distance < 0.0D || newDist < distance) {
								distance = newDist;
								actualX  = tempX;
								actualY  = tempY;
								actualZ  = tempZ;
								axis     = riv % 2;
							}
						}
					}
				}
			}
		}

		int finalX = actualX;
		int finalY = actualY;
		int finalZ = actualZ;
		int coEff1 = axis % 2;
		int coEff2 = 1 - coEff1;

		if(axis % 4 >= 2) {
			coEff1 = -coEff1;
			coEff2 = -coEff2;
		}

		// If still no place is found, create an obsidian
		// platform in the void to place the portal onto
		clearBlockLocations();
		if(distance < 0.0D) {
			actualY = Math.min(Math.max(actualY, 70), worldHeight - 10);
			finalY  = actualY;

			for(int safeBeforeAfter = -1; safeBeforeAfter <= 1;
				++safeBeforeAfter) {
				for(int safeWidth = 0; safeWidth < 2; ++safeWidth) {
					for(int height = -1; height < 3; ++height) {
						int curX = finalX + safeWidth * coEff1
								   + safeBeforeAfter * coEff2;
						int curY = finalY + height;
						int curZ = finalZ + safeWidth * coEff2
								   - safeBeforeAfter * coEff1;
						Block block
							= new Location(world, curX, curY, curZ).getBlock();
						block.setType(height < 0
										  ? XMaterial.OBSIDIAN.parseMaterial()
										  : XMaterial.AIR.parseMaterial());
						addBlockLocation(block, 0);
					}
				}
			}
		}

		int facing = (coEff1 == 0 ? 2 : 0);
		for(int width = -1; width < 3; ++width) {
			for(int height = -1; height < 4; ++height) {
				int curX    = finalX + width * coEff1;
				int curY    = finalY + height;
				int curZ    = finalZ + width * coEff2;
				Block block = new Location(world, curX, curY, curZ).getBlock();
				if(width == -1 || width == 2 || height == -1 || height == 3) {
					block.setType(XMaterial.OBSIDIAN.parseMaterial(), false);
				} else {
					block.setType(
						XMaterial.NETHER_PORTAL.parseMaterial(), false);

					XBlock.setOrient(block, facing);

					portalLocations.add(block.getLocation());
				}
				addBlockLocation(block, facing);
			}
		}

		return true;
	}
}