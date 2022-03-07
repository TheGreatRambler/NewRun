# NewRun

![logo](images/newrun.png)

A spigot plugin that allows for playing over 50 different challenges and saving them to replays you can view whenever you want. Every challenge is saved to an online leaderboard viewable anywhere.

# Installation
1. Create a Spigot or Paper server newer than 1.8
2. Put the NewRun plugins in your `plugins` folder
3. Start the server whenever you want to play NewRun

# Usage
## `/startchallenge <types> <seed> <players, ...>`
`types` is a list of `:` delimited entries, with the first entry being a generation type. A generation type must currently be either `DEFAULT` or `AMPLIFIED`. After choosing a generation type, you can choose any number of gameplay types, use tab complete to browse the available gameplay types. Press `:` to add a new type. `seed` is either `r` for a random seed or any string or number for a seed. `players` is a list of space delimited users to put in the challenge. Certain gameplay types, like manhunt, work best with multiple players.

## `/stopchallenge <id>`
End the challenge with the given ID, or the current challenge if not provided.

## `/pausechallenge <id>`
Prevent gameplay types in the challenge with the given ID, or the current challenge if not provided.

## `/unpausechallenge <id>`
Unpause the challenge with the given ID, or the current challenge if not provided.

## `/inforeplay <name>`
Print info on the replay with the given name to chat.

## `/playreplay <name>`
Play the replay with the given name.

## `/leavereplay`
Leave the replay you are currently viewing.

## `/downloadreplay <name>`
If online, downloads the replay from the leaderboard with the given name.