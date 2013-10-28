/*
 * Mapcoords
 * Version 2.1.0
 * Date: Mon Oct 28, 2013 11:02:25 AM
 * 
 * Updated Metrics to latest version
 * Fixes Unknown world bug for 1st new world coordinates save
 * Added ability to do things like mc.add.* and mc.list.*
 * Fixed bug in default permissions that allowed a user to publish coords to public list
 * 
 */

package com.guysthatcode.Mapcoords;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Mapcoords extends JavaPlugin implements Listener {
	
	// Init GLobal File vars
    File configFile, root, users, worlds, counterFile;
    FileConfiguration config, counter;
    
    // Init Global variables
	String world, version, newVersion;
	int world_id, iver, inewver;
	boolean epic_fail, first_run, newUpdate;

    @Override
    public void onEnable() {    	
    	// Load config file
        configFile = new File(getDataFolder(), "config.yml");

        try {
            firstRun();
        } catch (Exception e) {
        	getLogger().severe("\033[1;31mfirstRun configuration failed!\033[0m");
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
        }

        // Load config
        config = new YamlConfiguration();
        loadYamls();
        
        // Update config file if it is missing any values
        boolean restore = false;
        if (config.get("settings.checkForUpdates") == null || (config.getString("settings.checkForUpdates") != "true" && config.getString("settings.checkForUpdates") != "false")) {
            config.set("settings.checkForUpdates", true);
            restore = true;
        }
        if (config.get("settings.permissions") == null || (config.getString("settings.permissions") != "true" && config.getString("settings.permissions") != "false")) {
            config.set("settings.permissions", true);
            restore = true;
        }
        if (config.get("settings.useDatabase") == null || (config.getString("settings.useDatabase") != "true" && config.getString("settings.useDatabase") != "false")) {
            config.set("settings.useDatabase", false);
            restore = true;
        }
        if (config.get("settings.folderName") == null) {
            config.set("settings.folderName", "coords");
            restore = true;
        }
        if (config.get("settings.debug") == null || (config.getString("settings.debug") != "true" && config.getString("settings.debug") != "false")) {
            config.set("settings.debug", false);
            restore = true;
        }
        if (config.get("database.url") == null) {
            config.set("database.url", "jdbc:mysql://localhost:3306/DATABASE");
            restore = true;
        }
        if (config.get("database.table") == null) {
            config.set("database.table", "coords");
            restore = true;
        }
        if (config.get("database.username") == null) {
            config.set("database.username", "user");
            restore = true;
        }
        if (config.get("database.password") == null) {
            config.set("database.password", "pass");
            restore = true;
        }
        saveYamls();
        if (restore) {
            getLogger().info("Restoring corrupt configuration values...");
        }
        
        // Debug Mode On
        if (debug()) {
            getLogger().info("=== \033[1;34mDebug Mode Enabled\033[0m ===\n");
            getLogger().info("--- Settings ---");
            getLogger().info("checkForUpdates: " + settingsColor(config.getBoolean("settings.checkForUpdates")));
            getLogger().info("permissions:     " + settingsColor(config.getBoolean("settings.permissions")));
            getLogger().info("useDatabase:     " + settingsColor(config.getBoolean("settings.useDatabase")));
            getLogger().info("folderName:      " + config.getString("settings.folderName"));
            getLogger().info("debug:           " + settingsColor(config.getBoolean("settings.debug")));
            getLogger().info("--- Database ---");
            getLogger().info("url:             " + config.getString("database.url"));
            getLogger().info("table:           " + config.getString("database.table"));
            getLogger().info("username:        " + config.getString("database.username"));
            getLogger().info("password:        " + config.getString("database.password") + "\n");
        }
        // Start Metrics logging for mcstats
    	try {
    	    Metrics metrics = new Metrics(this);
    	    metrics.start();
    	    getLogger().info("Enabled metrics for MCStats");
    	} catch (IOException e) {
    	    // Failed to submit the stats :-(
    		getLogger().severe("Failed to enable MCStats");
    		if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
    	}
    	
    	// Setup event listener
    	getServer().getPluginManager().registerEvents(this, this);
    	
    	// Use Database as storage
    	if (useDatabase()) {
    		getLogger().info("=== Using \033[1;34mDatabase\033[0m for storage ===");
	    	// Check for JDBC driver
	    	try {
	    	    Class.forName("com.mysql.jdbc.Driver");
	    	} catch (ClassNotFoundException e) {
	    		getLogger().severe("\033[1;31mMySQL Driver failed to load!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
	    	}
	    	
	        // Try to connect to the database. Return success message
	        // on connect and failure message on failure (duh).
	        canConnect();
			
	        if (!epic_fail) {
		        // Attempt to set up table
		        createTable();
				
		        // Attempt to set up table_users
		        createTableUsers();
				
				// Attempt to set up table_worlds
		        createTableWorlds();
	        }
	    // Use flat file
    	} else {
    		getLogger().info("=== Using \033[1;34mFlat Files\033[0m for storage ===");
    		// See if folders exist. If not, create them.
    		createFoldersAndFiles();
    	}
        
        // Check for updates
        if (config.getBoolean("settings.checkForUpdates") == true) {
	        if (isInternetReachable()) {
	        	checkForUpdates();
	        } else {
	        	getLogger().severe("\033[1;31mFailed to contact update server, no internet connection available!\033[0m");
	        }
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    	if (cmd.getName().equalsIgnoreCase("mapcoords")) {
    		
    		// Display valid commands
    		if (args.length == 0 || isNumeric(args[0])) {
    			
    			int page = 1;
    			if (args.length != 0) {
    				page = Integer.parseInt(args[0]);
    			}
    			if (page > 2) {
    				page = 2;
    			}
    			if (page < 1) {
    				page = 1;
    			}
    			boolean console = false;
    			// If typing /mc from Console, display all commands
    			if (!(sender instanceof Player)) {
    				sender.sendMessage(ChatColor.YELLOW + "------- " + ChatColor.WHITE + "Available Mapcoords Commands " + ChatColor.YELLOW + "-------");
    				console = true;
    			} else {
    				sender.sendMessage(ChatColor.YELLOW + "--- " + ChatColor.WHITE + "Available Mapcoords Commands - Page (" + page + "/2) " + ChatColor.YELLOW + "---");
    			}
    			if (page == 1 || console) {
	    			sender.sendMessage(ChatColor.GOLD + "/mc [page]:" + ChatColor.WHITE + " Lists all available commands");
	    			sender.sendMessage(ChatColor.GOLD + "/mc add [name]:" + ChatColor.WHITE + " Adds current location to private list");
	    			sender.sendMessage(ChatColor.GOLD + "/mc addp [name]:" + ChatColor.WHITE + " Adds current location to public list");
					sender.sendMessage(ChatColor.GOLD + "/mc delete [id]:" + ChatColor.WHITE + " Delete saved location id from database");
					sender.sendMessage(ChatColor.GOLD + "/mc list [page]:" + ChatColor.WHITE + " Lists coordinates from your private list");
					sender.sendMessage(ChatColor.GOLD + "/mc listp [page]:" + ChatColor.WHITE + " Lists coordinates from public list");
					sender.sendMessage(ChatColor.GOLD + "/mc listo [username] [page]:" + ChatColor.WHITE + " Lists another player's coordinates list");
					sender.sendMessage(ChatColor.GOLD + "/mc coords:" + ChatColor.WHITE + " Displays your current coordinates");
    			}
    			if (page == 2 || console) {
					sender.sendMessage(ChatColor.GOLD + "/mc saycoords:" + ChatColor.WHITE + " Says your current coordinates in chat");
					sender.sendMessage(ChatColor.GOLD + "/mc goto [id]:" + ChatColor.WHITE + " Gives directions to a location");
					sender.sendMessage(ChatColor.GOLD + "/mc find [username]:" + ChatColor.WHITE + " Finds a current player's location");
					sender.sendMessage(ChatColor.GOLD + "/mc tp [id]:" + ChatColor.WHITE + " Teleports you to location id");
					sender.sendMessage(ChatColor.GOLD + "/mc setc [id]:" + ChatColor.WHITE + " Set compass to point to location");
					sender.sendMessage(ChatColor.GOLD + "/mc reset:" + ChatColor.WHITE + " Reset compass to point to spawn");
					sender.sendMessage(ChatColor.GOLD + "/mc publish [id]:" + ChatColor.WHITE + " Move a private coordinate to the public list. This can't be undone.");
					sender.sendMessage(ChatColor.GOLD + "/mc rename [id] [name]:" + ChatColor.WHITE + " Rename a location");
    			}
				return true;
    	    }
			
			// Adding coordinates command
			if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("addprivate")) {
				
				// Only players can use this command
				if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
				
				// Check if player is OP or has the proper permission to run command
				if (!sender.hasPermission("mc.add.private") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
					return true;
				}
				
				// Not enough arguments for coordinate
				if (args.length == 1) {
					sender.sendMessage(ChatColor.RED + "Please specify a name for your current location.");
					return true;
				}
				
				// Location information for player 
				Player player = (Player) sender;
			    Location loc = player.getLocation();
			    int x = loc.getBlockX();
			    int y = loc.getBlockY();
			    int z = loc.getBlockZ();
			    world = player.getLocation().getWorld().getName();
			    world_id = stringToWorldID(world);
			    
			    if (useDatabase()) {
				    // Attempt to write to database
					try {
						// Init query variables
						Connection con = connection();
						con.setAutoCommit(false);
	    				
	    				// Insert coordinates into database
	    				PreparedStatement statement = con.prepareStatement("INSERT INTO " + getTable() + " VALUES (?, ?, ?, ?, ?, ?, ?)");
	    				statement.setInt(1, 0);
	    				statement.setInt(2, usernameToUserID(player.getPlayerListName()));
	    				statement.setString(3, args[1]);
	    				statement.setInt(4, x);
	    				statement.setInt(5, y);
	    				statement.setInt(6, z);
	    				statement.setInt(7, world_id);
						statement.executeUpdate();
						con.commit();
						
						statement.close();
						con.close();
	    			} catch (SQLException e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
			    } else {
			    	// Use Flat Files
			    	// Load in counters file for Flat File Storage
			    	loadCounter();
			    	try {
			    		// Create File object of new location with id as name
    					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + counter.get(getFolderName()));
    					location.createNewFile();
        				FileConfiguration locconfig = new YamlConfiguration();
        				try {
        					locconfig.load(location);
        		        } catch (Exception e) {
        		        	if (debug()) {
        		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        		        	}
        		        }
        		        locconfig.set("user", usernameToUserID(player.getPlayerListName()));
        		        locconfig.set("name", args[1]);
        		        locconfig.set("x", x);
        		        locconfig.set("y", y);
        		        locconfig.set("z", z);
        		        locconfig.set("world", world_id);
        		        locconfig.save(location);
        		        
    				} catch(Exception e) {
    					if (debug()) {
    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
    		        	}
    				}
    				
    				// Update counter values
    				updateCounter(1);
			    }
			    
			    // Send message to player that their coordinates have been inserted successfully.
		        sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z + ChatColor.WHITE + " saved as " + ChatColor.GREEN + args[1] + ChatColor.WHITE + " to " + ChatColor.BLUE + "private " + ChatColor.WHITE + "list.");
		        if (debug()) {
	        		getLogger().info("\033[1;34m[Debug] User " + player.getPlayerListName() + "[" + usernameToUserID(player.getPlayerListName()) + "] added location [" + worldIDToString(world_id) + "][" + x + ", " + y + ", " + z + "] to their private list as " + args[1] + ".\033[0m");
	        	}
				return true;
			}
			
			// Adding coordinates command
			else if (args[0].equalsIgnoreCase("addp") || args[0].equalsIgnoreCase("addpublic")) {
					
				// Only players can use this command
				if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
				
				// Check if player is OP or has the proper permission to run command
				if (!sender.hasPermission("mc.add.public") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
					return true;
				}
				
				// Not enough arguments for coordinate
				if (args.length == 1) {
					sender.sendMessage(ChatColor.RED + "Please specify a name for your current location.");
					return true;
				}
				
				// Location information for player 
				Player player = (Player) sender;
			    Location loc = player.getLocation();
			    int x = loc.getBlockX();
			    int y = loc.getBlockY();
			    int z = loc.getBlockZ();
			    world = player.getLocation().getWorld().getName();
			    world_id = stringToWorldID(world);
			    
			    if (useDatabase()) {
				    // Attempt to write to database
					try {
						// Init query variables
						Connection con = connection();
						con.setAutoCommit(false);
	    				
	    				// Insert coordinates into database
	    				PreparedStatement statement = con.prepareStatement("INSERT INTO " + getTable() + " VALUES (?, 2, ?, ?, ?, ?, ?)");
	    				statement.setInt(1, 0);
	    				statement.setString(2, args[1]);
	    				statement.setInt(3, x);
	    				statement.setInt(4, y);
	    				statement.setInt(5, z);
	    				statement.setInt(6, world_id);
						statement.executeUpdate();
						con.commit();
						
						statement.close();
						con.close();
	    			} catch (SQLException e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
			    } else {
			    	// Use Flat Files
			    	// Load in counters file for Flat File Storage
			    	loadCounter();
			    	try {
			    		// Create File object of new location with id as name
    					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + counter.get(getFolderName()));
    					location.createNewFile();
        				FileConfiguration locconfig = new YamlConfiguration();
        				try {
        					locconfig.load(location);
        		        } catch (Exception e) {
        		        	if (debug()) {
        		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        		        	}
        		        }
        		        locconfig.set("user", 2);
        		        locconfig.set("name", args[1]);
        		        locconfig.set("x", x);
        		        locconfig.set("y", y);
        		        locconfig.set("z", z);
        		        locconfig.set("world", world_id);
        		        locconfig.save(location);
        		        
    				} catch(Exception e) {
    					if (debug()) {
    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
    		        	}
    				}
    				
    				// Update counter values
    				updateCounter(1);
			    }
			    
			    // Send message to player that their coordinates have been inserted successfully.
		        sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z + ChatColor.WHITE + " saved as " + ChatColor.GREEN + args[1] + ChatColor.WHITE + " to" + ChatColor.DARK_RED + " public " + ChatColor.WHITE + "list.");
		        if (debug()) {
	        		getLogger().info("\033[1;34m[Debug] User " + player.getPlayerListName() + "[" + usernameToUserID(player.getPlayerListName()) + "] added location [" + worldIDToString(world_id) + "][" + x + ", " + y + ", " + z + "] to the public list as " + args[1] + ".\033[0m");
	        	}
				return true;
			}
			
			// Lists map coordinates that have been added in the database
			else if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("listprivate")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console.
				if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.list.private") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				} else {
						sender.sendMessage("This command can only be run by a player.");
						return true;
				}
				
				if (useDatabase()) {
					// Try retrieving coordinates from database
					Player player = (Player) sender;
	    			try {
	    				Connection con = connection();
	    				con.setAutoCommit(false);
	    				
	    				// Count number of coordinates in database
	    				Statement stnum = con.createStatement();
						String querynum = "SELECT COUNT(id) FROM " + getTable() + " WHERE user = " + usernameToUserID(player.getPlayerListName());
						ResultSet resultnum = stnum.executeQuery(querynum);
						con.commit();
						resultnum.next();
						int totalPages = (int) Math.ceil(resultnum.getInt("COUNT(id)")/9.0), currentPage;
						if (args.length == 1 || !isNumeric(args[1])) {
							currentPage = 1;
						} else {
							currentPage = Integer.parseInt(args[1]);
						}
						if (currentPage > totalPages) {
							currentPage = totalPages;
						}
						if (currentPage < 1) {
							currentPage = 1;
						}
						if (totalPages == 0) {
							totalPages = 1;
						}
						
	    				Statement st = con.createStatement();
						String query = "SELECT id, user, name, x, y, z, world FROM " + getTable() + " WHERE user = " + usernameToUserID(player.getPlayerListName()) + " ORDER BY id DESC LIMIT " + (currentPage-1)*9 + ",9";
						ResultSet result = st.executeQuery(query);
						con.commit();
						sender.sendMessage(ChatColor.YELLOW + "---" + ChatColor.WHITE + " Listing" + ChatColor.BLUE + " private " + ChatColor.WHITE + "coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + resultnum.getInt("COUNT(id)") + ")" + ChatColor.YELLOW + " ---");
						
						// Output all records in database
						int a = 0;
						while (result.next()) {
							a++;
							sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + result.getInt("id") + ChatColor.WHITE + "] - " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE);
						}
						
						// No coordinates if a is still 0
						if (a == 0) {
							sender.sendMessage(ChatColor.GOLD + "You haven't added any coordinates yet!");
						}
						st.close();
						resultnum.close();
						result.close();
						con.close();
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
				} else {
					// Use Flat Files
					Player player = (Player) sender;
					
					// Load in counters file for Flat File Storage
			    	loadCounter();
			    	
			    	ArrayList<String> records = new ArrayList<String>();

			    	int b = 0;
			    	for (int a = 1; a < counter.getInt(getFolderName()); a++) {
			    		try {
				    		// Create File object of new location with id as name
	    					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + a);
	        				FileConfiguration locconfig = new YamlConfiguration();
	        				try {
	        					locconfig.load(location);
	        		        } catch (Exception e) {
	        		        	
	        		        }
	        		        
	        		        // Save everything if there is a match into an arraylist
	        		        if (locconfig.getInt("user") == usernameToUserID(player.getPlayerListName())) {
	        		        	records.add("[" + ChatColor.LIGHT_PURPLE + worldIDToString(locconfig.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + a + ChatColor.WHITE + "] - " + ChatColor.GREEN + locconfig.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + locconfig.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + locconfig.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + locconfig.getInt("z") + ChatColor.WHITE);
	        		        	b++;
	        		        }
	        		        
	    				} catch(Exception e) {
	    					if (debug()) {
	    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    		        	}
	    				}
			    	}
			    	
			    	int totalPages = (int) Math.ceil((double)records.size()/9.0), currentPage;
					if (args.length == 1 || !isNumeric(args[1])) {
						currentPage = 1;
					} else {
						currentPage = Integer.parseInt(args[1]);
					}
					if (currentPage > totalPages) {
						currentPage = totalPages;
					}
					if (currentPage < 1) {
						currentPage = 1;
					}
					if (totalPages == 0) {
						totalPages = 1;
					}
			    	
					Collections.reverse(records);
					
					int start = (currentPage-1)*9;
					sender.sendMessage(ChatColor.YELLOW + "---" + ChatColor.WHITE + " Listing" + ChatColor.BLUE + " private " + ChatColor.WHITE + "coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + records.size() + ")" + ChatColor.YELLOW + " ---");
			    	for (int a = start; a < (start+9); a++) {
			    		try {
			    			sender.sendMessage(records.get(a).toString());
			    		} catch (Exception e) {
			    			
			    		}
			    	}
			    	// No coordinates if b is still 0
					if (b == 0) {
						sender.sendMessage(ChatColor.GOLD + "You haven't added any coordinates yet!");
					}
				}
    			return true;
    		}
			
			// Lists map coordinates that have been added in the database
			else if (args[0].equalsIgnoreCase("listpublic") || args[0].equalsIgnoreCase("listp")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console. [PUBLIC LIST]
				if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.list.public") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				}
				if (useDatabase()) {
					// Try retrieve coordinates from database
	    			try {
	    				Connection con = connection();
	    				con.setAutoCommit(false);
	    				
					    // Count number of coordinates in database
	    				Statement stnum = con.createStatement();
						String querynum = "SELECT COUNT(id) FROM " + getTable() + " WHERE user = 2";
						ResultSet resultnum = stnum.executeQuery(querynum);
						con.commit();
						resultnum.next();
						int totalPages = (int) Math.ceil(resultnum.getInt("COUNT(id)")/9.0), currentPage;
						if (args.length == 1 || !isNumeric(args[1])) {
							currentPage = 1;
						} else {
							currentPage = Integer.parseInt(args[1]);
						}
						if (currentPage > totalPages) {
							currentPage = totalPages;
						}
						if (currentPage < 1) {
							currentPage = 1;
						}
						if (totalPages == 0) {
							totalPages = 1;
						}
						
	    				Statement st = con.createStatement();
						String query = "SELECT id, user, name, x, y, z, world FROM " + getTable() + " WHERE user = 2 ORDER BY id DESC LIMIT " + (currentPage-1)*9 + ",9";
						ResultSet result = st.executeQuery(query);
						con.commit();
						sender.sendMessage(ChatColor.YELLOW + "---" + ChatColor.WHITE + " Listing" + ChatColor.DARK_RED + " public " + ChatColor.WHITE + "coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + resultnum.getInt("COUNT(id)") + ")" + ChatColor.YELLOW + " ---");
	
						// Output all records in database
						int a = 0;
						while (result.next()) {
							a++;
							sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + result.getInt("id") + ChatColor.WHITE + "] - " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE);
						}
						
						// No coordinates if a is still 0
						if (a == 0) {
							sender.sendMessage(ChatColor.GOLD + "No public coordinates have been added yet!");
						}
						st.close();
						resultnum.close();
						result.close();
						con.close();
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
				} else {
					// Use Flat Files
					// Load in counters file for Flat File Storage
			    	loadCounter();
			    	
			    	ArrayList<String> records = new ArrayList<String>();

			    	int b = 0;
			    	for (int a = 1; a < counter.getInt(getFolderName()); a++) {
			    		try {
				    		// Create File object of new location with id as name
	    					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + a);
	        				FileConfiguration locconfig = new YamlConfiguration();
	        				try {
	        					locconfig.load(location);
	        		        } catch (Exception e) {
	        		        	
	        		        }
	        		        
	        		        // Save everything if there is a match into an arraylist
	        		        if (locconfig.getInt("user") == 2) {
	        		        	records.add("[" + ChatColor.LIGHT_PURPLE + worldIDToString(locconfig.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + a + ChatColor.WHITE + "] - " + ChatColor.GREEN + locconfig.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + locconfig.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + locconfig.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + locconfig.getInt("z") + ChatColor.WHITE);
	        		        	b++;
	        		        }
	        		        
	    				} catch(Exception e) {
	    					if (debug()) {
	    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    		        	}
	    				}
			    	}
			    	
			    	int totalPages = (int) Math.ceil((double)records.size()/9.0), currentPage;
					if (args.length == 1 || !isNumeric(args[1])) {
						currentPage = 1;
					} else {
						currentPage = Integer.parseInt(args[1]);
					}
					if (currentPage > totalPages) {
						currentPage = totalPages;
					}
					if (currentPage < 1) {
						currentPage = 1;
					}
					if (totalPages == 0) {
						totalPages = 1;
					}
			    	
					Collections.reverse(records);
					
					int start = (currentPage-1)*9;
					sender.sendMessage(ChatColor.YELLOW + "---" + ChatColor.WHITE + " Listing" + ChatColor.DARK_RED + " public " + ChatColor.WHITE + "coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + records.size() + ")" + ChatColor.YELLOW + " ---");
			    	for (int a = start; a < (start+9); a++) {
			    		try {
			    			sender.sendMessage(records.get(a).toString());
			    		} catch (Exception e) {
			    			
			    		}
			    	}
			    	// No coordinates if b is still 0
					if (b == 0) {
						sender.sendMessage(ChatColor.GOLD + "No public coordinates have been added yet!");
					}
				}
    			return true;
    		}
			
			// Lists map coordinates of another player
			else if (args[0].equalsIgnoreCase("listother") || args[0].equalsIgnoreCase("listo")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console. [PUBLIC LIST]
				if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.list.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				}
				
				if (args.length == 1) {
					sender.sendMessage(ChatColor.RED + "You must specify a player's name.");
					return true;
				}
				
				if (!doesUsernameExist(args[1])) {
					sender.sendMessage(ChatColor.RED + args[1] + " does not have any saved coordinates.");
					return true;
				}
				
				if (sender instanceof Player) {
					Player perms = (Player) sender;
					if (perms.getPlayerListName().equalsIgnoreCase(args[1]) && !sender.hasPermission("mc.list.private")) {
						sender.sendMessage(ChatColor.RED + "You don't have permission to view this player's coordinates.");
						return true;
					}
				}
				
				if (useDatabase()) {
					// Try retrieve coordinates from database
	    			try {
	    				Connection con = connection();
	    				con.setAutoCommit(false);
	    				
					    // Count number of coordinates in database
	    				Statement stnum = con.createStatement();
						String querynum = "SELECT COUNT(id) FROM " + getTable() + " WHERE user = " + usernameToUserID(args[1]);
						ResultSet resultnum = stnum.executeQuery(querynum);
						con.commit();
						resultnum.next();
						int totalPages = (int) Math.ceil(resultnum.getInt("COUNT(id)")/9.0), currentPage;
						if (args.length == 2 || !isNumeric(args[1])) {
							currentPage = 1;
						} else {
							currentPage = Integer.parseInt(args[2]);
						}
						if (currentPage > totalPages) {
							currentPage = totalPages;
						}
						if (currentPage < 1) {
							currentPage = 1;
						}
						if (totalPages == 0) {
							totalPages = 1;
						}
						
	    				Statement st = con.createStatement();
						String query = "SELECT id, user, name, x, y, z, world FROM " + getTable() + " WHERE user = " + usernameToUserID(args[1]) + " ORDER BY id DESC LIMIT " + (currentPage-1)*9 + ",9";
						ResultSet result = st.executeQuery(query);
						con.commit();
						sender.sendMessage(ChatColor.YELLOW + "-" + ChatColor.WHITE + " Listing " + ChatColor.AQUA + args[1] + ChatColor.WHITE + "'s coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + resultnum.getInt("COUNT(id)") + ")" + ChatColor.YELLOW + " -");
	
						// Output all records in database
						int a = 0;
						while (result.next()) {
							a++;
							sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + result.getInt("id") + ChatColor.WHITE + "] - " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE);
						}
						
						// No coordinates if a is still 0
						if (a == 0) {
							sender.sendMessage(ChatColor.GOLD + args[1] + " doesn't have any saved coordinates!");
						}
						st.close();
						resultnum.close();
						result.close();
						con.close();
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
				} else {
					// Use Flat Files
					// Load in counters file for Flat File Storage
			    	loadCounter();
			    	
			    	ArrayList<String> records = new ArrayList<String>();

			    	int b = 0;
			    	for (int a = 1; a < counter.getInt(getFolderName()); a++) {
			    		try {
				    		// Create File object of new location with id as name
	    					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + a);
	        				FileConfiguration locconfig = new YamlConfiguration();
	        				try {
	        					locconfig.load(location);
	        		        } catch (Exception e) {
	        		        	
	        		        }
	        		        
	        		        // Save everything if there is a match into an arraylist
	        		        if (locconfig.getInt("user") == usernameToUserID(args[1])) {
	        		        	records.add("[" + ChatColor.LIGHT_PURPLE + worldIDToString(locconfig.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + a + ChatColor.WHITE + "] - " + ChatColor.GREEN + locconfig.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + locconfig.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + locconfig.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + locconfig.getInt("z") + ChatColor.WHITE);
	        		        	b++;
	        		        }
	        		        
	    				} catch(Exception e) {
	    					if (debug()) {
	    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    		        	}
	    				}
			    	}
			    	
			    	int totalPages = (int) Math.ceil((double)records.size()/9.0), currentPage;
					if (args.length == 1 || !isNumeric(args[1])) {
						currentPage = 1;
					} else {
						currentPage = Integer.parseInt(args[1]);
					}
					if (currentPage > totalPages) {
						currentPage = totalPages;
					}
					if (currentPage < 1) {
						currentPage = 1;
					}
					if (totalPages == 0) {
						totalPages = 1;
					}
			    	
					Collections.reverse(records);
					
					int start = (currentPage-1)*9;
					sender.sendMessage(ChatColor.YELLOW + "-" + ChatColor.WHITE + " Listing " + ChatColor.AQUA + args[1] + ChatColor.WHITE + "'s coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + records.size() + ")" + ChatColor.YELLOW + " -");
			    	for (int a = start; a < (start+9); a++) {
			    		try {
			    			sender.sendMessage(records.get(a).toString());
			    		} catch (Exception e) {
			    			
			    		}
			    	}
			    	// No coordinates if b is still 0
					if (b == 0) {
						sender.sendMessage(ChatColor.GOLD + args[1] + " doesn't have any saved coordinates!");
					}
				}
    			return true;
    		}
			
			// Delete coordinates command
    		else if (args[0].equalsIgnoreCase("delete")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					if (!sender.hasPermission("mc.delete.public") && !sender.hasPermission("mc.delete.private") && !sender.hasPermission("mc.delete.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
    			}
				
				// Not enough arguments for coordinate
				if (args.length == 1) {
					sender.sendMessage(ChatColor.RED + "Please specify ID number of location to delete.");
					return true;
				}
				
				// Is it a number?
				if (!isNumeric(args[1])) {
					sender.sendMessage(ChatColor.RED + "Location must be an id.");
					return true;
				}
				
				// Array for available IDs that user can delete
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			
    			if (useDatabase()) {
    				// Use Database - Gathers arraylist of ids user can choose from based on the permission delete.
	    			validIDs(sender, ids, "delete");
	    			
	    			// If id not found in ArrayList, then the ID doesn't exist.
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
	    				sender.sendMessage(ChatColor.RED + "Unknown Location ID!");
	    				return true;
	    			}
	    			
					try {
						Connection con = connection();
						con.setAutoCommit(false);
						
	    				PreparedStatement statement = con.prepareStatement("DELETE FROM " + getTable() + " WHERE id=?");
	    				statement.setInt(1, Integer.parseInt(args[1]));
						statement.executeUpdate();
						con.commit();
						
						sender.sendMessage(ChatColor.GOLD + "Location was deleted successfully!");
						statement.close();
						con.close();
	    			} catch (SQLException e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			} catch (IndexOutOfBoundsException e) {
	    				sender.sendMessage("There are no recorded coordinates to delete.");
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
    			} else {
    				// Use Flat Files
    				validIDsFF(sender, ids, "delete");
        			
        			// If id not found in ArrayList, then the ID doesn't exist.
        			if (!ids.contains(Integer.parseInt(args[1]))) {
        				sender.sendMessage(ChatColor.RED + "Unknown Location ID!");
        				return true;
        			}
        			
    				try {
    					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + Integer.parseInt(args[1]));
    					if (location.delete()) {
    						sender.sendMessage(ChatColor.GOLD + "Location was deleted successfully!");
    					} else {
    	    				sender.sendMessage(ChatColor.RED + "Unknown Location ID!");
    					}
        			} catch (Exception e) {
        				if (debug()) {
    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
    		        	}
        			}
    			}
				return true;
			}
			
			// Displays player's current coordinates
    		else if (args[0].equalsIgnoreCase("coords")) {
    			
    			if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			// Check if player is OP or has the proper permission to run command
				if (!sender.hasPermission("mc.coords") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
					return true;
				}
				
    			Player player = (Player) sender;
    		    Location loc = player.getLocation();
    		    int x = loc.getBlockX();
    		    int y = loc.getBlockY();
    		    int z = loc.getBlockZ();
    		    world = player.getLocation().getWorld().getName();
    		    world_id = stringToWorldID(world);
				sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
				return true;
			}
			
			// Player says their current coordinates
    		else if (args[0].equalsIgnoreCase("saycoords")) {
    			
    			if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			// Check if player is OP or has the proper permission to run command
				if (!sender.hasPermission("mc.saycoords") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
					return true;
				}
				
    			Player player = (Player) sender;
    		    Location loc = player.getLocation();
    		    int x = loc.getBlockX();
    		    int y = loc.getBlockY();
    		    int z = loc.getBlockZ();
    		    world = player.getLocation().getWorld().getName();
    		    world_id = stringToWorldID(world);
				player.chat("[" + ChatColor.LIGHT_PURPLE + worldIDToString(stringToWorldID(world)) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
				return true;
			}
    		
    		else if (args[0].equalsIgnoreCase("perms")) {
                
                if (!(sender instanceof Player)) {
                                sender.sendMessage("This command can only be run by a player.");
                                return true;
                        }
                // Check if player is OP or has the proper permission to run command
                Player perms = (Player) sender;
                        /*
                		if (!perms.hasPermission("mc.perms") && config.getBoolean("settings.permissions")) {
                                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                                return true;
                        }
                        */
                        // Output permission privileges
                        sender.sendMessage("Listing permssions that you have");
                        sender.sendMessage(permsColor(perms, "mc.add.public") + " | " + permsColor(perms, "mc.add.private"));
                        sender.sendMessage(permsColor(perms, "mc.list.public") + " | " + permsColor(perms, "mc.list.private") + " | " + permsColor(perms, "mc.list.other"));
                        sender.sendMessage(permsColor(perms, "mc.delete.public") + " | " + permsColor(perms, "mc.delete.private") + " | " + permsColor(perms, "mc.delete.other"));
                        sender.sendMessage(permsColor(perms, "mc.coords") + " | " + permsColor(perms, "mc.saycoords"));
                        sender.sendMessage(permsColor(perms, "mc.find"));
                        sender.sendMessage(permsColor(perms, "mc.goto.public") + " | " + permsColor(perms, "mc.goto.private") + " | " + permsColor(perms, "mc.goto.other"));
                        sender.sendMessage(permsColor(perms, "mc.tp.public") + " | " + permsColor(perms, "mc.tp.private") + " | " + permsColor(perms, "mc.tp.other"));
                        sender.sendMessage(permsColor(perms, "mc.compass.public") + " | " + permsColor(perms, "mc.compass.private") + " | " + permsColor(perms, "mc.compass.other") + " | " + permsColor(perms, "mc.compass.reset"));
                        sender.sendMessage(permsColor(perms, "mc.publish.private") + " | " + permsColor(perms, "mc.publish.other"));
                        sender.sendMessage(permsColor(perms, "mc.rename.public") + " | " + permsColor(perms, "mc.rename.private") + " | " + permsColor(perms, "mc.rename.other"));





                        
                        return true;
                }
			
    		// Finds Coordinates of other players
    		else if (args[0].equalsIgnoreCase("find")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.find") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				}
    			
    			if (args.length == 1) {
    				sender.sendMessage(ChatColor.RED + "You must specify a player.");
					return true;
    			}
				try {
					Player search = Bukkit.getPlayer(args[1]);
					Location loc = search.getLocation();
	    		    int x = loc.getBlockX();
	    		    int y = loc.getBlockY();
	    		    int z = loc.getBlockZ();
	    		    world = search.getLocation().getWorld().getName();
	    		    world_id = stringToWorldID(world);
	    		    
	    		    sender.sendMessage(ChatColor.AQUA + args[1] + ChatColor.WHITE + " is at [" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
	    		    return true;
				} catch (Exception e) {
					if (args[1].equalsIgnoreCase("Herobrine")) {
						sender.sendMessage(ChatColor.RED + args[1] + " isn't online...or is he?");
					} else {
						sender.sendMessage(ChatColor.RED + args[1] + " isn't online.");
					}
					if (debug()) {
		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
		        	}
					return true;
				}
				
			}
    		
			// Directions to coordinates
    		else if (args[0].equalsIgnoreCase("goto")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.goto.public") && !sender.hasPermission("mc.goto.private") && !sender.hasPermission("mc.goto.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				} else {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			
    			if (args.length == 1) {
    				sender.sendMessage(ChatColor.RED + "You must specify a location id.");
					return true;
    			}
    			
    			// Array for available IDs that user can goto
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			if (useDatabase()) {
    				// Use Database - Gathers arraylist of ids user can choose from based on the permission goto.
	    			validIDs(sender, ids, "goto");
	    			
    			} else {
    				// Use Flat Files
    				validIDsFF(sender, ids, "goto");
    			}
    			// Now that we have all available ids that user can goto, try to calculate route.
    			
    			// Does the player have permission to goto this location?
    			if (!isNumeric(args[1])) {
    				sender.sendMessage(ChatColor.RED + "Location must be an id.");
    				return true;
    			}
    			if (!ids.contains(Integer.parseInt(args[1]))) {
					sender.sendMessage(ChatColor.RED + "Location does not exist!");
					return true;
    			}
    			
    			// Get the player's current location
    			Player player = (Player) sender;
    			Location loc = player.getLocation();
    		    int x = loc.getBlockX();
    		    int y = loc.getBlockY();
    		    int z = loc.getBlockZ();
    		    world = player.getLocation().getWorld().getName();
    		    world_id = stringToWorldID(world);
    			int rx = 0, ry = 0, rz = 0, rworld = 0;
    			String rname = null;
    			
    			if (useDatabase()) {
					try {
		    		    Connection con = connection();
		    	        con.setAutoCommit(false);
		    	        
		    	        Statement st = con.createStatement();
		    	        
		    			String query = "SELECT * FROM " + getTable() + " WHERE id = " + args[1];
		    			ResultSet result = st.executeQuery(query);
		    			con.commit();
		    			result.next();
		    			rname = result.getString("name");
		    			rx = result.getInt("x");
		    			ry = result.getInt("y");
		    			rz = result.getInt("z");
		    			rworld = result.getInt("world");
		    			result.close();
						st.close();
						con.close();
					} catch (Exception e) {
						
					}
    			} else {
    				// Use Flat Files
    				File locFile = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + args[1]);
			    	FileConfiguration locconfig = new YamlConfiguration();
			    	try {
			    		locconfig.load(locFile);
			    		rname = locconfig.getString("name");
			    		rx = locconfig.getInt("x");
		    			ry = locconfig.getInt("y");
		    			rz = locconfig.getInt("z");
		    			rworld = locconfig.getInt("world");
			    	} catch (Exception e) {
			    		if (debug()) {
			        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
			        	}
			    	}
    			}
						
    			sender.sendMessage(ChatColor.YELLOW + "---- " + ChatColor.WHITE + "Now calculating route to " + ChatColor.GOLD + rname + ChatColor.YELLOW + " ----");
    			sender.sendMessage("Current Coords    = X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    			sender.sendMessage("Destination Coords = X: " + ChatColor.GOLD + rx + ChatColor.WHITE + " Y: " + ChatColor.GOLD + ry + ChatColor.WHITE + " Z: " + ChatColor.GOLD + rz);
    			// A^2 + B^2 = C^2 (WOW! The FIRST time I have ever had to use actual formulas in a COMPUTER SCIENCE program. No derivatives yet but they are BOUND to be here soon!!!11!!)
    			if (world_id != rworld) {
    				sender.sendMessage(ChatColor.RED + "The location you want to go to is literally out of this world. Go to " + worldIDToString(rworld) + " first.");
    				return true;
    			}
    			double distance = Math.floor(Math.sqrt(Math.pow(Math.abs(y - ry), 2.0)+Math.pow(Math.sqrt(Math.pow(Math.abs(x - rx), 2.0) + Math.pow(Math.abs(z - rz), 2.0)), 2.0))*100+0.5)/100;
    			
    			sender.sendMessage("Distance: " + ChatColor.GREEN + distance + ChatColor.WHITE + " meters");
    			
    			double walk_h, walk_m, walk_s, walk_raw;
    			double sprint_h, sprint_m, sprint_s, sprint_raw;
                double fly_h, fly_m, fly_s, fly_raw;
    			
    			walk_raw   = (Math.floor((distance/4.3)*100+0.5)/100)+1;
    			sprint_raw = (Math.floor((distance/5.6)*100+0.5)/100)+1;
                fly_raw    = (Math.floor((distance/10.8)*100+0.5)/100)+1;
    			
    			walk_h = TimeUnit.SECONDS.toHours((int)walk_raw);
    			walk_m = TimeUnit.SECONDS.toMinutes((int)walk_raw) - (TimeUnit.SECONDS.toHours((int)walk_raw)* 60);
    			walk_s = TimeUnit.SECONDS.toSeconds((int)walk_raw) - (TimeUnit.SECONDS.toMinutes((int)walk_raw) *60);
    			
    			sprint_h = TimeUnit.SECONDS.toHours((int)sprint_raw);
    			sprint_m = TimeUnit.SECONDS.toMinutes((int)sprint_raw) - (TimeUnit.SECONDS.toHours((int)sprint_raw)* 60);
    			sprint_s = TimeUnit.SECONDS.toSeconds((int)sprint_raw) - (TimeUnit.SECONDS.toMinutes((int)sprint_raw) *60);

                fly_h = TimeUnit.SECONDS.toHours((int)fly_raw);
                fly_m = TimeUnit.SECONDS.toMinutes((int)fly_raw) - (TimeUnit.SECONDS.toHours((int)fly_raw)* 60);
                fly_s = TimeUnit.SECONDS.toSeconds((int)fly_raw) - (TimeUnit.SECONDS.toMinutes((int)fly_raw) *60);
    			
    			sender.sendMessage(ChatColor.YELLOW + "------ " + ChatColor.WHITE + "ETAs (Not including obstacles)" + ChatColor.YELLOW + " ------");
    			
    			String walk_message = "Walking: ";
    			if (walk_h != 0) {
    				if (walk_h == 1) {
    					walk_message = walk_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " hour ";
    				} else {
    					walk_message = walk_message + ChatColor.GOLD + (int)walk_h + ChatColor.WHITE + " hours ";
    				}
    			}
    			if (walk_m != 0) {
    				if (walk_m == 1) {
    					walk_message = walk_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " minute ";
    				} else {
    					walk_message = walk_message + ChatColor.GOLD + (int)walk_m + ChatColor.WHITE + " minutes ";
    				}
    			}
    			if (walk_s != 0) {
    				if (walk_s == 1) {
    					walk_message = walk_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " second";
    				} else {
    					walk_message = walk_message + ChatColor.GOLD + (int)walk_s + ChatColor.WHITE + " seconds";
    				}
    			}
    			if (distance >=4.3) {
    				sender.sendMessage(walk_message);
    			} else {
    				sender.sendMessage("Walking: " + ChatColor.GOLD + "0 " + ChatColor.WHITE + "seconds");
    			}
    			String sprint_message = "Sprinting: ";
    			if (sprint_h != 0) {
    				if (sprint_h == 1) {
    					sprint_message = sprint_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " hour ";
    				} else {
    					sprint_message = sprint_message + ChatColor.GOLD + (int)sprint_h + ChatColor.WHITE + " hours ";
    				}
    			}
    			if (sprint_m != 0) {
    				if (sprint_m == 1) {
    					sprint_message = sprint_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " minute ";
    				} else {
    					sprint_message = sprint_message + ChatColor.GOLD + (int)sprint_m + ChatColor.WHITE + " minutes ";
    				}
    			}
    			if (sprint_s != 0) {
    				if (sprint_s == 1) {
    					sprint_message = sprint_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " second";
    				} else {
    					sprint_message = sprint_message + ChatColor.GOLD + (int)sprint_s + ChatColor.WHITE + " seconds";
    				}
    			}
    			if (distance >=5.6) {
    				sender.sendMessage(sprint_message);
    			} else {
    				sender.sendMessage("Sprinting: " + ChatColor.GOLD + "0 " + ChatColor.WHITE + "seconds");
    			}
                String fly_message = "Flying: ";
                if (fly_h != 0) {
                    if (fly_h == 1) {
                        fly_message = fly_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " hour ";
                    } else {
                        fly_message = fly_message + ChatColor.GOLD + (int)fly_h + ChatColor.WHITE + " hours ";
                    }
                }
                if (fly_m != 0) {
                    if (fly_m == 1) {
                        fly_message = fly_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " minute ";
                    } else {
                        fly_message = fly_message + ChatColor.GOLD + (int)fly_m + ChatColor.WHITE + " minutes ";
                    }
                }
                if (fly_s != 0) {
                    if (fly_s == 1) {
                        fly_message = fly_message + ChatColor.GOLD + "1" + ChatColor.WHITE + " second";
                    } else {
                        fly_message = fly_message + ChatColor.GOLD + (int)fly_s + ChatColor.WHITE + " seconds";
                    }
                }
    			if (distance >= 10.8) {
    				sender.sendMessage(fly_message);
    			} else {
    				sender.sendMessage("Flying: " + ChatColor.GOLD + "0 " + ChatColor.WHITE + "seconds");
    			}
    			
    			String heading = null;
    			/* New Direction Finder */
    			int x_diff = Math.abs(x - rx);
    			int z_diff = Math.abs(z - rz);
    			int dest_x = rx;
    			int dest_y = ry;
    			int dest_z = rz;
    			double degree = 0.0;
    			try {
	    			degree = Math.toDegrees((double)Math.atan((double)x_diff/(double)z_diff));
				} catch (Exception ignore) {
	    			degree = Math.toDegrees((double)Math.atan((double)x_diff/(double)z_diff));
				}
				
    			// Quadrant 1
    			if (x >= dest_x && z <= dest_z) {
    				if (degree >= 0 && degree < 22.5) {
    					heading = "south";
    				}
    				else if (degree >= 22.5 && degree < 67.5) {
    					heading = "southwest";
    				}
    				else if (degree >= 67.5 && degree <= 90) {
    					heading = "west";
    				} else {
    					heading = "unknown";
    				}
    			}
    			// Quadrant 2
    			if (x <= dest_x && z <= dest_z) {
    				if (degree >= 0 && degree < 22.5) {
    					heading = "south";
    				}
    				else if (degree >= 22.5 && degree < 67.5) {
    					heading = "southeast";
    				}
    				else if (degree >= 67.5 && degree <= 90) {
    					heading = "east";
    				} else {
    					heading = "unknown";
    				}
    			}
    			// Quadrant 3
    			if (x <= dest_x && z >= dest_z) {
    				if (degree >= 0 && degree < 22.5) {
    					heading = "north";
    				}
    				else if (degree >= 22.5 && degree < 67.5) {
    					heading = "northeast";
    				}
    				else if (degree >= 67.5 && degree <= 90) {
    					heading = "east";
    				} else {
    					heading = "unknown";
    				}
    			}
    			// Quadrant 4
    			else if (x >= dest_x && z > dest_z) {
    				if (degree >= 0 && degree < 22.5) {
    					heading = "north";
    				}
    				else if (degree >= 22.5 && degree < 67.5) {
    					heading = "northwest";
    				}
    				else if (degree >= 67.5 && degree <= 90) {
    					heading = "west";
    				} else {
    					heading = "unknown";
    				}
    			}
    			
    			if (heading == "unknown" && y >= dest_y) {
        			sender.sendMessage("Your destination is " + ChatColor.GOLD + "under" + ChatColor.WHITE + " you.");
    			}
    			else if (heading == "unknown" && y < dest_y) {
        			sender.sendMessage("Your destination is " + ChatColor.GOLD + "above" + ChatColor.WHITE + " you.");
    			} else {
    				sender.sendMessage("Your destination is " + ChatColor.GOLD + heading + ChatColor.WHITE + " from you.");
    			}
    			sender.sendMessage("You are currently facing " + ChatColor.GOLD + getDirection(loc.getYaw(), true) + ChatColor.WHITE + ".");
				return true;
    		}
			
			// Teleport command
    		else if (args[0].equalsIgnoreCase("tp")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.tp.public") && !sender.hasPermission("mc.tp.private") && !sender.hasPermission("mc.tp.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				} else {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			
    			if (args.length == 1) {
    				sender.sendMessage(ChatColor.RED + "You must specify a location id.");
					return true;
    			}
    			
    			if (!isNumeric(args[1])) {
    				sender.sendMessage(ChatColor.RED + "Location must be an id.");
    				return true;
    			}
    			
    			// Array for available IDs that user can goto
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			if (useDatabase()) {
    				// Use Database - Gathers arraylist of ids user can choose from based on the permission tp.
	    			validIDs(sender, ids, "tp");
	    			
	    			// Now that we have all available ids that user can goto, try to calculate route.
	    			
	    			// Does the player have permission to goto this location?
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
						sender.sendMessage(ChatColor.RED + "Location does not exist!");
						return true;
	    			}
	    			
	    			try {
	    				Player player = (Player) sender;
		    		    Connection con = connection();
		    	        con.setAutoCommit(false);
		    	        
		    	        Statement st = con.createStatement();
		    	        
		    			String query = "SELECT * FROM " + getTable() + " WHERE id = " + args[1];
		    			ResultSet result = st.executeQuery(query);
		    			con.commit();
		    			result.next();
		    			
		    			player.teleport(new Location(Bukkit.getServer().getWorld(worldIDToString(result.getInt("world"), true)), result.getInt("x"), result.getInt("y"), result.getInt("z")));
		    			player.sendMessage(ChatColor.LIGHT_PURPLE + "Poof! " + ChatColor.WHITE + "Teleported to " + ChatColor.GOLD + result.getString("name") + ChatColor.WHITE + "!");
		    			
		    			result.close();
						st.close();
						con.close();
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
		    		    return true;
					}
	    			
	    		} else {
	    			// Use Flat Files
	    			validIDsFF(sender, ids, "tp");
			    	// Now that we have all available ids that user can goto, try to calculate route.
	    			
	    			// Does the player have permission to goto this location?
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
						sender.sendMessage(ChatColor.RED + "Location does not exist!");
						return true;
	    			}
	    			
	    			try {
	    				Player player = (Player) sender;
	    				
	    				File locFile = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + args[1]);
    			    	FileConfiguration loc = new YamlConfiguration();
    			    	try {
    			    		loc.load(locFile);
    			    	} catch (Exception e) {
    			    		if (debug()) {
    			        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
    			        	}
    			    	}
    			    	
		    			player.teleport(new Location(Bukkit.getServer().getWorld(worldIDToString(loc.getInt("world"), true)), loc.getInt("x")+.51337, loc.getInt("y")+.51337, loc.getInt("z")+.51337));
		    			player.sendMessage(ChatColor.LIGHT_PURPLE + "Poof! " + ChatColor.WHITE + "Teleported to " + ChatColor.GOLD + loc.getString("name") + ChatColor.WHITE + "!");
		    			
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
		    		    return true;
					}
	    		}    			
    			return true;
    		}
			
			// Set Compass
    		else if (args[0].equalsIgnoreCase("setc") || args[0].equalsIgnoreCase("setcompass")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.compass.public") && !sender.hasPermission("mc.compass.private") && !sender.hasPermission("mc.compass.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				} else {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			
    			if (args.length == 1) {
    				sender.sendMessage(ChatColor.RED + "You must specify a location id.");
					return true;
    			}
    			
    			// Array for available IDs that user can set compass to
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			
    			if (useDatabase()) {
    				// Use Database - Gathers arraylist of ids user can choose from based on the permission compass.
    				validIDs(sender, ids, "compass");
    			} else {
    				// Use Flat Files
    				validIDsFF(sender, ids, "compass");
    			}
    			// Now that we have all available ids that user can set compass to, set the compass.
    			
    			// Does the player have permission to goto this location?
    			if (!isNumeric(args[1])) {
    				sender.sendMessage(ChatColor.RED + "Location must be an id.");
    				return true;
    			}
    			if (!ids.contains(Integer.parseInt(args[1]))) {
					sender.sendMessage(ChatColor.RED + "Location does not exist!");
					return true;
    			}
    			
    			// Get the player's current location
    			Player player = (Player) sender;
    			int rx = 0, ry = 0, rz = 0, rworld = 0;
    			world = player.getLocation().getWorld().getName();
			    world_id = stringToWorldID(world);
    			String rname = null;
    			
    			if (useDatabase()) {
					try {
		    		    Connection con = connection();
		    	        con.setAutoCommit(false);
		    	        
		    	        Statement st = con.createStatement();
		    	        
		    			String query = "SELECT * FROM " + getTable() + " WHERE id = " + args[1];
		    			ResultSet result = st.executeQuery(query);
		    			con.commit();
		    			result.next();
		    			rname = result.getString("name");
		    			rx = result.getInt("x");
		    			ry = result.getInt("y");
		    			rz = result.getInt("z");
		    			rworld = result.getInt("world");
		    			result.close();
						st.close();
						con.close();
					} catch (Exception e) {
						
					}
    			} else {
    				// Use Flat Files
    				File locFile = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + args[1]);
			    	FileConfiguration locconfig = new YamlConfiguration();
			    	try {
			    		locconfig.load(locFile);
			    		rname = locconfig.getString("name");
			    		rx = locconfig.getInt("x");
		    			ry = locconfig.getInt("y");
		    			rz = locconfig.getInt("z");
		    			rworld = locconfig.getInt("world");
			    	} catch (Exception e) {
			    		if (debug()) {
			        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
			        	}
			    	}
    			}
    			if (world_id != rworld) {
    				sender.sendMessage(ChatColor.RED + "The compass won't point you to locations on different worlds you silly goose. Go to " + worldIDToString(rworld) + " first.");
    				return true;
    			}
    			
    			// Point Compass
    			player.setCompassTarget(player.getWorld().getBlockAt(rx, ry, rz).getLocation());
				sender.sendMessage("Your compass is now pointing at " + ChatColor.GREEN + rname + ChatColor.WHITE + " [" + ChatColor.LIGHT_PURPLE + worldIDToString(rworld) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + rx + ChatColor.WHITE + " Y: " + ChatColor.GOLD + ry + ChatColor.WHITE + " Z: " + ChatColor.GOLD + rz);
				sender.sendMessage("Type /mc reset to reset your compass to point to spawn.");
    			return true;
    		}
			// Reset Compass
    		else if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("resetc")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					
					if (!sender.hasPermission("mc.compass.reset") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
						return true;
					}
				} else {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			
    			// Reset Compass
    			Player player = (Player) sender;
    			player.setCompassTarget(player.getWorld().getSpawnLocation());
    			
				sender.sendMessage("Your compass has been reset to the default spawn point.");
    			
    			return true;
    		}
    		// Publish coordinates to Public List
    		else if (args[0].equalsIgnoreCase("publish") || args[0].equalsIgnoreCase("share")) {
    			
    			// Check if player is OP or has the proper permission to run command					
    			if (!sender.hasPermission("mc.publish.private") && !sender.hasPermission("mc.publish.other") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
					return true;
				}
				
				if (args.length == 1) {
    				sender.sendMessage(ChatColor.RED + "You must specify a location id.");
					return true;
    			}
    			
    			if (!isNumeric(args[1])) {
    				sender.sendMessage(ChatColor.RED + "Location must be an id.");
    				return true;
    			}
    			
    			// Array for available IDs that user can publish
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			if (useDatabase()) {
    				// Use Database - Gathers arraylist of ids user can choose from based on the permission publish.
	    			validIDs(sender, ids, "publish");
	    			
	    			// Does the player have permission to publish this location?
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
						sender.sendMessage(ChatColor.RED + "Location does not exist!");
						return true;
	    			}
	    			
	    			try {
		    			Connection con = connection();
			    		con.setAutoCommit(false);
			    		
						Statement st = con.createStatement();
						Statement stUpdate = con.createStatement();
						
						// For changing userid to public user
						String sql = "UPDATE " + getTable() + " SET user = 2 WHERE id = " + args[1]; 
						stUpdate.executeUpdate(sql);
						con.commit();
						sender.sendMessage("Location was published to public list!");
						// Close statements and database connection
						st.close();
				        stUpdate.close();
				        con.close();
	    			} catch (Exception e) {
	    				if (debug()) {
			        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
			        	}
	    			}
	    			
	    		} else {
	    			// Use Flat Files
	    			validIDsFF(sender, ids, "publish");
	    			
	    			// Does the player have permission to publish this location?
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
						sender.sendMessage(ChatColor.RED + "Location does not exist!");
						return true;
	    			}
			    	// Now that we have all available ids that user can publish, try to change user to public user.
	    			
	    			File locFile = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + args[1]);
	    	    	FileConfiguration loc = new YamlConfiguration();
	    	    	
	    	    	try {
	    	    		loc.load(locFile);
	    	    	} catch (Exception e) {
	    	    		if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    	    	}
	    	    	loc.set("user", 2);
	    	    	try {
	    				loc.save(locFile);
						sender.sendMessage("Location was published to public list!");
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
	    		}    			
    			return true;
    		}
    		// Rename locations
    		else if (args[0].equalsIgnoreCase("rename")) {
    			
    			// Check if player is OP or has the proper permission to run command					
    			if (!sender.hasPermission("mc.rename.public") && !sender.hasPermission("mc.rename.private") && !sender.hasPermission("mc.rename.other") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
					return true;
				}
				
				if (args.length == 1) {
    				sender.sendMessage(ChatColor.RED + "You must specify a location id.");
					return true;
    			}
				
				if (args.length == 2) {
    				sender.sendMessage(ChatColor.RED + "You must specify a new name.");
					return true;
    			}
    			
    			if (!isNumeric(args[1])) {
    				sender.sendMessage(ChatColor.RED + "Location must be an id.");
    				return true;
    			}
    			
    			// Array for available IDs that user can publish
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			if (useDatabase()) {
    				// Use Database - Gathers arraylist of ids user can choose from based on the permission rename.
	    			validIDs(sender, ids, "rename");
	    			
	    			// Does the player have permission to publish this location?
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
						sender.sendMessage(ChatColor.RED + "Location does not exist!");
						return true;
	    			}
	    			
	    			try {
		    			Connection con = connection();
			    		con.setAutoCommit(false);
			    		
						Statement st = con.createStatement();
						Statement stUpdate = con.createStatement();
						
						// For changing userid to public user
						String sql = "UPDATE " + getTable() + " SET name = '" + args[2] + "' WHERE id = " + args[1]; 
						stUpdate.executeUpdate(sql);
						con.commit();
						sender.sendMessage(ChatColor.GREEN + "Location was renamed successfully!");
						// Close statements and database connection
						st.close();
				        stUpdate.close();
				        con.close();
	    			} catch (Exception e) {
	    				if (debug()) {
			        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
			        	}
	    			}
	    			
	    		} else {
	    			// Use Flat Files
	    			validIDsFF(sender, ids, "rename");
	    			
	    			// Does the player have permission to publish this location?
	    			if (!ids.contains(Integer.parseInt(args[1]))) {
						sender.sendMessage(ChatColor.RED + "Location does not exist!");
						return true;
	    			}
			    	// Now that we have all available ids that user can publish, try to change user to public user.
	    			
	    			File locFile = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + args[1]);
	    	    	FileConfiguration loc = new YamlConfiguration();
	    	    	
	    	    	try {
	    	    		loc.load(locFile);
	    	    	} catch (Exception e) {
	    	    		if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    	    	}
	    	    	loc.set("name", args[2]);
	    	    	try {
	    				loc.save(locFile);
						sender.sendMessage(ChatColor.GREEN + "Location was renamed successfully!");
	    			} catch (Exception e) {
	    				if (debug()) {
	    	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	    	        	}
	    			}
	    		}    			
    			return true;
    		} else {
				sender.sendMessage(ChatColor.RED + "Unknown command. Type /mc for help.");
				return true;
			}
    	}
    	return false;
    }

    /**
     * Creates a Mapcoords directory in the plugins directory and copies
     * the default configuration file from the packaged jar archive into
     * the new folder
     */
    private void firstRun() throws Exception {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            copy(getResource("config.yml"), configFile);
        }
    }
    
    /**
     * Copies default files from the packaged jar into the new plugin
     * directory that is generated on firstRun().
     *
     * @param  in   The InputStream of the resource to copy
     * @param  file The destination of the copied file
     */
    private void copy(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
        }
    }

    /**
     * Loads the configuration file from the plugin's directory
     */
    private void loadYamls() {
        try {
            config.load(configFile);
        } catch (Exception e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
        }
    }

    /**
     * Saves the plugin's configuration file
     */
    private void saveYamls() {
        try {
            config.save(configFile);
        } catch (IOException e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
        }
    }
    
