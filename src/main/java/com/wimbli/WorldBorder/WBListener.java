package com.wimbli.WorldBorder;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.Location;
import org.bukkit.entity.Player;


public class WBListener implements Listener
{
	final static String PORTAL_EVENT_META = "WorldBorder-portal";

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerTeleport(PlayerTeleportEvent event)
	{
		// if knockback is set to 0, simply return
		if (Config.KnockBack() == 0.0)
			return;

		if (Config.Debug())
			Config.log("Teleport cause: " + event.getCause().toString());

		// Ignore nonsensical teleport events.
		// https://hub.spigotmc.org/jira/browse/SPIGOT-5252
		Player player = event.getPlayer();
		TeleportCause cause = event.getCause();
		if (cause == TeleportCause.NETHER_PORTAL || cause == TeleportCause.END_PORTAL) {
			// Store event details of the original correct event. TeleportCause.UNKNOWN comes after.
			player.setMetadata(PORTAL_EVENT_META, new FixedMetadataValue(WorldBorder.plugin, event));
			Bukkit.getScheduler().runTaskLater(WorldBorder.plugin, () -> {
				player.removeMetadata(PORTAL_EVENT_META, WorldBorder.plugin);
				//WorldBorder.plugin.getLogger().info("Removed " + player.getName() + " portal meta.");
			}, 0);
		} else if (cause == TeleportCause.UNKNOWN) {
			List<MetadataValue> metas = player.getMetadata(PORTAL_EVENT_META);
			if (metas != null && !metas.isEmpty()) {
				PlayerTeleportEvent savedEvent = (PlayerTeleportEvent) metas.get(0).value();
				if (savedEvent.getFrom().getWorld().equals(event.getFrom().getWorld())) {
					//WorldBorder.plugin.getLogger().info("Ignoring nonsensical teleport by " + player.getName() +
					//            " in " + savedEvent.getFrom().getWorld().getName() + ".");
					return;
				}
			}
		}

		Location newLoc = BorderCheckTask.checkPlayer(player, event.getTo(), true, true);
		if (newLoc != null)
		{
			if(cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && Config.getDenyEnderpearl())
			{
				event.setCancelled(true);
				return;
			}

			event.setTo(newLoc);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerPortal(PlayerPortalEvent event)
	{
		// if knockback is set to 0, or portal redirection is disabled, simply return
		if (Config.KnockBack() == 0.0 || !Config.portalRedirection())
			return;

		Location newLoc = BorderCheckTask.checkPlayer(event.getPlayer(), event.getTo(), true, false);
		if (newLoc != null)
			event.setTo(newLoc);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event)
	{
/*		// tested, found to spam pretty rapidly as client repeatedly requests the same chunks since they're not being sent
		// definitely too spammy at only 16 blocks outside border
		// potentially useful at standard 208 block padding as it was triggering only occasionally while trying to get out all along edge of round border, though sometimes up to 3 triggers within a second corresponding to 3 adjacent chunks
		// would of course need to be further worked on to have it only affect chunks outside a border, along with an option somewhere to disable it or even set specified distance outside border for it to take effect; maybe  send client chunk composed entirely of air to shut it up

		// method to prevent new chunks from being generated, core method courtesy of code from NoNewChunk plugin (http://dev.bukkit.org/bukkit-plugins/nonewchunk/)
		if(event.isNewChunk())
		{
			Chunk chunk = event.getChunk();
			chunk.unload(false, false);
			Config.logWarn("New chunk generation has been prevented at X " + chunk.getX() + ", Z " + chunk.getZ());
		}
*/
		// make sure our border monitoring task is still running like it should
		if (Config.isBorderTimerRunning()) return;

		Config.logWarn("Border-checking task was not running! Something on your server apparently killed it. It will now be restarted.");
		Config.StartBorderTimer();
	}

	/*
	 * Check if there is a fill task running, and if yes, if it's for the
	 * world that the unload event refers to, set "force loaded" flag off
	 * and track if chunk was somehow on unload prevention list
	 */
	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent e)
	{
		if (Config.fillTask == null)
			return;

		Chunk chunk = e.getChunk();
		if (e.getWorld() != Config.fillTask.getWorld())
			return;

		// just to be on the safe side, in case it's still set at this point somehow
		chunk.setForceLoaded(false);
	}

}
