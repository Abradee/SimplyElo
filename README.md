## About

This is a fairly simple, lightweight, and intuitive plugin for your Paper 26.2+ server that allows players to have elo. You can gain and lose elo by getting kills or deaths.

## How to use

This plugin is very easy to use, but has one step that might not be too simple. 

1. Download the plugin for your version of Paper/Purpur.
2. Download PlaceholderAPI for your version of Paper/Purpur.
2. Upload the `.jar` to your `/server/plugins/` folder.
3. Start your server.
4. Now, to view your starting elo, either do `/papi parse me %simplyelo_elo%`, or `/viewelo`
5. You may also configure the plugin at `/server/plugins/SimplyElo/config.yml`

Now, if you want to get these on a scoreboard for people to see, follow these steps (tuned specifically for TAB scoreboard)

1. Make sure you have SimplyElo, PAPI, and TAB (or another scoreboard plugin of your choice) installed on your server
2. Locate TAB's config at `/server/plugins/TAB/config.yml`
3. Locate the `scoreboard` section and make it how you want it, plus include `%simplyelo_elo%` where you want elo to be displayed.

## Commands, Permissions, and Placeholders
### Commands
- `/reloadelo` reloads the plugin.
- `/viewelo [player]` shows the elo of a specified player.
- `/resetelo [player]` resets the elo of a specified player.
- `/setrank [player]` sets the rank of the specified player.

### Permissions
- `simplyelo.admin.reset` is for `/resetelo` (operators only)
- `simplyelo.commands.viewelo` is for `/viewelo` (default) 
- `simplyelo.admin.reload` is for `/reloadelo` (operators only)
- `simplyelo.admin.setrank` is for `/setrank` (operators only)

### Placeholders

|**Placeholder**|**Description**|**Example Output**|
|---|---|---|
|`%simplyelo_elo%`|Player's current Elo score|`1420`|
|`%simplyelo_rank%`|Player's colored rank tier name|`Gold`|
|`%simplyelo_tier%`|Alias for `%simplyelo_rank%`|`Gold`|
|`%simplyelo_rank_icon%`|Player's colored rank icon/symbol|`★`|
|`%simplyelo_rank_emoji%`|Alias for `%simplyelo_rank_icon%`|`★`|
|`%simplyelo_rank_tag%`|Formatted bracketed rank tag|`[★ Gold]`|
|`%simplyelo_position%`|Player's numeric rank position on the leaderboard|`1`|
|`%simplyelo_top%`|Alias for `%simplyelo_position%`|`1`|

#### Leaderboard Placeholders

| **Placeholder**                            | **Description**                                           | **Example Output** |
| ------------------------------------------ | --------------------------------------------------------- | ------------------ |
| `%simplyelo_leaderboard_<placement>%`      | Name of the player at the given leaderboard position      | `Notch`            |
| `%simplyelo_leaderboard_<placement>_name%` | Explicit placeholder for the player's name                | `Notch`            |
| `%simplyelo_leaderboard_<placement>_elo%`  | Elo score of the player at the given leaderboard position | `2150`             |


## Default Config
```yaml
settings:
  # basic starting elo
  default-elo: 1000
  # determines how much Elo changes (higher = more volatile)
  k-factor: 32

ranks:
  UNRANKED:
    display-name: "Unranked"
    color: "&7"
    icon: "✧"
    min-elo: 0
  BRONZE:
    display-name: "Bronze"
    color: "&c"
    icon: "✦"
    min-elo: 800
  SILVER:
    display-name: "Silver"
    color: "&f"
    icon: "✦"
    min-elo: 1200
  GOLD:
    display-name: "Gold"
    color: "&e"
    icon: "★"
    min-elo: 1400
  PLATINUM:
    display-name: "Platinum"
    color: "&b"
    icon: "★"
    min-elo: 1600
  DIAMOND:
    display-name: "Diamond"
    color: "&9"
    icon: "✪"
    min-elo: 1800
  MASTER:
    display-name: "Master"
    color: "&d"
    icon: "❂"
    min-elo: 2000
```

## Example TAB Config
to implement this on a scoreboard in `/server/plugins/TAB/config.yml`:
```yaml
    scoreboard:
      title: "<#E0B11E>MyServer</#FF0000>"
      lines:
        - "&7%date%"
        - "%animation:MyAnimation1%"
        - "&6Online:"
        - "* &eOnline&7: &f%online%"
        - "* &eCurrent World&7: &f%worldonline%"
        - "* &eStaff&7: &f%staffonline%"
        - ""
        - "&6Personal Info:"
        - "* &bRank&7: &f%group%"
        - "* &bPing&7: &f%ping%&8ms"
        - "* &bElo&7: &f%simplyelo_elo%" # Here is your elo
        - "* &bWorld&7: &f%world%"
        - "%animation:MyAnimation1%"
```
