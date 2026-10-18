# SimpleTeams (Paper 26.3)

Player teams with friendly-fire protection.

## Build
Requires JDK 25 and Gradle (or open the folder in IntelliJ IDEA and run the `build` task).

    gradle build

The jar is written to `build/libs/SimpleTeams-1.0.0.jar`. Drop it in your server's `plugins/` folder.

If Gradle can't resolve the Paper API, change the version in `build.gradle.kts`
to the latest 26.3 build from https://papermc.io/downloads/paper.

## Commands (`/team`, alias `/teams`)
create <name>, invite <player>, accept, deny, leave, kick <player>,
transfer <player>, disband, ally <team>, unally <team>, allies, ff [on|off], stats [team], info [team], list

Friendly fire is OFF by default for new teams. The leader can switch it per team with `/team ff on|off`;
any member can check the setting with `/team ff`. The setting is saved in `teams.yml`.

Team stats (`/team stats [team]`): kills = players killed by a team member (teamkills excluded); deaths = team members killed by a player (mob, fall, lava etc. deaths are not counted). Stats belong to the team and are saved in `teams.yml`.

Alliances: a team leader runs `/team ally <team>` to send a request; the other team's leader accepts by running
`/team ally <that team>` (or clicking [Accept]). Requests expire after 10 minutes. `/team unally <team>` ends an
alliance or declines/cancels a request, and `/team allies` lists allies and pending requests. Members of allied
teams can never hurt each other, regardless of either team's friendly-fire setting. Alliances are saved in `teams.yml`.

Vanilla /team: by default (`redirect-vanilla-team: true` in `plugins/SimpleTeams/config.yml`), Minecraft's built-in
`/team` and `/minecraft:team` are redirected to this plugin's `/team`, so players can't use the scoreboard-team
command to add people to teams. Command blocks and datapack functions are not affected.

Building without installing anything: push this folder to a GitHub repository. The included workflow
(.github/workflows/build.yml) builds the jar automatically; download it from the run's "Artifacts" section
(Actions tab -> latest run -> SimpleTeams-jar).
