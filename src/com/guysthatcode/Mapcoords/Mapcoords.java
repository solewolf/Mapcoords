/*
 * Mapcoords
 * Version 1.0.2
 * Date: Tue Jun 4, 2013 10:20:40 PM
 * Update for multiworld support and database bug fixes
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
	boolean epic_fail, first_run;
	Connection con = null;
    Statement st = null;

    @Override
    public void onDisable() {
    	
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
        
        // Try to connect to the database. Return success message
        // on connect and failure message on failure (duh).
        connect();
		
        // Attempt to set up table
        createTable();
		
		// Attempt to set up table_worlds
        createTableWorlds();
		
		// Purge invalid database entries
        if (!first_run) {
        	//purgeInvalid();
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
			    				if (!epic_fail) {
			    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
			    				}
			    			} finally {
			    				try {
			    					con.close();
			    				} catch (Exception ex) {
			    					
			    				}
			    			}
	    				}
    				}
    				return true;
    			}
    			// Lists map coordinates that have been added in the database
    			else if (args[0].equalsIgnoreCase("list")) {
    					sender.sendMessage("Listing recorded coordinates...");

	    			try {
	    				con = DriverManager.getConnection(url, user, pass);
	    		        con.setAutoCommit(false);
	    				st = con.createStatement();
						String query = "SELECT id, user, name, x, y, z, world FROM " + table + " ORDER BY world, id DESC";
						ResultSet result = st.executeQuery(query);
						
						int a = 0;
						while (result.next()) {
							a++;
							sender.sendMessage("[" + ChatColor.LIGHT_PURPLE + worldIDToString(result.getInt("world")) + ChatColor.WHITE + "] {" + ChatColor.RED + result.getInt("id") + ChatColor.WHITE + "} - " + ChatColor.GREEN + result.getString("name") + ChatColor.WHITE + " at X: " + ChatColor.GOLD + result.getInt("x") + ChatColor.WHITE + " Y: " + ChatColor.GOLD + result.getInt("y") + ChatColor.WHITE + " Z: " + ChatColor.GOLD + result.getInt("z") + ChatColor.WHITE + " by " + ChatColor.AQUA + result.getString("user") + ChatColor.WHITE);
						}
						if (a == 0) {
							sender.sendMessage(ChatColor.GOLD + "No coordinates have been added yet!");
						}
	    			} catch (Exception ex) {
	    				if (!epic_fail) {
	    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
	    				}
	    			} finally {
	    				try {
	    					con.close();
	    				} catch (Exception ex) {
	    					
	    				}
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
    	    				if (!epic_fail) {
    	    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
    	    				}
    	    			} finally {
    	    				try {
    	    					con.close();
    	    				} catch (SQLException ex) {
    	    					
    	    				}
    	    			}
    	    			if (!ids.contains(Integer.parseInt(args[1]))) {
    	    				sender.sendMessage(ChatColor.RED + "Location ID was not found in the database!");
    	    				return false;
    	    			}
    	    			
	    				try {
	    					con = DriverManager.getConnection(url, user, pass);
	    					
	    					con.setAutoCommit(false);
	    					
		    				st = con.createStatement();
		    				
		    				PreparedStatement statement = con.prepareStatement("DELETE FROM " + table + " WHERE id=?");
		    				statement.setInt(1, Integer.parseInt(args[1]));
							statement.executeUpdate();
							
							sender.sendMessage(ChatColor.GOLD + "Location was deleted successfully!");
		    			} catch (SQLException ex) {
		    				if (!epic_fail) {
		    					sender.sendMessage(ChatColor.RED + "Error #1: Failed to connect to database! Is your database configuration set up correctly?");
		    				}
		    			} catch (IndexOutOfBoundsException ex) {
		    				sender.sendMessage("There are no recorded coordinates to delete.");
		    			} finally {
		    				try {
		    					con.close();
		    				} catch (Exception ex) {
		    					
		    				}
		    			}
    				}
    				return true;
    			}
    			// Displays player's current coordinates
	    		else if (args[0].equalsIgnoreCase("coords")) {
	    			Player player = (Player) sender;
	    		    Location loc = player.getLocation();
	    		    int x = loc.getBlockX();
	    		    int y = loc.getBlockY();
	    		    int z = loc.getBlockZ();
	    		    world = player.getLocation().getWorld().getName();
	    		    world_id = stringToWorldID(world);
    				if (!(sender instanceof Player)) {
    					sender.sendMessage("This command can only be run by a player.");
    				} else {
						sender.sendMessage("Your current location is [" + ChatColor.LIGHT_PURPLE + worldIDToString(stringToWorldID(world)) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    				}
    				return true;
    			}
    			// Player says their current coordinates
	    		else if (args[0].equalsIgnoreCase("saycoords")) {
	    			Player player = (Player) sender;
	    		    Location loc = player.getLocation();
	    		    int x = loc.getBlockX();
	    		    int y = loc.getBlockY();
	    		    int z = loc.getBlockZ();
	    		    world = player.getLocation().getWorld().getName();
	    		    world_id = stringToWorldID(world);
    				if (!(sender instanceof Player)) {
    					sender.sendMessage("This command can only be run by a player.");
    				} else {
    				    player.chat("[" + ChatColor.LIGHT_PURPLE + worldIDToString(stringToWorldID(world)) + ChatColor.WHITE + "] X: " + ChatColor.GOLD + x + ChatColor.WHITE + " Y: " + ChatColor.GOLD + y + ChatColor.WHITE + " Z: " + ChatColor.GOLD + z);
    				}
    				return true;
    			} else {
    				sender.sendMessage(ChatColor.GOLD + "Unknown command. Type /mc or /mapcoords for help.");
    				return true;
    			}
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
    /*
     * This method attempts to connect to the database.
     */
    public void connect() {
    	try {
			url = config.getString("database.url");
			table = config.getString("database.table");
	        user = config.getString("database.username");
	        pass = config.getString("database.password");
	        
			con = DriverManager.getConnection(url, user, pass);
			getLogger().info("\033[1;32mConnected to database!\033[0m");
			
			con.setAutoCommit(false);
		} catch (SQLException ex) {
			epic_fail = true;
			getLogger().severe("\033[1;31mError #1: Failed to connect to database! Is your database configuration set up correctly?\033[0m");
		} finally {
			try {
				con.close();
			} catch (Exception ex) {
				
			}
		}
    }
    
    /*
     * This method will create the primary coordinates
     * table. 
     */
    public void createTable() {
    	try {
	        con = DriverManager.getConnection(url, user, pass);
	        con.setAutoCommit(false);
	        
	        DatabaseMetaData metadata = con.getMetaData();
	        
	        ResultSet resultSet;
	        // Checks to see if the table already exists
	        resultSet = metadata.getTables(null, null, table, null);
	        
	        if (!resultSet.next()) {			
				st = con.createStatement();
				
				String sql = "CREATE TABLE IF NOT EXISTS " + table + " (`id` int(6) NOT NULL auto_increment, `user` varchar(25) NOT NULL,  `name` varchar(50) NOT NULL,  `x` int(8) NOT NULL,  `y` int(3) NOT NULL,  `z` int(8) NOT NULL,  `world` int(1) NOT NULL,  PRIMARY KEY  (`id`)) ENGINE=MyISAM DEFAULT CHARSET=latin1 AUTO_INCREMENT=1;"; 
				st.executeUpdate(sql);
				getLogger().info("\033[1;32mMapcoords table '" + table + "' was created successfully!\033[0m");
				first_run = true;
	        }
	        
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #2a: Failed to create '" + table + "' table for first run! You need to setup your database configuration in plugins/Mapcoords/config.yml.\033[0m");
        	}
		} finally {
			try {
				con.close();
			} catch (Exception ex) {
				
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
	        con = DriverManager.getConnection(url, user, pass);
	        con.setAutoCommit(false);
	        
	        DatabaseMetaData metadata = con.getMetaData();
	        
	        ResultSet resultSet;
	        // Checks to see if the table already exists
	        resultSet = metadata.getTables(null, null, table + "_worlds", null);
	        
	        if (!resultSet.next()) {			
				st = con.createStatement();
				
				// Create table_worlds table
				String sql = "CREATE TABLE IF NOT EXISTS " + table + "_worlds (`id` int(6) NOT NULL auto_increment, `name` varchar(25) NOT NULL,  `altname` varchar(25) NOT NULL,  PRIMARY KEY  (`id`)) ENGINE=MyISAM DEFAULT CHARSET=latin1 AUTO_INCREMENT=1"; 
				st.executeUpdate(sql);
				// Insert Default world data for ids 1, 2, 3, and 4
				sql = "INSERT INTO " + table + "_worlds VALUES(null, \"world\", \"Overworld\");"; 
				st.executeUpdate(sql);
				sql = "INSERT INTO " + table + "_worlds VALUES(null, \"world_nether\", \"Nether\");"; 
				st.executeUpdate(sql);
				sql = "INSERT INTO " + table + "_worlds VALUES(null, \"world_the_end\", \"The End\");"; 
				st.executeUpdate(sql);
				sql = "INSERT INTO " + table + "_worlds VALUES(null, \"Unknown\", \"Unknown\");"; 
				st.executeUpdate(sql);
				getLogger().info("\033[1;32mMapcoords table '" + table + "_worlds' was created successfully!\033[0m");
				
				// Purge all invalid records for coordinates before 1.0.2 with world id 3.
				moveInvalidPre();
	        }
	        
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #2b: Failed to create '" + table + "' table for first run! You need to setup your database configuration in plugins/Mapcoords/config.yml.\033[0m");
        	}
		} finally {
			try {
				con.close();
			} catch (Exception ex) {
				
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
	    		con = DriverManager.getConnection(url, user, pass);
		        con.setAutoCommit(false);
				st = con.createStatement();
				
				// Overworld
				String sql = "UPDATE " + table + " SET world=4 WHERE world=3"; 
				st.executeUpdate(sql);
				con = DriverManager.getConnection(url, user, pass);
		        con.setAutoCommit(false);
		        
				getLogger().info("\033[1;32mUnknown coordinate entries (pre 1.0.2) were updated successfully!\033[0m");
	    	} catch (SQLException ex) {
	    		if (!epic_fail) {
	    			getLogger().severe("\033[1;31mError #3: Failed to update unknown coordinate entries (pre 1.0.2)!\033[0m");
	    		}
			} finally {
				try {
					con.close();
				} catch (Exception ex) {
					
				}
			}
			// Update world ids 0,1,2,3 to 1,2,3,4
			if (!first_run) {
				try {
					getLogger().info("Attempting to update world ids to new format");
					con = DriverManager.getConnection(url, user, pass);
			        con.setAutoCommit(false);
					st = con.createStatement();
					
					// Overworld
					String sql = "UPDATE " + table + " SET world=3 WHERE world=2"; 
					st.executeUpdate(sql);
					sql = "UPDATE " + table + " SET world=2 WHERE world=1"; 
					st.executeUpdate(sql);
					sql= "UPDATE " + table + " SET world=1 WHERE world=0"; 
					st.executeUpdate(sql);
					// Nether
					
					// The End
					
					getLogger().info("\033[1;32mWorld ids were updated successfully!\033[0m");
				} catch (SQLException ex) {
					if (!epic_fail) {
						getLogger().severe("\033[1;31mError #4: Failed to update world ids to new format!id data from " + table + " table!\033[0m");
					}
				} finally {
					try {
						con.close();
					} catch (Exception ex) {
						
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
	    			getLogger().info("Attemping to purge invalid data (post 1.0.2) entries from " + table);
	    		}
				con = DriverManager.getConnection(url, user, pass);
		        con.setAutoCommit(false);
		        
		        PreparedStatement statement = con.prepareStatement("DELETE FROM " + table + " WHERE world IN (?,?)");
				statement.setInt(1, 0);
				statement.setInt(2, 4);
				statement.executeUpdate();
				getLogger().info("\033[1;32mInvalid data (post 1.0.2) was purged successfully!\033[0m");
			} catch (SQLException ex) {
				if (!epic_fail) {
					getLogger().severe("\033[1;31mError #3: Failed to purge invalid data (post 1.0.2) from " + table + " table!\033[0m");
				}
			} finally {
				try {
					con.close();
				} catch (Exception ex) {
					
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
	        con = DriverManager.getConnection(url, user, pass);
	        con.setAutoCommit(false);		
	        
	        st = con.createStatement();
			String query = "SELECT name, id FROM " + table + "_worlds WHERE name = '" + world_name + "'";
			ResultSet result = st.executeQuery(query);
			result.next();
			return result.getInt("id");
        } catch (SQLException ex) {
        	// If there was an exception, then that means that this world isn't in the database yet.
        	try {    	        
    	        PreparedStatement statement = con.prepareStatement("INSERT INTO " + table + "_worlds VALUES(null, ?, ?)");
    			statement.setString(1, world_name);
    			statement.setString(2, world_name);
    			statement.executeUpdate();
    			return stringToWorldID(world_name);
            } catch (SQLException exc) {
            	if (!epic_fail) {
            		getLogger().severe("\033[1;31mError #5: Failed to find world id equivalent of supplied world name!\033[0m");
            	}
            	return 4;
            }
		} finally {
			try {
				con.close();
			} catch (Exception ex) {
				
			}
		}
    }
    
    /*
     * This method will convert a worldname into its corresponding
     * world id that will be looked up in the database. If the
     * world id is not found, it'll be automatically created.
     */
    public String worldIDToString(int world_id) {
    	// Search for string in database first
    	try {
	        con = DriverManager.getConnection(url, user, pass);
	        con.setAutoCommit(false);		
	        
	        st = con.createStatement();
			String query = "SELECT name, altname, id FROM " + table + "_worlds WHERE id = '" + world_id + "'";
			ResultSet result = st.executeQuery(query);
			result.next();
			if (result.getString("altname") == null) {
				return result.getString("name");
			} else {
				return result.getString("altname");
			}
        } catch (SQLException ex) {
        	if (!epic_fail) {
        		getLogger().severe("\033[1;31mError #5: Failed to find world name equivalent of supplied world id!\033[0m");
        	}
			return "Unknown";
		} finally {
			try {
				con.close();
			} catch (Exception ex) {
				
			}
		}
    }
}