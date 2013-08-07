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
### Version 1.0.0
- Initial public release!
