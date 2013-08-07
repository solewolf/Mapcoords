/*
 * Mapcoords
 * Version 1.0.3
 * Date: Fri Jun 7, 2013 4:27:20 PM
 * This is the permissions update!
 * Also added update detection script!
 * And converted players into ids for future public/private lists update
 * And cleaned up code
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
    	    			sender.sendMessage("Mapcoords Usage:");
    	    			sender.sendMessage(ChatColor.GOLD + "/mapcoords" + ChatColor.WHITE + " - Lists all available commands");
    	    			sender.sendMessage(ChatColor.GOLD + "/mapcoords add [name]" + ChatColor.WHITE + " - Adds player's current location [name] to database");
    					sender.sendMessage(ChatColor.GOLD + "/mapcoords delete [id]" + ChatColor.WHITE + " - Delete saved location [id] from database");
    					sender.sendMessage(ChatColor.GOLD + "/mapcoords list" + ChatColor.WHITE + " - Lists saved coordinates in database");
    					sender.sendMessage(ChatColor.GOLD + "/mapcoords coords" + ChatColor.WHITE + " - Displays player's current coordinates");
    					sender.sendMessage(ChatColor.GOLD + "/mapcoords saycoords" + ChatColor.WHITE + " - Makes the player say their current coordinates");
    					return true;
    	    }
			
			// Adding coordinates command [PUBLIC LIST]
			if (args[0].equalsIgnoreCase("add")) {
				
				// Only players can use this command
				if (!(sender instanceof Player)) {
					sender.sendMessage("This command can only be run by a player.");
					return true;
				}
				
				// Check if player is OP or has the proper permission to run command [PUBLIC LIST]
				Player perms = (Player) sender;
				if (!pm.hasPermission(perms, "mc.add.public")) {
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
					sender.sendMessage("Your current location [" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] (X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z + ChatColor.WHITE + ") was saved as " + ChatColor.GREEN + args[1] + ChatColor.WHITE + "!");
    			} catch (SQLException ex) {
    				if (!epic_fail) {
    					// Send error message if there is an error
    					getLogger().severe("\033[1;31m" + ex + "\033[0m");
    				}
    			}
				return true;
			}
			
			// Lists map coordinates that have been added in the database [PUBLIC LIST]
			else if (args[0].equalsIgnoreCase("list")) {
				
				// Check if player is OP or has the proper permission to run command. Also make sure that sender is a user and not console. [PUBLIC LIST]
				if (sender instanceof Player) {
					Player perms = (Player) sender;
					
					if (!pm.hasPermission(perms, "mc.list.public")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
						return true;
					}
				}
				
				// Try retrieve coordinates from database
				sender.sendMessage("Listing recorded coordinates...");
    			try {
    				Connection con = connection();
    				con.setAutoCommit(false);
    				
    				Statement st = con.createStatement();
					String query = "SELECT id, user, name, x, y, z, world FROM " + getTable() + " ORDER BY world, id DESC";
					ResultSet result = st.executeQuery(query);
					con.commit();
					
					// Output all records in database
					int a = 0;
					while (result.next()) {
						a++;
						sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] [" + ChatColor.RED + result.getInt("id") + ChatColor.WHITE + "] - " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " at X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE + " by " + ChatColor.AQUA + userIDToUsername(result.getInt("user")) + ChatColor.WHITE);
					}
					
					// No coordinates if a is still 0
					if (a == 0) {
						sender.sendMessage(ChatColor.GOLD + "No coordinates have been added yet!");
					}
					st.close();
					result.close();
					con.close();
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
    				}
    			}
    			return true;
    		}
			
			// Delete coordinates command [PUBLIC LIST]
    		else if (args[0].equalsIgnoreCase("delete")) {
    			
    			// Check if player is OP or has the proper permission to run command [PUBLIC LIST]
    			if (sender instanceof Player) {
	    			Player perms = (Player) sender;
					if (!pm.hasPermission(perms, "mc.delete.public")) {
						sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
						return true;
					}
    			}
				
				// Not enough arguments for coordinate
				if (args.length == 1) {
					sender.sendMessage(ChatColor.RED + "Please specify the ID number of the coords to delete (use /mapcoords list).");
					return true;
				}
				
				// Gets the ids of the coordinate locations
    			ArrayList<Integer> ids = new ArrayList<Integer>();
    			try {
    				Connection con = connection();
    				con.setAutoCommit(false);
    				
    				Statement st = con.createStatement();
					String query = "SELECT id FROM " + getTable() + " ORDER BY world";
					ResultSet result = st.executeQuery(query);
					con.commit();
					
					// Add all ids to id ArrayList
					while (result.next()) {
						ids.add(result.getInt("id"));
					}
					
					st.close();
					result.close();
					con.close();
    			} catch (Exception ex) {
    				if (!epic_fail) {
    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
    				}
    			}
    			
    			// If id not found in ArrayList, then the ID doesn't exist.
    			if (!ids.contains(Integer.parseInt(args[1]))) {
    				sender.sendMessage(ChatColor.RED + "Location ID was not found in the database!");
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
    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
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
				if (!pm.hasPermission(perms, "mc.coords")) {
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
				sender.sendMessage("Your current location is [" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
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
				if (!pm.hasPermission(perms, "mc.saycoords")) {
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
			} else {
				sender.sendMessage(ChatColor.GOLD + "Unknown command. Type /mc or /mapcoords for help.");
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
}