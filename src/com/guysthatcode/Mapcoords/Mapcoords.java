/*
 * Mapcoords
 * Version 1.1.0
 * Date: Thu Jun 13, 2013 11:36:50 PM
 * Added public/private lists
 * Add support for disabling permissions
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

import com.timvisee.manager.permissionsmanager.PermissionsManager;

public class Mapcoords extends JavaPlugin implements Listener {
	// Permissions manager
	private PermissionsManager pm;
	
	// Init configFile vars
    File configFile;
    FileConfiguration config;
    
    // Init variables
	String world, version, newVersion;
	int world_id, iver, inewver;
	boolean epic_fail, first_run, newUpdate;
	enum DIRECTION {SOUTH, WEST, NORTH, EAST, UNKNOWN};

    @Override
    public void onEnable() {
    	// Check for JDBC driver
    	try {
    		getLogger().info("Looking for JDBC driver...");
    	    Class.forName("com.mysql.jdbc.Driver");
    	    getLogger().info("\033[1;32mDriver was loaded successfully!\033[0m");
    	} catch (ClassNotFoundException e) {
    		getLogger().severe("\033[1;31mDriver was not found! Database connections may not work!\033[0m");
    	}
    	
    	// Setup event listener
    	getServer().getPluginManager().registerEvents(this, this);
    	
    	// Setup the permissions manager
    	setupPermissionsManager();
    	
    	// Start Metrics logging for mcstats
    	try {
    	    Metrics metrics = new Metrics(this);
    	    metrics.start();
    	} catch (IOException e) {
    	    // Failed to submit the stats :-(
    		getLogger().severe("\033[1;31mFailed to start up Metrics (MCStats)!\033[0m");
    	}
    	
    	// Load config file
        configFile = new File(getDataFolder(), "config.yml");

        try {
            firstRun();
        } catch (Exception e) {
        	getLogger().severe("\033[1;31mFailed to run Mapcoords for firstRun configuration!\033[0m");
        }

        // Load config
        config = new YamlConfiguration();
        loadYamls();
        
        // Update config file if it is missing any values
        if (config.get("settings.checkForUpdates") == null) {
            config.set("settings.checkForUpdates", true);
            getLogger().info("Restoring missing settings.checkForUpdates configuration value...");
        }
        if (config.get("settings.permissions") == null) {
            config.set("settings.permissions", true);
            getLogger().info("Restoring missing settings.permissions configuration value...");
        }
        if (config.get("database.url") == null) {
            config.set("database.url", "jdbc:mysql://localhost:3306/DATABASE");
            getLogger().info("Restoring missing database.url configuration value...");
        }
        if (config.get("database.table") == null) {
            config.set("database.table", "coords");
            getLogger().info("Restoring missing database.table configuration value...");
        }
        if (config.get("database.username") == null) {
            config.set("database.username", "user");
            getLogger().info("Restoring missing database.username configuration value...");
        }
        if (config.get("database.password") == null) {
            config.set("database.password", "pass");
            getLogger().info("Restoring missing database.password configuration value...");
        }
        saveYamls();

        
        // Try to connect to the database. Return success message
        // on connect and failure message on failure (duh).
        canConnect();
		
        if (!epic_fail) {
	        // Attempt to set up table
	        createTable();
			
			// Attempt to set up table_worlds
	        createTableWorlds();
	        
	        // Attempt to set up table_users
	        createTableUsers();
	        
        }
        
        // Check for updates
        if (config.getBoolean("settings.checkForUpdates") == true) {
	        if (isInternetReachable()) {
	        	checkForUpdates();
	        } else {
	        	getLogger().info("\033[1;31mFailed to contact update server!\033[0m");
	        }
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    	if (cmd.getName().equalsIgnoreCase("mapcoords")) {
    		
    		// If there isn't an argument, it is not a valid command
    		if (args.length == 0) {
    			// Output how to use command if user doesn't provide arguments
		
    			// If typing /mc from Console, display all commands
				if (!(sender instanceof Player) || !config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.YELLOW + "------- " + ChatColor.WHITE + "Available Mapcoords Commands " + ChatColor.YELLOW + " -------");
	    			sender.sendMessage(ChatColor.GOLD + "/mc:" + ChatColor.WHITE + " Lists all available commands");
	    			sender.sendMessage(ChatColor.GOLD + "/mc add [name]:" + ChatColor.WHITE + " Adds current location to private list");
	    			sender.sendMessage(ChatColor.GOLD + "/mc addpublic/addp [name]:" + ChatColor.WHITE + " Adds current location to public list");
    				sender.sendMessage(ChatColor.GOLD + "/mc delete [id]:" + ChatColor.WHITE + " Delete saved location id from database");
    				sender.sendMessage(ChatColor.GOLD + "/mc list [page]:" + ChatColor.WHITE + " Lists coordinates from your private list");
    				sender.sendMessage(ChatColor.GOLD + "/mc listpublic/listp [page]:" + ChatColor.WHITE + " Lists coordinates from public list");
    				sender.sendMessage(ChatColor.GOLD + "/mc listother/listo [username] [page]:" + ChatColor.WHITE + " Lists another player's coordinates list");
    				sender.sendMessage(ChatColor.GOLD + "/mc coords:" + ChatColor.WHITE + " Displays your current coordinates");
    				sender.sendMessage(ChatColor.GOLD + "/mc saycoords:" + ChatColor.WHITE + " Says your current coordinates in chat");
    				sender.sendMessage(ChatColor.GOLD + "/mc goto [id]:" + ChatColor.WHITE + " Gives directions to a location");
    				sender.sendMessage(ChatColor.GOLD + "/mc find [username]:" + ChatColor.WHITE + " Finds a current player's location");
    				sender.sendMessage(ChatColor.GOLD + "/mc tp [id]:" + ChatColor.WHITE + " Teleports you to location id");
				} else {
	    			sender.sendMessage(ChatColor.YELLOW + "------- " + ChatColor.WHITE + "Available Mapcoords Commands " + ChatColor.YELLOW + " -------");
	    			sender.sendMessage(ChatColor.GOLD + "/mc:" + ChatColor.WHITE + " Lists all available commands");
	    			if (pm.hasPermission((Player) sender, "mc.add.private") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc add [name]:" + ChatColor.WHITE + " Adds current location to private list");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.add.public") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc addpublic/addp [name]:" + ChatColor.WHITE + " Adds current location to public list");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.delete.public") || pm.hasPermission((Player) sender, "mc.delete.private") || pm.hasPermission((Player) sender, "mc.delete.other") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc delete [id]:" + ChatColor.WHITE + " Delete saved location id from database");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.list.private") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc list [page]:" + ChatColor.WHITE + " Lists coordinates from your private list");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.list.public") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc listpublic/listp [page]:" + ChatColor.WHITE + " Lists coordinates from public list");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.list.other") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc listother/listo [username] [page]:" + ChatColor.WHITE + " Lists another player's coordinates list");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.coords") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc coords:" + ChatColor.WHITE + " Displays your current coordinates");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.saycoords") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc saycoords:" + ChatColor.WHITE + " Says your current coordinates in chat");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.goto.public") || pm.hasPermission((Player) sender, "mc.goto.private") || pm.hasPermission((Player) sender, "mc.goto.other") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc goto [id]:" + ChatColor.WHITE + " Gives directions to a location");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.find") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc find [username]:" + ChatColor.WHITE + " Finds a current player's location");
	    			}
	    			if (pm.hasPermission((Player) sender, "mc.tp.public") || pm.hasPermission((Player) sender, "mc.tp.private") || pm.hasPermission((Player) sender, "mc.tp.other") && config.getBoolean("settings.permissions")) {
	    				sender.sendMessage(ChatColor.GOLD + "/mc tp [id]:" + ChatColor.WHITE + " Teleports you to location id");
	    			}
				}
				//sender.sendMessage(ChatColor.GOLD + "/mc perms" + ChatColor.WHITE + " Shows your available permissions");
				return true;
    	    }
			
			// Adding coordinates command [PRIVATE LIST]
			if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("addprivate")) {
				
				// Only players can use this command
				if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
				
				// Check if player is OP or has the proper permission to run command [PRIVATE LIST]
				Player perms = (Player) sender;
				if (!pm.hasPermission(perms, "mc.add.private") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
					// Send message to player that their coordinates have been inserted successfully.
					sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z + ChatColor.WHITE + " saved as " + ChatColor.GREEN + args[1] + ChatColor.WHITE + " to private list.");
    			} catch (SQLException ex) {
    				if (!epic_fail) {
    					// Send error message if there is an error
    					getLogger().severe("\033[1;31m" + ex + "\033[0m");
    				}
    			}
				return true;
			}
			
			// Adding coordinates command [PUBLIC LIST]
			else if (args[0].equalsIgnoreCase("addpublic") || args[0].equalsIgnoreCase("addp")) {
					
				// Only players can use this command
				if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
				
				// Check if player is OP or has the proper permission to run command [PUBLIC LIST]
				Player perms = (Player) sender;
				if (!pm.hasPermission(perms, "mc.add.public") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
					// Send message to player that their coordinates have been inserted successfully.
					sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z + ChatColor.WHITE + " saved as " + ChatColor.GREEN + args[1] + ChatColor.WHITE + " to public list.");
    			} catch (SQLException ex) {
    				if (!epic_fail) {
    					// Send error message if there is an error
    					getLogger().severe("\033[1;31m" + ex + "\033[0m");
    				}
    			}
				return true;
			}
			
			// Lists map coordinates that have been added in the database [PRIVATE LIST]
			else if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("listprivate")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console. [PRIVATE LIST]
				if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.list.private") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
						return true;
					}
				} else {
						sender.sendMessage("This command can only be run by a player.");
						return true;
				}
				
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
					sender.sendMessage(ChatColor.YELLOW + "---" + ChatColor.WHITE + " Listing" + ChatColor.GOLD + " Private " + ChatColor.WHITE + "Coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + resultnum.getInt("COUNT(id)") + ")" + ChatColor.YELLOW + " ---");
					
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
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31Error #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    			}
    			return true;
    		}
			
			// Lists map coordinates that have been added in the database [PUBLIC LIST]
			else if (args[0].equalsIgnoreCase("listpublic") || args[0].equalsIgnoreCase("listp")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console. [PUBLIC LIST]
				if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.list.public") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
						return true;
					}
				}
				
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
					sender.sendMessage(ChatColor.YELLOW + "---" + ChatColor.WHITE + " Listing" + ChatColor.GOLD + " Public " + ChatColor.WHITE + "Coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + resultnum.getInt("COUNT(id)") + ")" + ChatColor.YELLOW + " ---");

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
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m");
    				}
    			}
    			return true;
    		}
			
			// Lists map coordinates of another player
			else if (args[0].equalsIgnoreCase("listother") || args[0].equalsIgnoreCase("listo")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console. [PUBLIC LIST]
				if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.list.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
					if (perms.getPlayerListName().equalsIgnoreCase(args[1]) && !pm.hasPermission(perms, "mc.list.private")) {
						sender.sendMessage(ChatColor.RED + "You don't have permission to view this player's coordinates.");
						return true;
					}
				}
				
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
					sender.sendMessage(ChatColor.YELLOW + "-" + ChatColor.WHITE + " Listing " + ChatColor.GOLD + args[1] + "'s " + ChatColor.WHITE + "Coordinates - Page (" + currentPage + "/" + totalPages + " | Total: " + resultnum.getInt("COUNT(id)") + ")" + ChatColor.YELLOW + " -");

					// Output all records in database
					int a = 0;
					while (result.next()) {
						a++;
						sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + result.getInt("id") + ChatColor.WHITE + "] - " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE);
					}
					
					// No coordinates if a is still 0
					if (a == 0) {
						sender.sendMessage(ChatColor.GOLD + args[1] + " doesn't have any coordinates saved!");
					}
					st.close();
					resultnum.close();
					result.close();
					con.close();
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    			}
    			return true;
    		}
			
			// Delete coordinates command
    		else if (args[0].equalsIgnoreCase("delete")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
	    			Player perms = (Player) sender;
					if (!pm.hasPermission(perms, "mc.delete.public") && !pm.hasPermission(perms, "mc.delete.private") && !pm.hasPermission(perms, "mc.delete.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
    			// If is a player
    			try {
    				Connection con = connection();
    				con.setAutoCommit(false);
    				
    				Statement st = con.createStatement();
    				
    				String query = null;
    				if (sender instanceof Player) {
    	    			Player perms = (Player) sender;
	    				if (pm.hasPermission(perms, "mc.delete.private") || !config.getBoolean("settings.permissions")) {
	    					query = "SELECT id FROM " + getTable() + " WHERE user = " + usernameToUserID(perms.getPlayerListName());
	    				}
	    				if (pm.hasPermission(perms, "mc.delete.public") || !config.getBoolean("settings.permissions")) {
	    					if (query != null) {
	    						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user = 2";
	    					} else {
	    						query = "SELECT id FROM " + getTable() + " WHERE user = 2";
	    					}
	    				}
	    				if (pm.hasPermission(perms, "mc.delete.other") || !config.getBoolean("settings.permissions")) {
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
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    			}
    			
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
    			} catch (SQLException ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    			} catch (IndexOutOfBoundsException ex) {
    				sender.sendMessage("There are no recorded coordinates to delete.");
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
    			Player perms = (Player) sender;
				if (!pm.hasPermission(perms, "mc.coords") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
    			Player perms = (Player) sender;
				if (!pm.hasPermission(perms, "mc.saycoords") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
			
    		// Player says their current coordinates
    		/*
    		else if (args[0].equalsIgnoreCase("perms")) {
    			
    			if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
    			// Check if player is OP or has the proper permission to run command
    			Player perms = (Player) sender;
				if (!pm.hasPermission(perms, "mc.perms") && config.getBoolean("settings.permissions")) {
					sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
					return true;
				}
				// Output permission privileges
				sender.sendMessage("Listing permssions that you have");
				sender.sendMessage(permsColor(perms, "mc.*"));
				sender.sendMessage(permsColor(perms, "mc.perms"));
				sender.sendMessage(permsColor(perms, "mc.add.public"));
				sender.sendMessage(permsColor(perms, "mc.add.private"));
				sender.sendMessage(permsColor(perms, "mc.list.public"));
				sender.sendMessage(permsColor(perms, "mc.list.private"));
				sender.sendMessage(permsColor(perms, "mc.list.other"));
				sender.sendMessage(permsColor(perms, "mc.delete.public"));
				sender.sendMessage(permsColor(perms, "mc.delete.private"));
				sender.sendMessage(permsColor(perms, "mc.delete.other"));
				sender.sendMessage(permsColor(perms, "mc.coords"));
				sender.sendMessage(permsColor(perms, "mc.saycoords"));
				
				return true;
			}
			*/
    		
    		// Finds Coordinates of other players
    		else if (args[0].equalsIgnoreCase("find")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.find") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
	    		    
	    		    sender.sendMessage(args[1] + " is at [" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
	    		    return true;
				} catch (Exception ignore) {
					if (args[1].equalsIgnoreCase("Herobrine")) {
						sender.sendMessage(ChatColor.RED + args[1] + " isn't online...or is he?");
					} else {
						sender.sendMessage(ChatColor.RED + args[1] + " isn't online.");
					}
					return true;
				}
				
			}
    		
    		// Directions to coordinates
    		else if (args[0].equalsIgnoreCase("goto")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.goto.public") && !pm.hasPermission(perms, "mc.goto.private") && !pm.hasPermission(perms, "mc.goto.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
    			Player perms = (Player) sender;
    			try {
    				Connection con = connection();
    				con.setAutoCommit(false);
    				
    				Statement st = con.createStatement();
    				
    				String query = null;
    				if (pm.hasPermission(perms, "mc.goto.private") || !config.getBoolean("settings.permissions")) {
    					query = "SELECT id FROM " + getTable() + " WHERE user = " + usernameToUserID(perms.getPlayerListName());
    				}
    				if (pm.hasPermission(perms, "mc.goto.public") || !config.getBoolean("settings.permissions")) {
    					if (query != null) {
    						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user = 2";
    					} else {
    						query = "SELECT id FROM " + getTable() + " WHERE user = 2";
    					}
    				}
    				if (pm.hasPermission(perms, "mc.goto.other") || !config.getBoolean("settings.permissions")) {
    					if (query != null) {
    						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user <> 2 AND user <> " + usernameToUserID(perms.getPlayerListName());
    					} else {
    						query = "SELECT id FROM " + getTable() + " WHERE user <> 2 AND user <> " + usernameToUserID(perms.getPlayerListName());
    					}
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
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
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
    			
				try {
					// Get the player's current location
					Player player = (Player) sender;
					Location loc = player.getLocation();
	    		    int x = loc.getBlockX();
	    		    int y = loc.getBlockY();
	    		    int z = loc.getBlockZ();
	    		    world = player.getLocation().getWorld().getName();
	    		    world_id = stringToWorldID(world);
	    		    
	    		    
	    		    Connection con = connection();
	    	        con.setAutoCommit(false);
	    	        
	    	        Statement st = con.createStatement();
	    	        
	    			String query = "SELECT * FROM " + getTable() + " WHERE id = " + args[1];
	    			ResultSet result = st.executeQuery(query);
	    			con.commit();
	    			result.next();
	    			sender.sendMessage(ChatColor.YELLOW + "---- " + ChatColor.WHITE + "Now calculating route to " + ChatColor.GOLD + result.getString("name") + ChatColor.YELLOW + " ----");
	    			sender.sendMessage("Current Coords    = X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
	    			sender.sendMessage("Destination Coords = X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z"));
	    			// A^2 + B^2 = C^2 (WOW! The FIRST time I have ever had to use actual formulas in a COMPUTER SCIENCE program. No derivatives yet but they are BOUND to be here soon!!!11!!)
	    			if (world_id != result.getInt("world")) {
	    				sender.sendMessage(ChatColor.RED + "The location you want to go to is literally out of this world. Go to " + worldIDToString(result.getInt("world")) + " first.");
	    				return true;
	    			}
	    			double distance = Math.floor(Math.sqrt(Math.pow(Math.abs(y - result.getInt("y")), 2.0)+Math.pow(Math.sqrt(Math.pow(Math.abs(x - result.getInt("x")), 2.0) + Math.pow(Math.abs(z - result.getInt("z")), 2.0)), 2.0))*100+0.5)/100;
	    			
	    			sender.sendMessage("Distance: " + ChatColor.GREEN + distance + ChatColor.WHITE + " meters");
	    			
	    			double walk_h, walk_m, walk_s, walk_raw;
	    			double sprint_h, sprint_m, sprint_s, sprint_raw;
	    			
	    			walk_raw = (Math.floor((distance/4.3)*100+0.5)/100)+1;
	    			sprint_raw = (Math.floor((distance/5.6)*100+0.5)/100)+1;
	    			
	    			walk_h = TimeUnit.SECONDS.toHours((int)walk_raw);
	    			walk_m = TimeUnit.SECONDS.toMinutes((int)walk_raw) - (TimeUnit.SECONDS.toHours((int)walk_raw)* 60);
	    			walk_s = TimeUnit.SECONDS.toSeconds((int)walk_raw) - (TimeUnit.SECONDS.toMinutes((int)walk_raw) *60);
	    			
	    			sprint_h = TimeUnit.SECONDS.toHours((int)sprint_raw);
	    			sprint_m = TimeUnit.SECONDS.toMinutes((int)sprint_raw) - (TimeUnit.SECONDS.toHours((int)sprint_raw)* 60);
	    			sprint_s = TimeUnit.SECONDS.toSeconds((int)sprint_raw) - (TimeUnit.SECONDS.toMinutes((int)sprint_raw) *60);
	    			
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
	    			if (distance >= 5.6) {
	    				sender.sendMessage(sprint_message);
	    			} else {
	    				sender.sendMessage("Sprinting: " + ChatColor.GOLD + "0 " + ChatColor.WHITE + "seconds");
	    			}
	    			
	    			String heading = null;
	    			/* OLD direction finder. New one uses awesomeness
	    			// If destination x is lower than our current x, head West else head East
	    			if (result.getInt("x") <= x) {
	    				// If destination z is lower than our current z, head North else head South
	    				if (result.getInt("z") < z) {
	    					heading = "northwest"; 
	    				}
	    				else if (result.getInt("z") > z) {
	    					heading = "southwest"; 
	    				} else {
	    					heading = "west";
	    				}
	    			} else {
	    				// If destination z is lower than our current z, head North else head South
	    				if (result.getInt("z") < z) {
	    					heading = "northeast"; 
	    				}
	    				else if (result.getInt("z") > z) {
	    					heading = "southeast"; 
	    				} else {
	    					heading = "east";
	    				}
	    			}
	    			*/
	    			/* New Direction Finder */
	    			int x_diff = Math.abs(x - result.getInt("x"));
	    			int z_diff = Math.abs(z - result.getInt("z"));
	    			int dest_x = result.getInt("x");
	    			int dest_z = result.getInt("z");
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
	    			
	    			sender.sendMessage("Your destination is " + ChatColor.GOLD + heading + ChatColor.WHITE + " from you.");
	    			sender.sendMessage("You are currently facing " + ChatColor.GOLD + getDirection(loc.getYaw(), true) + ChatColor.WHITE + ".");
	    			result.close();
					st.close();
					con.close();
    			} catch (SQLException ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    				
	    		    return true;
				} catch (Exception ignore) {
					return true;
				}
				return true;
			}
			
			// Teleport command
    		else if (args[0].equalsIgnoreCase("tp")) {
    			
    			// Check if player is OP or has the proper permission to run command
    			if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.tp.public") && !pm.hasPermission(perms, "mc.tp.private") && !pm.hasPermission(perms, "mc.tp.other") && config.getBoolean("settings.permissions")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
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
    			Player perms = (Player) sender;
    			try {
    				Connection con = connection();
    				con.setAutoCommit(false);
    				
    				Statement st = con.createStatement();
    				
    				String query = null;
    				if (pm.hasPermission(perms, "mc.tp.private") || !config.getBoolean("settings.permissions")) {
    					query = "SELECT id FROM " + getTable() + " WHERE user = " + usernameToUserID(perms.getPlayerListName());
    				}
    				if (pm.hasPermission(perms, "mc.tp.public") || !config.getBoolean("settings.permissions")) {
    					if (query != null) {
    						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user = 2";
    					} else {
    						query = "SELECT id FROM " + getTable() + " WHERE user = 2";
    					}
    				}
    				if (pm.hasPermission(perms, "mc.tp.other") || !config.getBoolean("settings.permissions")) {
    					if (query != null) {
    						query = query + " UNION SELECT id FROM " + getTable() + " WHERE user <> 2 AND user <> " + usernameToUserID(perms.getPlayerListName());
    					} else {
    						query = "SELECT id FROM " + getTable() + " WHERE user <> 2 AND user <> " + usernameToUserID(perms.getPlayerListName());
    					}
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
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    			}
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
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m" + ex);
    				}
    				
	    		    return true;
				}    			
    			return true;
    		}
    		
    		
    		else {
				sender.sendMessage(ChatColor.RED + "Unknown command. Type /mc for help.");
				return true;
			}
    	}
    	return false;
    }

    private void firstRun() throws Exception {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            copy(getResource("config.yml"), configFile);
        }
    }
    
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
            e.printStackTrace();
        }
    }

    public void loadYamls() {
        try {
            config.load(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveYamls() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
/* Custom Methods */
    /*
     * This method attempts to connect to the database.
     */
    public void canConnect() {
    	String url = config.getString("database.url");
        String user = config.getString("database.username");
        String pass = config.getString("database.password");
        
    	Connection connection = null;
    	try {
    	    connection = DriverManager.getConnection(url, user, pass);
    	    getLogger().info("\033[1;32mConnected to Database!\033[0m");
    	} catch (SQLException e) {
    		getLogger().severe("\033[1;31mFailed to connect to the database! Error code was: " + e + "\033[0m");
    		epic_fail = true;
    	} finally {
    	    if (connection != null) try { connection.close(); } catch (SQLException ignore) {}
    	}
    }
    
    /*
     * This method will create the primary coordinates
     * table. 
     */
    public void createTable() {
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
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #2a: Failed to create '" + getTable() + "' table for first run! You need to setup your database configuration in plugins/Mapcoords/config.yml.\033[0m");
        	}
		}
    }
    
    /*
     * This method will attempt to create a worlds table. It will
     * also insert the default values for the 3 worlds: world,
     * world_nether, and world_the_end and it will also create a
     * 3rd world for unknown worlds. Unknown worlds have id 4.
     */
    public void createTableWorlds() {
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
	        }
	        
	        // Close statement and database connection
	        resultSet.close();
	        st.close();
	        con.close();
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #2b: Failed to create '" + getTable() + "'_worlds table for first run! You need to setup your database configuration in plugins/Mapcoords/config.yml.\033[0m");
        	}
		}
    }
    
    /*
     * This method will attempt to create a users table.
     * Whenever a new user tries to add an ID, they will
     * be given a unique user id that will be paired
     * up with their coordinates that they add for support
     * of public and private coordinates in the future.
     */
    public void createTableUsers() {
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
	        }
	        
	        // Close statement and database connection
	        resultSet.close();
	        st.close();
	        con.close();
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #2c: Failed to create '" + getTable() + "'_users table for first run! You need to setup your database configuration in plugins/Mapcoords/config.yml.\033[0m");
        	}
		}
    }
    
    /*
     * This method will move all invalid coordinates that were
     * created pre 1.0.2 that were generated with the world id 3 
     * to world id 4
     * World id 3 is reserved for unknown worlds < 1.0.2.
     */
    public void moveInvalidPre() {
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
	    	} catch (SQLException ex) {
	    		if (!epic_fail) {
	    			getLogger().severe("\033[1;31mError #3: Failed to update unknown coordinate entries (pre 1.0.2)!\033[0m");
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
				} catch (SQLException ex) {
					if (!epic_fail) {
						getLogger().severe("\033[1;31mError #4: Failed to update world ids to new format!id data from " + getTable() + " table!\033[0m");
					}
				}
			}
    	}
    }
    
    /*
     * This method will purge all invalid coordinates that are
     * created after 1.0.2 with the world id 4.
     * World id 4 is reserved for unknown worlds >= 1.0.2.
     * Currently this will be inactive unless it needs to be used
     * in the future.
     */
    public void purgeInvalid() {
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
			} catch (SQLException ex) {
				if (!epic_fail) {
					getLogger().severe("\033[1;31mError #3: Failed to purge invalid data (post 1.0.2) from " + getTable() + " table!\033[0m");
				}
			}
    	}
    }
    
    /*
     * This method will convert a worldname into its corresponding
     * world id that will be looked up in the database. If the
     * world id is not found, it'll be automatically created.
     */
    public int stringToWorldID(String world_name) {
    	// Search for string in database first
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
        } catch (SQLException ex) {
        	// If there was an exception, then that means that this world isn't in the database yet.
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
            } catch (SQLException exc) {
            	if (!epic_fail) {
            		getLogger().severe("\033[1;31mError #5: Failed to find world id equivalent of supplied world name!\033[0m");
            	}
            	return 4;
            }
		}
    }
    
    /*
     * This method will convert a worldname into its corresponding
     * world id that will be looked up in the database.
     */
    public String worldIDToString(int world_id) {
    	// Search for string in database first
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
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #5: Failed to find world name equivalent of supplied world id!\033[0m");
        	}
			return "Unknown";
		}
    }
    
    public String worldIDToString(int world_id, boolean internal_name) {
    	// Search for string in database first
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
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #5: Failed to find world name equivalent of supplied world id!\033[0m");
        	}
			return "Unknown";
		}
    }
    
	/*
    * Setup the permissions manager
    */
    public void setupPermissionsManager() {
	    // Setup the permissions manager
	    this.pm = new PermissionsManager(this.getServer(), this);
	    this.pm.setup();
    }

    /*
    * Get the permissions manager
    * @return permissions manager
    */
    public PermissionsManager getPermissionsManager() {
    	return this.pm;
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
	    	        if (iver < inewver) {
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
    public int usernameToUserID(String username) {
    	// Search for string in database first
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
    }
    
    /*
     * This method will convert a username into their corresponding
     * user id that will be looked up in the database.
     */
    public String userIDToUsername(int userid) {
    	// Search for string in database first
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
    }
    
    /*
     * This method will move convert usernames in the coords table
     * into user ids. This is for the future when public and
     * private coordinate lists will be added.
     */
    public void updateUserValues() {
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
    
    public void checkForUpdates() {
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
	        if (iver < inewver) {
	        	getLogger().info("\033[1;35mThere is a new version of Mapcoords available :D\033[0m");
	        	getLogger().info("\033[1;35mDownload it from http://dev.bukkit.org/bukkit-mods/mapcoords/\033[0m");
	        	getLogger().info("Your version: \033[1;31m" + version + "\033[0m");
	        	getLogger().info("New version : \033[1;32m" + newVersion + "\033[0m");
	        	newUpdate = true;
	        } else {
	        	getLogger().info("No new updates available...");
	        }
        } catch (Exception ex) {
        	getLogger().severe("\033[1;31m" + ex + "\033[0m");
        }
    }
    
    public Connection connection() {
    	String url = config.getString("database.url");
        String user = config.getString("database.username");
        String pass = config.getString("database.password");
        
    	Connection connection = null;
    	try {
    	    connection = DriverManager.getConnection(url, user, pass);
    	} catch (SQLException e) {
    		getLogger().severe("\033[1;31mFailed to connect to the database! Error code was: " + e + "\033[0m");
    	}
    	return connection;
    }
    
    public String getTable() {
    	return config.getString("database.table");
    }
    
    @SuppressWarnings({"unused"})
    public static boolean isInternetReachable()
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
    
    public String permsColor(Player p, String perms) {
    	if (pm.hasPermission(p, perms)) {
    		return perms + ": " + ChatColor.GREEN + "yes" + ChatColor.WHITE;
    	} else {
    		return perms + ": " + ChatColor.RED + "no" + ChatColor.WHITE;
    	}
    }
    
    public boolean doesUsernameExist(String username) {
    	try {
    		Connection con = connection();
    		con.setAutoCommit(false);
    		
			Statement st = con.createStatement();
			
			String query = "SELECT COUNT(*) FROM test_users WHERE username = '" + username + "'";
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
    		return false;
		}
    }
    
    public int getDirection(Float yaw) {
        yaw = yaw / 90;
        yaw = (float)Math.round(yaw);
        if (yaw == -4 || yaw == 0 || yaw == 4) {
        	return 0;
        }
        else if (yaw == -1 || yaw == 3) {
        	return 3;
        }
        else if (yaw == -2 || yaw == 2) {
        	return 2;
        }
        else if (yaw == -3 || yaw == 1) {
        	return 1;
        } else {
        	return 4;
        }
    }
    
    public String getDirection(float yaw, boolean name) {
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
    	/*
        yaw = (float) (Math.floor(Math.abs((float)yaw)*100+0.5)/100);
        System.out.println(yaw);
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
        */
        /*
        if (yaw >= 60 && yaw < 30) {
        	return "southeast";
        }
        else if (yaw >= -120 && yaw < -60) {
        	return "east";
        }
        else if (yaw >= -150 && yaw < -120) {
        	return "northeast";
        }
        else if (yaw >= -210 && yaw < -150) {
        	return "north";
        }
        else if (yaw >= -240 && yaw < -210) {
        	return "northwest";
        }
        else if (yaw >= -300 && yaw < -240) {
        	return "west";
        }
        else if (yaw >= -330 && yaw < -300) {
        	return "southwest";
        } else {
        	return "south";
        }
        */
    }
    
    @SuppressWarnings({"unused"})
    public static boolean isNumeric(String str) {  
    	try {  
    		double d = Double.parseDouble(str);  
    	} catch(NumberFormatException nfe) {    
    		return false;  
    	}
	  return true;  
	}
}