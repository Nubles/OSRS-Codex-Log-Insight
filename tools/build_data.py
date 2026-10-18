#!/usr/bin/env python3
"""Builds src/main/resources/com/codex/loginsight/codex-log.json.

Usage: tools/build_data.py <LogAdviser checkout> <collection-log-luck checkout>

Rates, pace and skill/quest requirements come from Log Adviser's activity tables (BSD 2-Clause,
SFranciscoSouza). Per-kill-count odds for the luck read come from Collection Log
Luck's item table (BSD 2-Clause, peanubnutter). See THIRD_PARTY_NOTICES.md.
"""
import json
import re
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / 'src/main/resources/com/codex/loginsight/codex-log.json'

CATEGORIES = {'combat': 'C', 'minigame': 'M', 'miscellaneous': 'O'}
EXACT, INDEPENDENT, SEQUENTIAL = 1, 2, 4


def activities(adviser):
    data = adviser / 'src/main/resources/com/logadviser/data'
    acts = json.load(open(data / 'activities.json'))
    rows = json.load(open(data / 'activity_map.json'))
    pets = [p['itemId'] for p in json.load(open(data / 'pets.json'))]
    needs = json.load(open(data / 'activity_requirements.json'))
    by_index = {}
    for a in acts:
        by_index[a['index']] = {
            'n': a['name'][0].upper() + a['name'][1:],
            'c': CATEGORIES.get(a['category'], 'O'),
            'm': a['completionsPerHrMain'],
            'i': a['completionsPerHrIron'],
            'f': a['extraTimeFirst'],
            'd': [],
        }
        need = needs.get(str(a['index']), {})
        if need.get('skills') or need.get('quests'):
            by_index[a['index']]['r'] = {'s': need.get('skills', {}), 'q': need.get('quests', [])}
    for r in rows:
        a = by_index.get(r['activityIndex'])
        if a is None or r['dropRateAttempts'] <= 0:
            continue
        flags = (EXACT if r['exact'] else 0) | (INDEPENDENT if r['independent'] else 0) | (SEQUENTIAL if r['requiresPrevious'] else 0)
        a['d'].append([r['itemId'], round(r['dropRateAttempts'], 3), flags])
    out = [a for _, a in sorted(by_index.items()) if a['d']]
    return out, sorted(set(pets))


ROLL = re.compile(r'new RollInfo\(\s*LogItemSourceInfo\.(\w+)\s*,\s*([^,()]+(?:\([^()]*\))?[^,()]*)\s*(?:,\s*(\d+))?\s*\)')
ITEM = re.compile(r'public static LogItemInfo \w+ = new LogItemInfo\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*(\d+)\s*,(.*?)\);\s*(?=public static|private|//|\Z)', re.S)
SIMPLE = {'BinomialDrop', 'PoissonBinomialDrop', 'FixedStackDrop', 'FiniteBinomialDrop', 'BinomialUniformSumDrop',
          'DupeProtectedSetBinomialDrop', 'DupeProtectedFirstSetBinomialDrop', 'InterchangeableSetBinomialDrop',
          'InterchangeableSetFiniteBinomialDrop', 'SinglePityBinomialDrop', 'GuaranteedOnceBinomialDrop'}


# Collection Log Luck scales some odds by a setting (raid points, team share). Log Insight
# applies that plugin's defaults, which describe a typical player.
SCALE = {
    ('AVG_PERSONAL_COX_POINTS_KEY', 'Chambers of Xeric completions'): 30000 / 867600,
    ('AVG_PERSONAL_COX_CM_POINTS_KEY', 'Chambers of Xeric (CM) completions'): 45000 / 867600,
    ('AVG_PERSONAL_TOB_POINTS_KEY', 'Theatre of Blood completions'): 0.25,
    ('AVG_PERSONAL_TOB_HM_POINTS_KEY', 'Theatre of Blood (Hard) completions'): 0.2,
    ('ENTRY_TOA_UNIQUE_CHANCE_KEY', 'Tombs of Amascut (Entry) completions'): 0.0076,
    ('REGULAR_TOA_UNIQUE_CHANCE_KEY', 'Tombs of Amascut completions'): 0.0202,
    ('EXPERT_TOA_UNIQUE_CHANCE_KEY', 'Tombs of Amascut (Expert) completions'): 0.0440,
    ('AVG_NEX_CONTRIBUTION_KEY', 'Nex kills'): 0.204,
    ('AVG_HUEYCOATL_CONTRIBUTION_KEY', 'Hueycoatl kills'): 0.367,
    ('AVG_ROYAL_TITANS_CONTRIBUTION_KEY', 'Royal Titan kills'): 0.5,
    ('AVG_CALLISTO_CONTRIBUTION_KEY', 'Callisto kills'): 0.2,
    ('AVG_VENENATIS_CONTRIBUTION_KEY', 'Venenatis kills'): 0.5,
    ('AVG_VETION_CONTRIBUTION_KEY', "Vet'ion kills"): 0.5,
}
TOA_PET = {'Tombs of Amascut (Entry) completions': 0.0076, 'Tombs of Amascut completions': 0.0202,
           'Tombs of Amascut (Expert) completions': 0.0440}
