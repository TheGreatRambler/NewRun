package challengeserver.tgrcode.challenges;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;

public final class ChallengeCompleteEvent extends Event {
	private static final HandlerList handlers = new HandlerList();
	private Player winningPlayer;
	private Location targetLocation;
	private boolean isCanceled = false;

	public ChallengeCompleteEvent(Player player, Location loc) {
		winningPlayer  = player;
		targetLocation = loc;
	}

	public Player getWinningPlayer() {
		return winningPlayer;
	}

	public Location getTargetLocation() {
		return targetLocation;
	}

	public void setTargetLocation(Location loc) {
		targetLocation = loc;
	}

	public boolean getCanceled() {
		return isCanceled;
	}

	public void setCanceled(boolean canceled) {
		isCanceled = canceled;
	}

	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}