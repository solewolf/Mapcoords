# Mapcoords plugin for Bukkit Servers

Bukkit URL: http://dev.bukkit.org/bukkit-plugins/mapcoords/

I am a noob at Java and I really need to learn OOP. Sorry :)

With that out of the way, hope this helps someone.

### Description and Changelog as seen on Bukkit

## Mapcoords
Save coordinates! Teleport to saved locations if you want! Find the coordinates of other players!

Instead of having to write down the coordinates of important locations like a ravine, mineshaft, or portals, I decided to make a plugin that could be conveniently accessed by anyone else on the server. This would come in handy especially when the person who had all the coordindates was offline.

This plugin allows player to save coordinates into each of their own private lists. There is also a public lists that is viewable by everyone on the server.

In addition to coordinate saving, you can teleport to your saved coordinates and find the coordinates of other players on the server.

Those are the main features: There is a lot of functionality in this little plugin.

This is my first plugin that I have ever made for Bukkit so feel free to post a ticket if I am doing anything wrong or how I can make something better. Thanks and I hope you enjoy!

## Features
- Supports The Overworld, Nether, and The End!
- Supports servers with multiple worlds
- Flat File & MySQL support
- Private and Public Lists
- Teleport to saved coordinates
- Get directions to saved coordinates
- Find coordinates of other players
- Compass can points towards saved locations

## Commands
Here are the list of commands that are available with Mapcoords.
Please note that you can also use /mc in place of /mapcoords.

- /mc [page]: Lists all available commands
- /mc add [name]: Adds current location to private list
- /mc addp [name]: Adds current location to public list
- /mc delete [id]: Delete saved location id from database
- /mc list [page]: Lists coordinates from your private list
- /mc listp [page]: Lists coordinates from public list
- /mc listo [username] [page]: Lists another player's coordinates list
- /mc coords: Displays your current coordinates
- /mc saycoords: Says your current coordinates in chat
- /mc goto [id]: Gives directions to a location
- /mc find [username]: Finds a current player's location
- /mc tp [id]: Teleports you to location id
- /mc setc [id]: Set compass to point to location
- /mc reset: Reset compass to point to spawn
- /mc publish [id]: Move a private coordinate to the public list. This can't be undone.
- /mc rename [id] [name]: Rename a location

## Configuration
Mapcoords uses MySQL or Flat Files to store coordinates.
If you want to use a MySQL database, you'll have to edit the config.yml file that is generated after you run the plugin for the first time to setup database name, user, and pass. If you want to use flat files, you don't have to set anything up. Mapcoords uses flat files by default.

The config file will look like this
It'll look like this:

```yaml
settings:
  checkForUpdates: true
  permissions: true
  useDatabase: false
  folderName: coords
  debug: false
database:
  url: jdbc:mysql://localhost:3306/DATABASE_NAME
  table: coords
  username: user
  password: pass
```

If you are using a database
- Replace DATABASE with the name of the database that you wish to use.
- The table is set to "coords" by default, but if you wish to use this plugin on multiple servers, you can change the table name to something unique.
- The tables that this plugin will create are: [table_name], [table_name]_users, and [table_name]_worlds. So if my table name was "coords", the tables that would be generated are: coords, coords_users, and coords_worlds.
- The username and passwords options are for a MySQL user that has the appropriate privileges for the database (CREATE, INSERT INTO, DELETE FROM, SELECT).
## Permissions
- mc.*              - All permissions
- mc.add.public    - Add coordinates to public list
- mc.add.private     - Add coordinates to the private list
- mc.list.public   - View coordinates on public list
- mc.list.private    - View coordinates on private list
- mc.list.other     - View coordinates on other player's private list
- mc.delete.public - Delete coordinates from public list
- mc.delete.private  - Delete coordinates from the private list
- mc.delete.other   - Delete other players' private coordinates
- mc.coords         - Use coords command (Even though they can still push F3, but yeah)
- mc.saycoords      - Use saycoords command
- mc.find                - Get location of other players
- mc.goto.public   - Get directions to public saved coordinates
- mc.goto.private    - Get directions to private saved coordinates
- mc.goto.other    - Get directions to other players' saved coordinates
- mc.tp.public     - Teleport to their own saved coordinates
- mc.tp.private      - Teleport to private saved coordinates
- mc.tp.other       - Teleport to other players' saved coordinates
- mc.compass.public - Point the compass at public public coordinates
- mc.compass.private - Point the compass at private coordinates
- mc.compass.other - Point the compass at other players coordinates
- mc.compass.reset - Reset the compass to spawn
- mc.publish.private    - Publish private coordinates to the public list
- mc.publish.other       - Publish other players coordinates to the public list
- mc.rename.public      - Rename public coordinates
- mc.rename.private     - Rename private coordinates
- mc.rename.other       - Rename other players coordinates

## Change Log
### Version 2.0.0
Did I say the last update was big? I really meant that this one is the big one. So big, it went up an entire version number :O!
New Features
- Added support for Flat Files! If you don't have a MySQL server running on your computer, you can now actually use this plugin!
- Added compass command
- Added publish command
- Added rename command
- Added flying speed as an ETA for the goto command
- Added page numbers for help menu. Also, help menu now displays all commands even if you don't have permissions.
- New useDatabase config setting
- New debug setting
- Modified the color scheme a little
- Reduced coding redundancies and messiness saving 5 kb of space (if you care to know).
- A whole lot more permissions for control of what commands your players have access to.

Bug Fixes
- Command listo actually works now.
- Fixed goto bug saying your destination is "unknown" from you. Now it'll say under/above if you are at the correct x/z but not y.
- Fixed tp bug that teleported you into the ground and caused suffocation damage.

### Version 1.1.0
This is the BIG update! Where do I start...
- Added private and public lists!
Each player now has access to their own private coordinates! There is also a public lists that anyone can view if they are given the proper permissions.
- Fixed update bug in plugin.yml configuration
- Added option to disable permissions system

New Commands
- goto command
Tells you how far away a saved location is as well as how long it'll take you to get there via walking or sprinting.
- find command
Tells you are player's location (Their world and x, y, and z coordinates)
- tp command
Teleport to any of your saved locations!

New Permissions
Because of all of the new features, there is a plethora of new permissions that gives you complete control over what your players are allowed to do!

### Version 1.0.3
This is the permissions update! They are here!
- Added permissions support
- Auto update detection! Whenever you start up your server, the script will check for an update. Also, whenever an OP logs on to the server, they will receive an "update available" message.
- Converted player names into player ids and also added a tablename_users table.
- Code is much more clean and maintainable now.

### Version 1.0.2
- Fixed database bug that made the connection with the database timeout.
- Fixed bug that prevented database connection from closing (hint: I forgot to add code that closed connection to the database).
- Added support for multiple worlds! Unfortunately since previous versions of Mapcoords stored worlds that weren't named "world*" as "Unknown", servers that had custom worlds will have to redo those coordinates so that they accurately reflect which world they can be found in.
- Changed numbering system for /mc list. Instead of a numerical order, the list displays the coordinate internal ids. This is to prevent accidental deletion of coordinates. Thus, the /mc delete command will require the coordinate ids now.

### Version 1.0.1
- Added support for The Nether and The End!
- Made the coords and saycoords command say what world you are in.
- List command says what world coordinates are in and also sorts all of the stored coordinates by world type.

### Version 1.0.0
- Initial public release!