/* Custom Methods */
    /**
     * This method will determine whether it can connect to a MySQL Database
     * using the authentication information supplied in the config file.
     */
    private void canConnect() {
    	String url = config.getString("database.url");
        String user = config.getString("database.username");
        String pass = config.getString("database.password");
        
    	Connection connection = null;
    	try {
    	    connection = DriverManager.getConnection(url, user, pass);
    	} catch (SQLException e) {
    		getLogger().severe("\033[1;31mFailed to connect to the database! Make sure your configuration is set up properly or that you have enabled flat file storage by setting the useDatabase option to false.\033[0m");
    		epic_fail = true;
    		if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
    	} finally {
    	    if (connection != null) try { connection.close(); } catch (SQLException ignore) {}
    	}
    }
    
    /**
     * Creates the primary coordinates table for the MySQL Database.
     */
    private void createTable() {
    	try {
	        Connection con = connection();
	        con.setAutoCommit(false);
	        
	        Statement st = con.createStatement();
	        DatabaseMetaData metadata = con.getMetaData();
	        ResultSet resultSet;
	        
	        // Checks to see if the table already exists
	        resultSet = metadata.getTables(null, null, getTable(), null);
	        
	        if (!resultSet.next()) {			
				String sql = "CREATE TABLE IF NOT EXISTS " + getTable() + " (`id` int(6) NOT NULL auto_increment, `user` varchar(25) NOT NULL,  `name` varchar(50) NOT NULL,  `x` int(8) NOT NULL,  `y` int(3) NOT NULL,  `z` int(8) NOT NULL,  `world` int(1) NOT NULL,  PRIMARY KEY  (`id`)) ENGINE=MyISAM DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;"; 
				st.executeUpdate(sql);
				con.commit();
				getLogger().info("\033[1;32mMapcoords table '" + getTable() + "' was created successfully!\033[0m");
				first_run = true;
	        }

	        // Close statement and database connection
	        resultSet.close();
	        st.close();
	        con.close();
        } catch (SQLException e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
		}
    }
    
    /**
     * Create the worlds table and insert the default world profiles,
     * world, world_nether, world_the_end, and Unknown.
     */
    private void createTableWorlds() {
    	try {
	        Connection con = connection();
	        con.setAutoCommit(false);
	        
	        Statement st = con.createStatement();
	        DatabaseMetaData metadata = con.getMetaData();
	        ResultSet resultSet;
	        
	        // Checks to see if the table already exists
	        resultSet = metadata.getTables(null, null, getTable() + "_worlds", null);
	        
	        if (!resultSet.next()) {			
				st = con.createStatement();
				
				// Create table_worlds table
				String sql = "CREATE TABLE IF NOT EXISTS " + getTable() + "_worlds (`id` int(6) NOT NULL auto_increment, `name` varchar(25) NOT NULL,  `altname` varchar(25) NOT NULL,  PRIMARY KEY  (`id`)) ENGINE=MyISAM DEFAULT CHARSET=latin1 AUTO_INCREMENT=1"; 
				st.executeUpdate(sql);
				// Insert Default world data for ids 1, 2, 3, and 4
				sql = "INSERT INTO " + getTable() + "_worlds VALUES(null, \"world\", \"Overworld\");"; 
				st.executeUpdate(sql);
				sql = "INSERT INTO " + getTable() + "_worlds VALUES(null, \"world_nether\", \"Nether\");"; 
				st.executeUpdate(sql);
				sql = "INSERT INTO " + getTable() + "_worlds VALUES(null, \"world_the_end\", \"The End\");"; 
				st.executeUpdate(sql);
				sql = "INSERT INTO " + getTable() + "_worlds VALUES(null, \"Unknown\", \"Unknown\");"; 
				st.executeUpdate(sql);
				con.commit();
				getLogger().info("\033[1;32mMapcoords table '" + getTable() + "_worlds' was created successfully!\033[0m");
				
				// Purge all invalid records for coordinates before 1.0.2 with world id 3.
				moveInvalidPre();
				getLogger().info("\t\033[1;32mAdding default world profiles...\033[0m");
				getLogger().info("\t\033[1;32m- world          [1] added!\033[0m");
		        getLogger().info("\t\033[1;32m- world_nether   [2] added!\033[0m");
				getLogger().info("\t\033[1;32m- world_the_end  [3] added!\033[0m");
				getLogger().info("\t\033[1;32m- Unknown        [4] added!\033[0m");
	        }
	        // Close statement and database connection
	        resultSet.close();
	        st.close();
	        con.close();
        } catch (SQLException e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
		}
    }
    
    /**
     * Creates the users table. Whenever a new player tries to
     * add a new coordinate location, they will have a unique
     * user id paired with their account and all of their
     * coordinates for support of public and private coordinates.
     */
    private void createTableUsers() {
    	try {
	        Connection con = connection();
	        con.setAutoCommit(false);
	        
	        Statement st = con.createStatement();
	        DatabaseMetaData metadata = con.getMetaData();
	        ResultSet resultSet;
	        
	        // Checks to see if the table already exists
	        resultSet = metadata.getTables(null, null, getTable() + "_users", null);
	        
	        if (!resultSet.next()) {			
				// Create table_worlds table
				String sql = "CREATE TABLE IF NOT EXISTS " + getTable() + "_users (`id` int(6) NOT NULL auto_increment, `username` varchar(50) NOT NULL,  PRIMARY KEY  (`id`)) ENGINE=MyISAM DEFAULT CHARSET=latin1 AUTO_INCREMENT=1"; 
				st.executeUpdate(sql);
				// Insert Unknown Player
				sql = "INSERT INTO " + getTable() + "_users VALUES(null, \"Unknown Player\");"; 
				st.executeUpdate(sql);
				// Insert Public Player
				sql = "INSERT INTO " + getTable() + "_users VALUES(null, \"Public Player\");"; 
				st.executeUpdate(sql);
				con.commit();
				getLogger().info("\033[1;32mMapcoords table '" + getTable() + "_users' was created successfully!\033[0m");
				
				// Update usernames into userids for coords table
				updateUserValues();
				
				getLogger().info("\t\033[1;32mAdding default user profiles...\033[0m");
				getLogger().info("\t\033[1;32m- Unknown Player [1] added!\033[0m");
				getLogger().info("\t\033[1;32m- Public Player  [2] added!\033[0m");
	        }
	        // Close statement and database connection
	        resultSet.close();
	        st.close();
	        con.close();
        } catch (SQLException e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
		}
    }
    
    /**
     * Converts all invalid coordinates that were created before 1.0.2
     * that have the world id of 3 to a new world id of 4. World id 4
     * is the new reserved world ids that used to be 3 in versions prior
     * to 1.0.2
     */
    private void moveInvalidPre() {
    	// Move invalid coordinates with id 3 to new unknown world id 4
    	if (!first_run) {
	    	try {
	    		getLogger().info("Attempting to move unknown coordinate entries (pre 1.0.2) to updated unknown world id");
	    		Connection con = connection();
	    		con.setAutoCommit(false);
	    		
				Statement st = con.createStatement();
				
				// Update Unknown world ids
				String sql = "UPDATE " + getTable() + " SET world=4 WHERE world=3"; 
				st.executeUpdate(sql);
				con.commit();
				getLogger().info("\033[1;32mUnknown coordinate entries (pre 1.0.2) were updated successfully!\033[0m");
				
				// Close statement and database connection
		        st.close();
		        con.close();
	    	} catch (SQLException e) {
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
			// Update world ids 0,1,2,3 to 1,2,3,4
			if (!first_run) {
				try {
					Connection con = connection();
					con.setAutoCommit(false);
					
					Statement st = con.createStatement();
					
					getLogger().info("Attempting to update world ids to new format");
					String sql = "UPDATE " + getTable() + " SET world=3 WHERE world=2"; 
					st.executeUpdate(sql);
					sql = "UPDATE " + getTable() + " SET world=2 WHERE world=1"; 
					st.executeUpdate(sql);
					sql= "UPDATE " + getTable() + " SET world=1 WHERE world=0"; 
					st.executeUpdate(sql);
					con.commit();
					getLogger().info("\033[1;32mWorld ids were updated successfully!\033[0m");
					
					// Close statement and database connection
			        st.close();
			        con.close();
				} catch (SQLException e) {
					if (debug()) {
		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
		        	}
				}
			}
    	}
    }
    
    /**
     * Purges invalid coordinates with the world id 4.
     */
    @SuppressWarnings({"unused"})
    private void purgeInvalid() {
    	if (!first_run) {
	    	try {
	    		if (!epic_fail) {
	    			getLogger().info("Attemping to purge invalid data (post 1.0.2) entries from " + getTable());
	    		}
				Connection con = connection();
				con.setAutoCommit(false);
				
		        PreparedStatement st = con.prepareStatement("DELETE FROM " + getTable() + " WHERE world IN (?,?)");
				st.setInt(1, 0);
				st.setInt(2, 4);
				st.executeUpdate();
				con.commit();
				getLogger().info("\033[1;32mInvalid data (post 1.0.2) was purged successfully!\033[0m");
				
				// Close statement and database connection
		        st.close();
		        con.close();
			} catch (SQLException e) {
				if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
    	}
    }
    
    /**
     * Converts a world name to its equivalent world id.
     *
     * @param  world_name  The world name that is to be converted into a world id
     * @return             World id if found. If it isn't found, a new worldid will be generated.
     */
    private int stringToWorldID(String world_name) {
    	// Search for string in database first
    	if (useDatabase()) {
	    	try {
		        Connection con = connection();
		        con.setAutoCommit(false);
		        
		        Statement st = con.createStatement();
		        
				String query = "SELECT name, id FROM " + getTable() + "_worlds WHERE name = '" + world_name + "'";
				ResultSet result = st.executeQuery(query);
				con.commit();
				result.next();
				
				int id = result.getInt("id");
				
				// Close statement and database connection
				result.close();
		        st.close();
		        con.close();
				return id;
	        } catch (SQLException e) {
	        	// If there was an exception, then that means that this world isn't in the database yet.
	        	if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
	        	try {
	        		Connection con = connection();
	        		con.setAutoCommit(false);
	    	        
	    	        PreparedStatement st = con.prepareStatement("INSERT INTO " + getTable() + "_worlds VALUES(null, ?, ?)");
	    			st.setString(1, world_name);
	    			st.setString(2, world_name);
	    			st.executeUpdate();
	    			con.commit();
	    			
	    			// Close statement and database connection
	    	        st.close();
	    	        con.close();
	    			return stringToWorldID(world_name);
	            } catch (SQLException ex) {
	            	if (debug()) {
	            		getLogger().severe("\033[1;31m[Debug] Error occured: " + ex + "\033[0m");
	            	}
	            	return 4;
	            }
			}
    	} else {
    		// Use flat files
    		String files, worldname;
    		int worldid = 0;
			File folder = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds");
			File[] listOfFiles = folder.listFiles(); 
			 
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile()) {
					files = listOfFiles[i].getName();
					File world = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + files);
                
					FileConfiguration worldConfig = new YamlConfiguration();
	
					try {
						worldConfig.load(world);
						worldname = worldConfig.getString("name");
						if (worldname.equalsIgnoreCase(world_name)) {
							return Integer.parseInt(files);
						}
					} catch (Exception e) {
						if (debug()) {
			        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
			        	}
					}
				}
            }
			// Load in counters file for Flat File Storage
	    	loadCounter();
		    
			try {
				File world = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + counter.get(getFolderName() + "_worlds"));
				world.createNewFile();
				FileConfiguration worldconfig = new YamlConfiguration();
				
				worldconfig.load(world);
		        worldconfig.set("name", world_name);
		        worldconfig.set("altname", world_name);
		        worldconfig.save(world);
			} catch (Exception e) {
				if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
	        
	        if (debug()) {
	        	String newworld = getFolderName() + "_worlds";
	        	worldid = counter.getInt(newworld);
	        	getLogger().info("\033[1;34mWorld " + world_name + " [" + worldid + "] added!\033[0m");
	        }

			// Update counter values
			updateCounter(3);
            return worldid;
    	}
    }
    
    /**
     * Converts a world id into a worldname 
     *
     * @param  world_id  The world id to be converted into a world name.
     * @return      Name of the world that has the supplied world id.
     */
    private String worldIDToString(int world_id) {
    	// Search for string in database first
    	if (useDatabase()) {
	    	try {
		        Connection con = connection();
		        con.setAutoCommit(false);
		        Statement st = con.createStatement();
		        
				String query = "SELECT name, altname, id FROM " + getTable() + "_worlds WHERE id = '" + world_id + "'";
				ResultSet result = st.executeQuery(query);
				con.commit();
				result.next();
				
				String name;
				if (result.getString("altname") == null) {
					name = result.getString("name");
				} else {
					name = result.getString("altname");
				}
				
				// Close statement and database connection
				result.close();
		        st.close();
		        con.close();
		        return name;
	        } catch (SQLException e) {
	        	if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
				return "Unknown";
			}
    	} else {
    		// Use flat files
			File world = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + world_id);
			FileConfiguration worldConfig = new YamlConfiguration();
			
			try {
				worldConfig.load(world);
				return worldConfig.getString("altname");
			} catch (Exception e) {
				if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
			return "Unknown";
    	}
    }
    
    /**
     * Returns the internal name of the supplied world id.
     *
     * @param  world_id  The world_id that is to be converted
     * @param  internal_name True if you want the internal name to be returned
     * @return      Internal Name of world.
     */
    private String worldIDToString(int world_id, boolean internal_name) {
    	// Search for string in database first
    	if (useDatabase()) {
	    	try {
		        Connection con = connection();
		        con.setAutoCommit(false);
		        Statement st = con.createStatement();
		        
				String query = "SELECT name, altname, id FROM " + getTable() + "_worlds WHERE id = '" + world_id + "'";
				ResultSet result = st.executeQuery(query);
				con.commit();
				result.next();
				
				String name;
				if (result.getString("name") == null) {
					name = result.getString("altname");
				} else {
					name = result.getString("name");
				}
				
				// Close statement and database connection
				result.close();
		        st.close();
		        con.close();
		        return name;
	        } catch (SQLException e) {
	        	if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
				return "Unknown";
			}
    	} else {
    		// Use flat files
			File world = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + world_id);
			FileConfiguration worldConfig = new YamlConfiguration();
			
			String name;
			try {
				worldConfig.load(world);
				if (worldConfig.getString("name") == null) {
					name = worldConfig.getString("altname");
				} else {
					name = worldConfig.getString("name");
				}
				return name;
			} catch (Exception e) {
				if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
			return "Unknown";
    	}
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (config.getBoolean("settings.checkForUpdates") == true) {
	        if (player.isOp() && isInternetReachable()) {
	        	try {
	    	        URL update = new URL("http://guysthatcode.com/keith/bukkit/mapcoords.php");
	    	        BufferedReader in = new BufferedReader(new InputStreamReader(update.openStream()));
	    	
	    	        newVersion = in.readLine();
	    	        in.close();
	    	        
	    	        // Convert versions into numbers
	    	        iver = Integer.parseInt(version.replaceAll("[.]", ""));
	    	        inewver = Integer.parseInt(newVersion.replaceAll("[.]", ""));
	    	        if (iver != inewver) {
	    	        	player.sendMessage(ChatColor.LIGHT_PURPLE + "A new version of Mapcoords is available!");
	    	        }
	            } catch (Exception ex) {
	    			getLogger().severe("\033[1;31mError #6: Failed connecting to update server!\033[0m");
	            }
	        }
        }
    }
    
    /*
     * This method will convert a username into their corresponding
     * user id that will be looked up in the database. If the
     * user id is not found, it'll be automatically created.
     */
    /**
     * Convers a username into its corresponding world id. If a userid is
     * not found, one will be created automatically
     *
     * @param  username  Username to be converted to user id.
     * @return      User id of supplied username
     */
    private int usernameToUserID(String username) {
    	// Search for string in database first
    	if (useDatabase()) {
	    	try {
		        Connection con = connection();
		        con.setAutoCommit(false);
		        
		        Statement st = con.createStatement();
		        
				String query = "SELECT username, id FROM " + getTable() + "_users WHERE username = '" + username + "'";
				ResultSet result = st.executeQuery(query);
				con.commit();
				result.next();
				int id = result.getInt("id");
				
				// Close statement and database connection
				result.close();
		        st.close();
		        con.close();
				return id;
	        } catch (SQLException ex) {
	        	// If there was an exception, then that means that this user isn't in the database yet.
	        	getLogger().info("\033[1;34mUser " + username + " doesn't have a userid yet. Creating new userid...\033[0m");
	        	try {
	        		Connection con = connection();
	        		con.setAutoCommit(false);
	        		
	        		Statement st = con.createStatement();
	    	        PreparedStatement statement = con.prepareStatement("INSERT INTO " + getTable() + "_users VALUES(null, ?)");
	    			statement.setString(1, username);
	    			statement.executeUpdate();
	    			
	
	    			String query = "SELECT username, id FROM " + getTable() + "_users WHERE username = '" + username + "'";
	    			ResultSet result = st.executeQuery(query);
	    			con.commit();
	    			result.next();
	    			
	    			int id = result.getInt("id");
	    			// Close statement and database connection
	    			result.close();
	    			statement.close();
	    	        st.close();
	    	        con.close();
	    			return id;
	            } catch (SQLException exc) {
	            	if (!epic_fail) {
	            		getLogger().severe("\033[1;31mError #7: Failed to find user id equivalent of supplied username!\033[0m" + ex);
	            	}
	            	return 1;
	            }
			}
    	} else {
    		// Use flat files
    		String files, testuser;
			File folder = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users");
			File[] listOfFiles = folder.listFiles(); 
			 
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile()) {
					files = listOfFiles[i].getName();
					File user = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users" + File.separator + files);
                
					FileConfiguration userConfig = new YamlConfiguration();
	
					try {
						userConfig.load(user);
						testuser = userConfig.getString("username");
						if (testuser.equalsIgnoreCase(username)) {
							return Integer.parseInt(files);
						}
					} catch (Exception ex) {
	
					}
				}
            }
			// If there was no user profile found, then that means that this user isn't in the database yet.
        	getLogger().info("\033[1;34mUser " + username + " doesn't have a userid yet. Creating new userid...\033[0m");
        	
        	// Load in counters file for Flat File Storage
	    	loadCounter();
	    	
	    	try {
	    		// Create File object of new user
				File user = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users" + File.separator + counter.get(getFolderName() + "_users"));
				user.createNewFile();
				FileConfiguration userconfig = new YamlConfiguration();
				try {
					userconfig.load(user);
		        } catch (Exception e) {
		            
		        }
		        userconfig.set("username", username);
		        userconfig.save(user);
		        
		        // Update counter values
				updateCounter(2);
				
		        return counter.getInt(getFolderName() + "_users")-1;
			} catch(Exception ex) {
				System.out.println(ex);
			}
			
			return 1;
    	}
    }
    
    /**
     * Converts userid to corresponding username.
     *
     * @param  userid  Userid that is to be converted into a username.
     * @return      Converted username.
     */
    @SuppressWarnings({"unused"})
    private String userIDToUsername(int userid) {
    	// Search for string in database first
    	if (useDatabase()) {
	    	try {
		        Connection con = connection();
		        con.setAutoCommit(false);
		        
		        Statement st = con.createStatement();
				String query = "SELECT id, username FROM " + getTable() + "_users WHERE id = '" + userid + "'";
				ResultSet result = st.executeQuery(query);
				con.commit();
				
				result.next();
				String username;
				if (result.getString("username") == null || userid == 0 || userid == 1) {
					username = "Unknown Player";
				} else {
					username = result.getString("username");
				}
				// Close statement and database connection
				result.close();
		        st.close();
		        con.close();
		        return username;
	        } catch (SQLException ex) {
	        	if (!epic_fail) {
	        		getLogger().severe("\033[1;31mError #8: Failed to find username equivalent of supplied userid!\033[0m" + ex);
	        	}
				return "Unknown Player";
			}
    	} else {
    		// Flat file
    		
    		// Retrieve list of user profiles
    		File users = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users");
            for (File f : users.listFiles()){
                File check = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users" + File.separator + f.getName());
                
				FileConfiguration checkConfig = new YamlConfiguration();

				try {
					checkConfig.load(check);
					checkConfig.save(check);
					return checkConfig.getString("username");
				} catch (Exception ex) {
					if (!epic_fail) {
		        		getLogger().severe("\033[1;31mError #8: Failed to find username equivalent of supplied userid!\033[0m" + ex);
		        	}
				}
            }
    		return "Unknadsfasdown Player";
    	}
    }
    
    /*
     * This method will move convert usernames in the coords table
     * into user ids. This is for the future when public and
     * private coordinate lists will be added.
     */
    /**
     * Converts usernames into userids if table is pre 1.0.2
     */
    private void updateUserValues() {
    	// Move invalid coordinates with id 3 to new unknown world id 4
    	if (!first_run) {
	    	try {
	    		getLogger().info("Attempting to update usernames into userids");
	    		Connection con = connection();
	    		con.setAutoCommit(false);
	    		
				Statement st = con.createStatement();
				Statement stUpdate = con.createStatement();
				// Cycle through each record of the coords table and update the user column into userids.
				// If the userid doesn't exist yet, automatically create it.
				
				// For updating username into userid
				String sql;
				String query = "SELECT user, id FROM " + getTable();
				ResultSet result = st.executeQuery(query);
				
				while (result.next()) {
					sql = "UPDATE " + getTable() + " SET user=" + usernameToUserID(result.getString("user")) + " WHERE id=" + result.getInt("id"); 
					stUpdate.executeUpdate(sql);
					con.commit();
				}
				getLogger().info("\033[1;32mAll usernames values have been converted into userids successfully!\033[0m");
				
				// This fixes it but daddy doesn't want to admit it
				Thread.sleep(5000);
				
				// This MIGHT have fixed it! :D
				String update = "ALTER TABLE " + getTable() + " CHANGE user user INT( 6 ) NOT NULL"; 
				PreparedStatement altupdate = con.prepareStatement(update);
				altupdate.executeUpdate();
				con.commit();
				
				// Close statements and database connection
				result.close();
				altupdate.close();
				st.close();
		        stUpdate.close();
		        con.close();
	    	} catch (Exception ex) {
	    		if (!epic_fail) {
	    			getLogger().severe("\033[1;31mError #9a: Failed to update usernames into userids!\033[0m" + ex);
	    		}
			}
    	}
    }
    
    /**
     * Checks for available updates of Mapcoords
     */
    private void checkForUpdates() {
    	PluginManager pm = getServer().getPluginManager();
    	Plugin p = pm.getPlugin("Mapcoords");
    	PluginDescriptionFile pdf = p.getDescription();
    	version = pdf.getVersion();
    	
    	getLogger().info("Checking for updates...");
        try {
	        URL update = new URL("http://guysthatcode.com/keith/bukkit/mapcoords.php");
	        BufferedReader in = new BufferedReader(new InputStreamReader(update.openStream()));
	
	        newVersion = in.readLine();
	        in.close();
	        
	        // Convert versions into numbers
	        iver = Integer.parseInt(version.replaceAll("[.]", ""));
	        inewver = Integer.parseInt(newVersion.replaceAll("[.]", ""));
	        if (iver != inewver) {
	        	getLogger().info("\033[1;35mThere is a new version of Mapcoords available :D\033[0m");
	        	getLogger().info("\033[1;35mDownload it from http://dev.bukkit.org/bukkit-mods/mapcoords/\033[0m");
	        	getLogger().info("Your version: \033[1;31m" + version + "\033[0m");
	        	getLogger().info("New version : \033[1;32m" + newVersion + "\033[0m");
	        	newUpdate = true;
	        } else {
	        	getLogger().info("No new updates available...");
	        }
        } catch (Exception e) {
        	if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
        }
    }
    
    /**
     * Connects to the database and creates a Connection object.
     * @return      Connection object to database.
     */
    private Connection connection() {
    	String url = config.getString("database.url");
        String user = config.getString("database.username");
        String pass = config.getString("database.password");
        
    	Connection connection = null;
    	try {
    	    connection = DriverManager.getConnection(url, user, pass);
    	} catch (SQLException e) {
    		getLogger().severe("\033[1;31mFailed to connect to the database!\033[0m");
    		if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
    	}
    	return connection;
    }
    
    /**
     * Returns the table name that is supplied in the configuration file.
     *
     * @return      Table name.
     */
    private String getTable() {
    	return config.getString("database.table");
    }
    
    /**
     * Determines if the plugin has internet access to check for updates. 
     *
     * @return      True or false if internet is available
     */
    @SuppressWarnings({"unused"})
    private static boolean isInternetReachable()
    {
        try {
            //make a URL to a known source
            URL url = new URL("http://guysthatcode.com/keith/bukkit/mapcoords.php");

            //open a connection to that source
            HttpURLConnection urlConnect = (HttpURLConnection)url.openConnection();

            //trying to retrieve data from the source. If there
            //is no connection, this line will fail
            Object objData = urlConnect.getContent();

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            return false;
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            return false;
        }
        return true;
    }
    
    private String permsColor(Player p, String perms) {
    	if (p.hasPermission(perms)) {
    		return perms + ": " + ChatColor.GREEN + "yes" + ChatColor.WHITE;
    	} else {
    		return perms + ": " + ChatColor.RED + "no" + ChatColor.WHITE;
    	}
    }
    
    private String settingsColor(boolean bool) {
    	if (bool) {
    		return "\033[1;32mTrue\033[0m";
    	} else {
    		return "\033[1;31mFalse\033[0m";
    	}
    }
    
    private boolean doesUsernameExist(String username) {
    	if (useDatabase()) {
	    	try {
	    		Connection con = connection();
	    		con.setAutoCommit(false);
	    		
				Statement st = con.createStatement();
				
				String query = "SELECT COUNT(*) FROM " + getTable() + "_users WHERE username = '" + username + "'";
				ResultSet result = st.executeQuery(query);
				con.commit();
				result.next();
				int found = result.getInt("COUNT(*)");
	
				// Close statements and database connection
				result.close();
				st.close();
		        con.close();
		        
		        if (found == 1) {
		        	return true;
		        } else {
		        	return false;
		        }
		        
	    	} catch (Exception ex) {
	    		if (!epic_fail) {
	    			getLogger().severe("\033[1;31mError #10: Failed to determine if username exists!\033[0m" + ex);
	    		}
			}
	    	return false;
    	} else {
    		// Use Flat Files
    		// Load in counters file for Flat File Storage
	    	loadCounter();
	    	
	    	for (int a = 1; a < counter.getInt(getFolderName() + "_users"); a++) {
	    		try {
		    		// Create File object of new location with id as name
					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users" + File.separator + a);
    				FileConfiguration locconfig = new YamlConfiguration();
    				try {
    					locconfig.load(location);
    		        } catch (Exception e) {
    		        	if (debug()) {
    		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
    		        	}
    		        }
    		        
    		        // return true if there is a match
    		        if (locconfig.getString("username").equalsIgnoreCase(username)) {
    		        	return true;
    		        }
    		        
				} catch(Exception e) {
					if (debug()) {
		        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
		        	}
				}
	    	}
    	}
    	return false;
    }
    
    private String getDirection(float yaw, boolean name) {
    	if ( yaw < 0 ){
    		yaw += 360;
    	}
    	yaw %= 360;
        if (yaw <= 22.5 && yaw >= 0 || yaw > 337.5 && yaw <= 360) {
        	return "south";
        }
        else if (yaw > 22.5 && yaw <= 67.5) {
        	return "southwest";
        }
        else if (yaw > 67.5 && yaw <= 112.5) {
        	return "west";
        }
        else if (yaw > 112.5 && yaw <= 157.5) {
        	return "northwest";
        }
        else if (yaw > 157.5 && yaw <= 202.5) {
        	return "north";
        }
        else if (yaw > 202.5 && yaw <= 247.5) {
        	return "northeast";
        }
        else if (yaw > 247.5 && yaw <= 292.5) {
        	return "east";
        }
        else if (yaw > 247.5 && yaw <= 337.5) {
        	return "southeast";
        } else {
        	return "unknown";
        }
    }
    
    @SuppressWarnings({"unused"})
    private static boolean isNumeric(String str) {  
    	try {  
    		double d = Double.parseDouble(str);  
    	} catch(NumberFormatException nfe) {    
    		return false;
    	}
	  return true;  
	}
    
    private boolean useDatabase() {
    	return config.getBoolean("settings.useDatabase");
    }
    
    private boolean debug() {
    	return config.getBoolean("settings.debug");
    }
    
    private String getFolderName() {
    	return config.getString("settings.folderName");
    }
    
    private void updateCounter(int type) {
    	loadCounter();
    	
    	switch (type) {
    	case 1:
    		counter.set(getFolderName(), counter.getInt(getFolderName())+1);
    		break;
    	case 2:
    		counter.set(getFolderName() + "_users", counter.getInt(getFolderName() + "_users")+1);
    		break;
    	case 3:
    		counter.set(getFolderName() + "_worlds", counter.getInt(getFolderName() + "_worlds")+1);
    		break;
    	default:
    		break;
    	}
    	
		try {
			counter.save(counterFile);
		} catch (Exception e) {
			if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
		}
    }
    
    private void loadCounter() {
    	counterFile = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_counters");
    	counter = new YamlConfiguration();
    	try {
    		counter.load(counterFile);
    	} catch (Exception e) {
    		if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
    	}
    }
    
    private void validIDs(CommandSender sender, ArrayList<Integer> ids, String perm) {
    	try {
			Connection con = connection();
			con.setAutoCommit(false);
			
			Statement st = con.createStatement();
			
			String query = null;
			if (sender instanceof Player) {
				Player perms = (Player) sender;
				if (sender.hasPermission("mc." + perm + ".private") || !config.getBoolean("settings.permissions")) {
					query = "SELECT id FROM " + getTable() + " WHERE user = " + usernameToUserID(perms.getPlayerListName());
				}
				if ((sender.hasPermission("mc." + perm + ".public") || !config.getBoolean("settings.permissions")) && perm != "publish") {
					if (query != null) {
						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user = 2";
					} else {
						query = "SELECT id FROM " + getTable() + " WHERE user = 2";
					}
				}
				if (sender.hasPermission("mc." + perm + ".other") || !config.getBoolean("settings.permissions")) {
					if (query != null) {
						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user <> 2 AND user <> " + usernameToUserID(perms.getPlayerListName());
					} else {
						query = "SELECT id FROM " + getTable() + " WHERE user <> 2 AND user <> " + usernameToUserID(perms.getPlayerListName());
					}
				}
			} else {
				query = "SELECT id FROM " + getTable();
			}
			query = query + ";";
			ResultSet result = st.executeQuery(query);
			con.commit();
			
			// Add all ids to id ArrayList
			while (result.next()) {
				if (!ids.contains(result.getInt("id"))) {
					ids.add(result.getInt("id"));
				}
			}
			
			st.close();
			result.close();
			con.close();
		} catch (Exception e) {
			if (debug()) {
        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
        	}
		}
    }
    
    private void validIDsFF(CommandSender sender, ArrayList<Integer> ids, String perm) {
    	// Load in counters file for Flat File Storage
    	loadCounter();
    	
    	for (int a = 1; a < counter.getInt(getFolderName()); a++) {
    		try {
    			if (sender instanceof Player) {
		    		// Create File object of new location with id as name
					File location = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + File.separator + a);
					if (!location.exists()) {
						continue;
					}
    				FileConfiguration locconfig = new YamlConfiguration();
    				try {
    					locconfig.load(location);
    		        } catch (Exception e) {
    		        	
    		        }
    		        
    		        Player perms = (Player) sender;
    		        // If user has private permissions, can use their own coordinates
    		        if (sender.hasPermission("mc." + perm + ".private") || !config.getBoolean("settings.permissions")) {
    					if (locconfig.getInt("user") == usernameToUserID(perms.getPlayerListName())) {
    						if (!ids.contains(a)) {
								ids.add(a);
							}
    					}
    				}
    		        // If user has public permissions, can use public coordinates
    				if ((sender.hasPermission("mc." + perm + ".public") || !config.getBoolean("settings.permissions"))  && perm != "publish") {
    					if (locconfig.getInt("user") == 2) {
    						if (!ids.contains(a)) {
								ids.add(a);
							}
    					}
    				}
    		        // If user has other permissions, can use others' coordinates
    				if (sender.hasPermission("mc." + perm + ".other") || !config.getBoolean("settings.permissions")) {
    					if (locconfig.getInt("user") != 2 && locconfig.getInt("user") != usernameToUserID(perms.getPlayerListName())) {
    						if (!ids.contains(a)) {
								ids.add(a);
							}
    					}
    				}
	    		} else {
	    			if (!ids.contains(a)) {
						ids.add(a);
					}
	    		}
			} catch(Exception e) {
				if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
    	}
    }
    
    private void createFoldersAndFiles() {
    	root = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName());
		if (!root.isDirectory()) {
			try {
				root.mkdirs();
				getLogger().info("\033[1;32mMapcoords directory '" + getFolderName() + "' was created successfully!\033[0m");
			} catch (Exception e) {
	    		getLogger().severe("\033[1;31mError creating directory '" + getFolderName() + "'!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
    		// Create counter file
    		try {
				File counter = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_counters");
				counter.createNewFile();
				FileConfiguration counterconfig = new YamlConfiguration();

				counterconfig.load(counter);
		        counterconfig.set(getFolderName(), 1);
		        counterconfig.set(getFolderName() + "_users", 3);
		        counterconfig.set(getFolderName() + "_worlds", 5);
		        counterconfig.save(counter);
		        
			} catch(Exception e) {
	    		getLogger().severe("\033[1;31mError creating counter file!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
		}
		// Users folder
		users = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users");
		if (!users.isDirectory()) {
			try {
				users.mkdirs();
				getLogger().info("\033[1;32mMapcoords directory '" + getFolderName() + "_users' was created successfully!\033[0m");
			} catch (Exception e) {
	    		getLogger().severe("\033[1;31mError creating directory '" + getFolderName() + "_users'!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
			getLogger().info("\t\033[1;32mAdding default user profiles...\033[0m");
			try {
				File unknown = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users" + File.separator + "1");
				unknown.createNewFile();
				FileConfiguration unknownConfig = new YamlConfiguration();
				
				unknownConfig.load(unknown);
				unknownConfig.set("username", "Unknown Player");
				unknownConfig.save(unknown);
				getLogger().info("\t\033[1;32m- Unknown Player [1] added!\033[0m");
				
		        File publicPlayer = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_users" + File.separator + "2");
		        publicPlayer.createNewFile();
				FileConfiguration publicConfig = new YamlConfiguration();
				
				publicConfig.load(publicPlayer);
				publicConfig.set("username", "Public Player");
				publicConfig.save(publicPlayer);
				getLogger().info("\t\033[1;32m- Public Player  [2] added!\033[0m");
			} catch(Exception e) {
	    		getLogger().severe("\t\033[1;31mError adding user profiles!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
		}
		// Worlds folder
		worlds = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds");
		if (!worlds.isDirectory()) {
			try {
				worlds.mkdirs();
				getLogger().info("\033[1;32mMapcoords directory '" + getFolderName() + "_worlds' was created successfully!\033[0m");
			} catch (Exception e) {
	    		getLogger().severe("\033[1;31mError creating directory '" + getFolderName() + "_worlds'!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
			getLogger().info("\t\033[1;32mAdding default world profiles...\033[0m");
			try {
				File world = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + "1");
				world.createNewFile();
				FileConfiguration worldConfig = new YamlConfiguration();

				worldConfig.load(world);
				worldConfig.set("name", "world");
		        worldConfig.set("altname", "Overworld");
		        worldConfig.save(world);
		        getLogger().info("\t\033[1;32m- world          [1] added!\033[0m");

		        File worldNether = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + "2");
		        worldNether.createNewFile();
				FileConfiguration worldNetherConfig = new YamlConfiguration();

				worldNetherConfig.load(worldNether);
				worldNetherConfig.set("name", "world_nether");
				worldNetherConfig.set("altname", "Nether");
				worldNetherConfig.save(worldNether);
		        getLogger().info("\t\033[1;32m- world_nether   [2] added!\033[0m");
		        
		        File worldTheEnd = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + "3");
		        worldTheEnd.createNewFile();
				FileConfiguration worldTheEndConfig = new YamlConfiguration();

				worldTheEndConfig.load(worldTheEnd);
				worldTheEndConfig.set("name", "world_the_end");
				worldTheEndConfig.set("altname", "The End");
				worldTheEndConfig.save(worldTheEnd);
				getLogger().info("\t\033[1;32m- world_the_end  [3] added!\033[0m");
				
				File unknown = new File(getDataFolder() + File.separator + "data" + File.separator + getFolderName() + "_worlds" + File.separator + "4");
				unknown.createNewFile();
				FileConfiguration unknownConfig = new YamlConfiguration();
				
				unknownConfig.load(unknown);
				unknownConfig.set("name", "Unknown");
				unknownConfig.set("altname", "Unknown");
				unknownConfig.save(unknown);
				getLogger().info("\t\033[1;32m- Unknown        [4] added!\033[0m");
			} catch(Exception e) {
	    		getLogger().severe("\t\033[1;31mError adding world profiles!\033[0m");
	    		if (debug()) {
	        		getLogger().severe("\033[1;31m[Debug] Error occured:\033[0m " + e);
	        	}
			}
		}
    }
}