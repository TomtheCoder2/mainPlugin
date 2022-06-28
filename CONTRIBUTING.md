# Code Guidelines
The main goal of these guidelines is to modularize the code, so that one part of the code does not depend on all of the others.

The dependency structure of the code should look something like:
 - Main class
 - MiniMods
 - Utils, Database, DiscordRegistrar

1. Place all discord roles and channels in `Roles` and `Channels` respectively; and load them from `settings.json`. Do not have random floating channel IDs at random places in the code.
