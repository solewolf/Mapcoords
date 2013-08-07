/*
 * Mapcoords
 * Version 1.0.1
 * Date: Mon Jun 3, 2013 7:18:22 AM
 * Added Nether and The End support
 */
package com.guysthatcode.Mapcoords;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.bukkit.plugin.java.JavaPlugin;

public class Mapcoords extends JavaPlugin {
	// Init configFile vars
    File configFile;
    FileConfiguration config;
    
    // Init. variables for database
	String url, table, user, pass, world;
	int world_id;
	Connection con = null;
    Statement st = null;

    @Override
    public void onDisable() {
    	// Nothing
    }

    @Override
    public void onEnable() {
    	try {
    	    Metrics metrics = new Metrics(this);
    	    metrics.start();
    	} catch (IOException e) {
    	    // Failed to submit the stats :-(
    	}
    	// Load config file
        configFile = new File(getDataFolder(), "config.yml");

        // Check if this is the first time server has run Mapcoords
        try {
            firstRun();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load config
        config = new YamlConfiguration();
        loadYamls();
        
        // Try to connect to the database.
        try {
			url = config.getString("database.url");
			table = config.getString("database.table");
	        user = config.getString("database.username");
	        pass = config.getString("database.password");
	        
			con = DriverManager.getConnection(url, user, pass);
			getLogger().info("\033[1;32mConnected to database!\033[0m");
			
			con.setAutoCommit(false);
		} catch (SQLException ex) {
			getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m");
		}
		
        // Attempt to set up database and table
        try {
	        con = DriverManager.getConnection(url, user, pass);
	        con.setAutoCommit(false);
	        
	        DatabaseMetaData metadata = con.getMetaData();
	        
	        ResultSet resultSet;
	        resultSet = metadata.getTables(null, null, table, null);
	        
	        if (!resultSet.next()) {			
				st = con.createStatement();
				
				String sql = "CREATE TABLE IF NOT EXISTS " + table + " (`id` int(6) NOT NULL auto_increment, `user` varchar(25) NOT NULL,  `name` varchar(50) NOT NULL,  `x` int(8) NOT NULL,  `y` int(3) NOT NULL,  `z` int(8) NOT NULL,  `world` int(1) NOT NULL,  PRIMARY KEY  (`id`)) ENGINE=MyISAM DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;"; 
				st.executeUpdate(sql);
				getLogger().info("\033[1;32mMapcoords table '" + table + "' was created successfully!\033[0m");
	        }
        } catch (SQLException ex) {
			getLogger().severe("\033[1;31mError #2: Failed to create '" + table + "' table for first run! You need to setup your database configuration in plugins/Mapcoords/config.yml.\033[0m");
		}
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    	if (cmd.getName().equalsIgnoreCase("mapcoords") || cmd.getName().equalsIgnoreCase("mc")) {
    		
    		// If there is an argument, it is a valid command
    		if (args.length > 0) {
    			
    			// Adding coordinates command
    			if (args[0].equalsIgnoreCase("add")) {
    				if (!(sender instanceof Player)) {
    					sender.sendMessage("This command can only be run by a player.");
    				} else {
	    				// Not enough arguments for coordinate
	    				if (args.length == 1) {
	    					sender.sendMessage(ChatColor.RED + "Please specify a name for your current location.");
	    				} else {
	    				    Player player = (Player) sender;
	    				    Location loc = player.getLocation();
	    				    int x = loc.getBlockX();
	    				    int y = loc.getBlockY();
	    				    int z = loc.getBlockZ();
	    				    world = player.getLocation().getWorld().getName();
	    				    world_id = stringToWorldID(world);
	    				    
		    				try {
		    					con = DriverManager.getConnection(url, user, pass);
		    					
		    					con.setAutoCommit(false);
		    					
			    				st = con.createStatement();
			    				// Records coods into database
			    				
			    				PreparedStatement statement = con.prepareStatement("INSERT INTO " + table + " VALUES (?, ?, ?, ?, ?, ?, ?)");
			    				statement.setInt(1, 0);
			    				statement.setString(2, player.getPlayerListName());
			    				statement.setString(3, args[1]);
			    				statement.setInt(4, x);
			    				statement.setInt(5, y);
			    				statement.setInt(6, z);
			    				statement.setInt(7, world_id);
								statement.executeUpdate();
								
								sender.sendMessage("Your current location [" + ChatColor.LIGHT_PURPLE + worldIDToString(world_id) + ChatColor.WHITE + "] (X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z + ChatColor.WHITE + ") was saved as " + ChatColor.GREEN + args[1] + ChatColor.WHITE + "!");
			    			} catch (SQLException ex) {
			    				sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
			    			}
	    				}
    				}
    				return true;
    			}
    			// Lists map coordinates that have been added in the database
    			else if (args[0].equalsIgnoreCase("list")) {
    					sender.sendMessage("Listing recorded coordinates...");

	    			try {
	    				st = con.createStatement();
						String query = "SELECT id, user, name, x, y, z, world FROM " + table + " ORDER BY world";
						ResultSet result = st.executeQuery(query);
						
						int a = 0;
						while (result.next()) {
							a++;
							sender.sendMessage(a + ". [" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " at X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE + " by " + ChatColor.AQUA + result.getString("user") + ChatColor.WHITE);
						}
						if (a == 0) {
							sender.sendMessage(ChatColor.GOLD + "No coordinates have been added yet!");
						}
	    			} catch (Exception ex) {
	    				sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
	    			}
	    			return true;
	    		}
    			// Delete coordinates command
	    		else if (args[0].equalsIgnoreCase("delete")) {
    				
    				// Not enough arguments for coordinate
    				if (args.length == 1) {
    					sender.sendMessage(ChatColor.RED + "Please specify the ID number of the coords to delete (use /mapcoords list).");
    				} else {
    					// Gets the ids of the coordinate locations
    	    			ArrayList<Integer> ids = new ArrayList<Integer>();
    	    			try {
    	    				con = DriverManager.getConnection(url, user, pass);
	    					
	    					con.setAutoCommit(false);
	    					
    	    				st = con.createStatement();
    						String query = "SELECT id FROM " + table + " ORDER BY world";
    						ResultSet result = st.executeQuery(query);
    						
    						while (result.next()) {
    							ids.add(result.getInt("id"));
    						}
    	    			} catch (Exception ex) {
    	    				sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
    	    			}
    	    			if ((Integer.parseInt(args[1]) <= 0 || Integer.parseInt(args[1]) > ids.size()) && ids.size() != 0) {
    	    				sender.sendMessage(ChatColor.RED + "Location ID was not found in the database!");
    	    				return false;
    	    			}
    	    			
	    				try {
	    					con = DriverManager.getConnection(url, user, pass);
	    					
	    					con.setAutoCommit(false);
	    					
		    				st = con.createStatement();
		    				
		    				PreparedStatement statement = con.prepareStatement("DELETE FROM " + table + " WHERE id=?");
		    				statement.setInt(1, ids.get(Integer.parseInt(args[1])-1));
							statement.executeUpdate();
							
							sender.sendMessage(ChatColor.GOLD + "Location was deleted successfully!");
		    			} catch (SQLException ex) {
		    				sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
		    			} catch (IndexOutOfBoundsException ex) {
		    				sender.sendMessage("There are no recorded coordinates to delete.");
		    			}
    				}
    				return true;
    			}
    			// Displays player's current coordinates
	    		else if (args[0].equalsIgnoreCase("coords")) {
    				if (!(sender instanceof Player)) {
    					sender.sendMessage("This command can only be run by a player.");
    				} else {
    				    Player player = (Player) sender;
    				    Location loc = player.getLocation();
    				    int x = loc.getBlockX();
    				    int y = loc.getBlockY();
    				    int z = loc.getBlockZ();
    				    
    				    world = player.getLocation().getWorld().getName();
						sender.sendMessage("Your current location is [" + ChatColor.LIGHT_PURPLE + worldIDToString(stringToWorldID(world)) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    				}
    				return true;
    			}
    			// Player says their current coordinates
	    		else if (args[0].equalsIgnoreCase("saycoords")) {
    				if (!(sender instanceof Player)) {
    					sender.sendMessage("This command can only be run by a player.");
    				} else {
    				    Player player = (Player) sender;
    				    Location loc = player.getLocation();
    				    int x = loc.getBlockX();
    				    int y = loc.getBlockY();
    				    int z = loc.getBlockZ();
    				    world = player.getLocation().getWorld().getName();
    				    player.chat("[" + ChatColor.LIGHT_PURPLE + worldIDToString(stringToWorldID(world)) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    				}
    				return true;
    			} else {
    				sender.sendMessage(ChatColor.GOLD + "Unknown command. Type /mc or /mapcoords for help.");
    				return true;
    			}
    			/*
    			 * For goto command in version 1.0.1
    			// Gives players directions to location
	    		else if (args[0].equalsIgnoreCase("to")) {
    				if (!(sender instanceof Player)) {
    					sender.sendMessage("This command can only be run by a player.");
    				} else {
    					
    				    Player player = (Player) sender;
    				    Location loc = player.getLocation();
    				    int x = loc.getBlockX();
    				    int y = loc.getBlockY();
    				    int z = loc.getBlockZ();
    				    sender.sendMessage("Current Location = X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    				    sender.sendMessage("Destination = X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    				}
    				return true;
    			}
    			*/
    		} else {
    			// Output how to use command if user doesn't provide arguments
    			sender.sendMessage("Mapcoords Usage:\n" + ChatColor.GOLD + "/mapcoords" + ChatColor.WHITE + " - Lists all available commands");
    			sender.sendMessage(ChatColor.GOLD + "/mapcoords add [name]" + ChatColor.WHITE + " - Adds player's current location [name] to database");
				sender.sendMessage(ChatColor.GOLD + "/mapcoords delete [id]" + ChatColor.WHITE + " - Delete saved location [id] from database");
				sender.sendMessage(ChatColor.GOLD + "/mapcoords list" + ChatColor.WHITE + " - Lists saved coordinates in database");
				sender.sendMessage(ChatColor.GOLD + "/mapcoords coords" + ChatColor.WHITE + " - Displays player's current coordinates");
				sender.sendMessage(ChatColor.GOLD + "/mapcoords saycoords" + ChatColor.WHITE + " - Makes the player say their current coordinates");
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
    public String worldIDToString(int world_id) {
    	switch(world_id) {
    	case 0:
    		return "Overworld";
    	case 1:
    		return "Nether";
    	case 2:
    		return "End";
    	default:
    		return "Unknown";
    	}
    }
    public int stringToWorldID(String world) {
    	if (world.equalsIgnoreCase("world")) {
    		return 0;
    	}
    	else if (world.equalsIgnoreCase("world_nether")) {
    		return 1;
    	}
    	else if (world.equalsIgnoreCase("world_the_end")) {
    		return 2;
    	} else {
    		return 3;
    	}
    }

}