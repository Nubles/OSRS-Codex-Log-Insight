# Codex Log Insight

**Collection log analytics.** Codex Log Insight reads your collection log and answers the questions the log itself never does: how lucky or dry you really are at your kill counts, how long each page has left, and which activities will give you a new slot the soonest.

It is the third plugin in the **Codex** suite, after [Codex Navigator](https://github.com/Nubles/OSRS-Codex-Navigator) and [Codex Guardian](https://github.com/Nubles/OSRS-Codex-Guardian), and shares their look: warm ink, aged gold and the game's own fonts.

<p align="center">
  <img src="docs/screenshots/ledger.png" width="225" alt="Ledger">
  <img src="docs/screenshots/page.png" width="225" alt="A log page">
  <img src="docs/screenshots/quarry.png" width="225" alt="Quarry">
</p>

## What it does

- **Ledger.** Your whole log at a glance: slots owned, pages finished, a luck dial from Parched to Blessed comparing your slots with what an average player would own at your kill counts, and the hours left to finish the log. Below it, the best next hunts, the drops you are driest on, and your recent finds with how lucky each one was.
- **Folios.** Every log page with its progress, its luck verdict, your kill count and the time to the next slot. Open a page for its own dial, the time to finish it, every missing slot with its drop rate and how dry you are on it, and the slots you already own.
- **Quarry.** Every activity ranked by the wait for your next new slot, filtered by combat, minigames, other activities or pets. Guaranteed rewards (a reward shop, a fixed number of completions) are counted exactly rather than as a random roll, and multi-step unlocks are chained in order.
- **New slot plaque.** When a new slot drops, a gilt plaque shows its drop rate, your kill count and how lucky it was. Pets get their own violet plaque. A matching line can go to the chat box too.
- **Your own pace.** Rates start at typical kills per hour for a main or an ironman (picked automatically). As you play, Log Insight times your kill counts, and activities you do often switch to your own pace.

<p align="center">
  <img src="docs/screenshots/plaque.png" width="300" alt="New slot plaque">
  <img src="docs/screenshots/plaque-pet.png" width="300" alt="Pet plaque">
</p>

## Getting started

Open your collection log and press its **Search** button once. Log Insight reads every slot you own from it. Kill counts are read from the log pages you open and from the kill count messages in chat, so open the pages you care about once and the luck numbers fill in.

## How the numbers work

- **Luck** is a percentile: the share of players with your kill counts who would own fewer of these slots than you, with ties split evenly. Above 85% is Blessed, above 65% Fortunate, 35% to 65% Even, below 35% Dry and below 15% Parched.
- **Dry** on a single slot is the chance an average player would already have it by your kill count.
- **Time to the next slot and to finish** race every missing slot of an activity against each other at its kills per hour, so a page with many rare slots is honest about how long the last one takes.

## Rules posture

Log Insight only reads what the game already shows you: your collection log, its page counters and your chat messages. It does not click, change menus, send chat or touch the network. Your progress is saved per account in your RuneLite profile and never leaves your computer.

## Credits

Drop rates and activity rates come from two open-source plugins, used under their BSD licenses: [Log Adviser](https://github.com/SFranciscoSouza/LogAdviser) and [Collection Log Luck](https://github.com/peanubnutter/collection-log-luck). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Development

```bash
./gradlew build   # compile and run tests
./gradlew run     # launch RuneLite with the plugin loaded
python3 tools/build_data.py <log-adviser checkout> <collection-log-luck checkout>   # rebuild the rate data
```
