package net.crazysnailboy.mods.chattime;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.config.Configuration;

@Mod(modid = ChatTimeMod.MODID, name = ChatTimeMod.NAME, version = ChatTimeMod.VERSION, acceptableRemoteVersions = "*")
public class ChatTimeMod
{

	public static final String MODID = "timeinchat";
	public static final String NAME = "Time in Chat Mod";
	public static final String VERSION = "1.0";

	private static Logger LOGGER = LogManager.getLogger(MODID);

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static ScheduledFuture<?> handle;

	private static boolean getTimeFromInternet = true;
	private static String dateTimeFormat = "HH:mm";
	private static String messageText = '\u00A7' + "7The time now is " + "\u00A7" + "f%s"; // '\u00A7' is the unicode section character used for Minecraft formatting codes
	private static int frequencySeconds = 900;


	@EventHandler
	public static void preInit(FMLPreInitializationEvent event)
	{
		// load the configuration options from a file, if present
		File configFile = new File(Loader.instance().getConfigDir(), ChatTimeMod.MODID + ".cfg");
		Configuration config = new Configuration(configFile);
		config.load();

		// set the variables to the configuration values
		getTimeFromInternet = config.get(Configuration.CATEGORY_GENERAL, "getTimeFromInternet", getTimeFromInternet).getBoolean();
		dateTimeFormat = config.get(Configuration.CATEGORY_GENERAL, "dateTimeFormat", dateTimeFormat).getString();
		messageText = config.get(Configuration.CATEGORY_GENERAL, "messageText", messageText).getString();
		frequencySeconds = config.get(Configuration.CATEGORY_GENERAL, "frequencySeconds", frequencySeconds).getInt();

		// save the configuration if it's changed
		if (config.hasChanged()) config.save();
	}


	@EventHandler
	public static void onServerStarted(FMLServerStartedEvent event)
	{
		// get the current time in milliseconds, and round up to the next interval
		long ms1 = getCurrentTime().getTime();
		long ms2 = (long)(Math.ceil( (double)( (double)ms1/(double)(frequencySeconds * 1000) ) ) * (frequencySeconds * 1000));
		// calculate how long we should delay before starting the scheduler
		long initialDelay = (ms2 - ms1) / 1000;

		// schedule a task to execute every interval, waiting the prerequisite time before starting
		handle = scheduler.scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				// create a chat message displaying the current time
				ChatComponentText message = new ChatComponentText(String.format(messageText, new SimpleDateFormat(dateTimeFormat).format(getCurrentTime())));
				// send the chat message to all players on the server
				List<EntityPlayer> players = Lists.newArrayList(Iterables.filter( FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().playerEntityList , EntityPlayer.class));
				for (EntityPlayer player : players )
				{
					player.addChatMessage(message);
				}
			}
		},
		initialDelay, frequencySeconds, TimeUnit.SECONDS);
	}

	@EventHandler
	public static void onServerStopped(FMLServerStoppedEvent event)
	{
		// stop the scheduler
		scheduler.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				handle.cancel(true);
			}

		}, 0, TimeUnit.SECONDS);
	}


	private static Date getCurrentTime()
	{
		// get the current server date & time
		Date date = new Date();
		// if we want to use internet time instead of the server time...
		if (getTimeFromInternet)
		{
			InputStream inputStream = null;
			try
			{
				// make a call to the url and get the response json into a string
				inputStream = new URL("https://script.google.com/macros/s/AKfycbyd5AcbAnWi2Yn0xhFRbyzS4qMq1VucMVgVvhul5XqS9HkAyJY/exec").openStream(); // http://davidayala.eu/current-time/
				String jsonText = IOUtils.toString(inputStream, Charset.defaultCharset());

				// extract the fulldate field from the json into a string
				JsonObject jsonObject = new JsonParser().parse(jsonText).getAsJsonObject();
				String fulldate = jsonObject.get("fulldate").getAsString(); // e.g. "Tue, 25 Apr 2017 22:20:08 +0000"

				// parse the string into a date object
				DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
				date = df.parse(fulldate);

			}
			catch (Exception ex) { LOGGER.catching(ex); }
			finally { IOUtils.closeQuietly(inputStream); }
		}
		// return the date & time
		return date;
	}

}