# Settings whose effect depends on team size or points in a way too uncertain to assume.
SKIP = {'AVG_NIGHTMARE_TEAM_SIZE_KEY', 'AVG_NIGHTMARE_CONTRIBUTION_KEY', 'AVG_ZALCANO_POINTS_KEY',
        'Jar of dreams', 'Little nightmare'}


# Some items split one counter into stretches with different odds: kills before and after a
# drop-rate buff, the first Jad or Zuk kill off task, cape sacrifices, Maggot King eggs taken.
# Collection Log Luck asks the player for the size of each stretch and defaults them so every
# kill falls in one. Log Insight cannot ask, so it keeps only that roll (by its index in the
# item's roll list) instead of adding every stretch's odds to every kill.
SEGMENT_ROLLS_DROPPED = {
    'KBD_KC_PRE_D_PICK_BUFF_KEY': {0},
    'SKOTIZO_KC_PRE_BUFF_KEY': {0},
    'NIGHTMARE_KC_PRE_BUFF_KEY': {0},
    'PHOSANIS_NIGHTMARE_KC_PRE_BUFF_KEY': {2},
    'SHELLBANE_GRYPHON_KC_PRE_BUFF_KEY': {0, 1},
    'NUM_FIRE_CAPES_SACRIFICED_KEY': {0, 2},
    'NUM_INFERNAL_CAPES_SACRIFICED_KEY': {0, 2},
    'Maggot marquess': {1},
}


def toa_pet(unique):
    return 9.266e-02 * unique * unique + 2.539e-02 * unique + 1.269e-04


def arithmetic(expr):
    expr = expr.strip().replace('_', '')
    if not re.fullmatch(r'[0-9.\s/*+\-()]+', expr):
        raise ValueError(expr)
    return float(eval(expr, {'__builtins__': {}}))


def luck(luck_repo):
    base = luck_repo / 'src/main/java/com/peanubnutter/collectionlogluck/luck'
    labels = {}
    for m in re.finditer(r'^\s*(\w+)\("([^"]+)"\)', (base / 'LogItemSourceInfo.java').read_text(), re.M):
        labels[m.group(1)] = m.group(2)
    text = (base / 'LogItemInfo.java').read_text()
    # Drop commented-out lines so retired formulas never leak in.
    text = '\n'.join(line for line in text.splitlines() if not line.strip().startswith('//'))
    label_list = sorted(set(labels.values()))
    label_index = {l: i for i, l in enumerate(label_list)}
    items = {}
    for m in ITEM.finditer(text):
        item_id = int(m.group(2))
        body = m.group(3)
        kind = re.search(r'new (\w+Drop)\(', body)
        if not kind or kind.group(1) not in SIMPLE:
            continue
        options = set(re.findall(r'withConfigOption\((?:CollectionLogLuckConfig|LogItemInfo)\.(\w+)', body))
        options |= set(re.findall(r'withConfigOption\("([^"]+)"\)', body))
        if options & SKIP:
            continue
        dropped = set()
        for option in options:
            dropped |= SEGMENT_ROLLS_DROPPED.get(option, set())
        rolls = []
        for index, r in enumerate(ROLL.finditer(body)):
            if index in dropped:
                continue
            label = labels[r.group(1)]
            p = arithmetic(r.group(2))
            if "Tumeken's guardian" in options and label in TOA_PET:
                p = toa_pet(TOA_PET[label])
            for (option, scaled), factor in SCALE.items():
                if option in options and scaled == label:
                    p *= factor
            if not 0 < p <= 1:
                continue
            rolls.append([label_index[label], float('%.6g' % p), int(r.group(3) or 1)])
        if not rolls:
            continue
        seen = [r[0] for r in rolls]
        if len(seen) != len(set(seen)):
            print(f'warning: {m.group(1)} rolls one counter twice; check it is not split into stretches',
                  file=sys.stderr)
        entry = {'r': rolls}
        if kind.group(1) == 'GuaranteedOnceBinomialDrop':
            g = re.search(r'\)\s*,\s*(\d+)\s*\)\s*$', body.strip())
            if g:
                entry['g'] = int(g.group(1))
        items[str(item_id)] = entry
    return label_list, items


def main():
    adviser, luck_repo = Path(sys.argv[1]), Path(sys.argv[2])
    acts, pets = activities(adviser)
    labels, odds = luck(luck_repo)
    data = {'activities': acts, 'pets': pets, 'labels': labels, 'odds': odds}
    OUT.write_text(json.dumps(data, separators=(',', ':')))
    print(f'{len(acts)} activities, {sum(len(a["d"]) for a in acts)} rates, {len(labels)} counters, '
          f'{len(odds)} items with odds -> {OUT} ({OUT.stat().st_size // 1024} KB)')


if __name__ == '__main__':
    main()
