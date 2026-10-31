"""Regenerate the second story growth seed and guarded migration from the approved brief.

Run from the repository root: python springboot-backend/tools/build_second_growth.py
The original Markdown text is copied into event descriptions and choices without rewriting.
"""

import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / "springboot-backend/src/main/resources/db"
BRIEF = ROOT / ".ai-collab/计划/树状主线第二次生长-剧情文本.md"
JSON_SEED = DB / "seed_explore.json"
SQL_SEED = DB / "seed_explore.sql"
MIGRATION = DB / "migrations/20261101_story_tree_second_growth.sql"
MARKER = "-- Second story tree growth: authoritative final state for the new-library SQL seed."
ROUTES = {
    "A0": ("chungmuro_conspiracy_story", ["chungmuro_council_audit", "chungmuro_shadow_vote", "chungmuro_power_fork"], "chungmuro", 0),
    "A1": ("chungmuro_compromise_ledger", ["dongmyo_ledger_delivery", "dongmyo_resistance_weigh", "geumho_resistance_muster"], None, 0),
    "U0": ("chungmuro_script_blank", ["chungmuro_paper_summon", "chungmuro_blank_revision", "chungmuro_name_fork"], "chungmuro", 1),
    "U1": ("chungmuro_hidden_reader_wall", ["geumho_wall_message", "geumho_nameless_cell", "chungmuro_reader_anchor"], None, 1),
}
REWARDS = {"碎片": "storyFragments", "经验": "exp", "星币": "coins", "热度": "channelHeat"}


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (dict, list)):
        value = compact(value)
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("\n", "\\n").replace("'", "''") + "'"


def equal_guard(column, old):
    if old is None:
        return f"`{column}` IS NULL"
    literal = sql_literal(old)
    return (f"JSON_CONTAINS(`{column}`, CAST({literal} AS JSON)) AND "
            f"JSON_CONTAINS(CAST({literal} AS JSON), `{column}`)")


def parse_brief():
    text = BRIEF.read_text(encoding="utf-8")
    headings = list(re.finditer(r"^### (A0|A1|U0|U1)-([123]) `([^`]+)` ([^\r\n]+)$", text, re.M))
    assert len(headings) == 12, len(headings)
    contexts = {}
    for line in text.splitlines():
        if not line.startswith("| `") or "story_" not in line:
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) == 3 and cells[0].startswith("`") and cells[1].startswith("`story_"):
            contexts.setdefault(cells[0].strip("`"), []).append({
                "story_flag": cells[1].strip("`"), "text": cells[2]})
    assert sum(map(len, contexts.values())) == 8
    events = []
    for index, heading in enumerate(headings):
        route, number, key, raw_name = heading.groups()
        body = text[heading.end():headings[index + 1].start() if index + 1 < len(headings) else text.index("## 路线回响")]
        location_line = re.search(r"^门槛：`([^`]+)` \[0, 1\]　地点：([^\r\n]+)", body, re.M)
        assert location_line, key
        predecessor, location_name = location_line.groups()
        location_key = {"忠武路": "chungmuro", "东庙": "dongmyo", "金湖站": "geumho_station"}[location_name]
        expected_predecessor = ROUTES[route][0] if number == "1" else ROUTES[route][1][int(number) - 2]
        assert predecessor == expected_predecessor, key
        paragraphs = re.findall(r"^> (.*)$", body, re.M)
        assert len(paragraphs) >= 3, key
        description = "\n\n".join(paragraphs)
        rows = re.findall(r"^\| ([01])(?:「([^|]+)」| ([^|]+)) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$", body, re.M)
        assert len(rows) == 2, (key, rows)
        choices = []
        for choice_index, quoted_label, plain_label, consequence, reward_text, destination, bias_text in rows:
            assert int(choice_index) == len(choices), key
            # Keep the quotation marks in labels exactly as printed in the approved table.
            label = "「" + quoted_label + "」" if quoted_label else plain_label.strip()
            reward = {}
            for item in reward_text.split("，"):
                match = re.fullmatch(r"(碎片|经验|星币|热度) \+(\d+)", item.strip())
                assert match, (key, item)
                reward[REWARDS[match.group(1)]] = int(match.group(2))
            bias = re.fullmatch(r"`([^`]+)` (\d+)", bias_text.strip())
            assert bias, (key, bias_text)
            choice = {"label": label, "consequence_text": consequence.strip(),
                      "rewards_override": reward, "title_bias": {bias.group(1): int(bias.group(2))}}
            if number == "3":
                flag = re.search(r"`(story_[^`]+)`", destination)
                assert flag and "`route_pending`" in destination, (key, destination)
                choice["set_story_flags"] = {flag.group(1): True}
                choice["route_pending"] = True
            else:
                next_key = ROUTES[route][1][int(number)]
                assert f"`{next_key}`" in destination, (key, destination)
                choice["unlock_events"] = [next_key]
            choices.append(choice)
        gate = {"choice_unlockers": [{"event_key": predecessor, "choice_indices": [0, 1]}],
                "required_choices": [{"event_key": "station_first_trace", "choice_indices": [ROUTES[route][3]]}]}
        if key in contexts:
            gate["personal_contexts"] = contexts[key]
        event = {"type": "event", "event_key": key, "required_conditions": gate,
                 "event_type": "story", "stage_key": "vol1_ch4", "location_key": location_key,
                 "name": raw_name.split("（")[0].strip(), "description": description,
                 "weight": 12, "stamina_cost": 8, "repeatable": 0, "rewards": {},
                 "progress_effects": {"storyPityReset": True}, "choices": choices}
        assert not any(word in compact(event) for word in ("死亡", "死去", "尸体", "葬")), key
        events.append(event)
    assert len(events) == 12 and len(contexts) == 4
    return events


def main():
    # The frozen first-growth revision supplies stable old-value guards after this batch is committed.
    old = json.loads(subprocess.check_output([
        "git", "show", "5b85c2f:springboot-backend/src/main/resources/db/seed_explore.json"], cwd=ROOT))
    old_by_key = {entry.get("event_key"): entry for entry in old if entry.get("type") == "event"}
    current = json.loads(JSON_SEED.read_text(encoding="utf-8"))
    by_key = {entry.get("event_key"): entry for entry in current if entry.get("type") == "event"}
    new_events = parse_brief()
    for route, (old_key, new_keys, _, _) in ROUTES.items():
        tip = by_key[old_key]
        for choice in tip["choices"]:
            assert choice.pop("route_pending", None) is True or "route_pending" not in choice
            choice["unlock_events"] = [new_keys[0]]
    for event in new_events:
        if event["event_key"] in by_key:
            current[current.index(by_key[event["event_key"]])] = event
        else:
            current.append(event)
    JSON_SEED.write_text(json.dumps(current, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    updates = []
    inserts = []
    for old_key, _, _, _ in ROUTES.values():
        old_choices = old_by_key[old_key]["choices"]
        new_choices = by_key[old_key]["choices"]
        updates.append((f"UPDATE `exploration_events`\nSET `choices_json` = {sql_literal(new_choices)}\n"
                        f"WHERE `event_key` = {sql_literal(old_key)}",
                        f"  AND {equal_guard('choices_json', old_choices)}"))
    columns = ("event_key", "event_type", "stage_key", "location_key", "name", "description",
               "weight", "stamina_cost", "repeatable", "required_conditions_json", "rewards_json",
               "progress_effects_json", "choices_json", "log_template")
    mapping = {"required_conditions_json": "required_conditions", "rewards_json": "rewards",
               "progress_effects_json": "progress_effects", "choices_json": "choices"}
    for event in new_events:
        vals = [event.get(mapping.get(column, column)) for column in columns]
        inserts.append("INSERT IGNORE INTO `exploration_events`\n(" + ", ".join(f"`{column}`" for column in columns)
                       + ")\nVALUES\n(" + ", ".join(sql_literal(value) for value in vals) + ");")
    seed_section = MARKER + "\n" + "\n\n".join(statement + ";" for statement, _ in updates)
    seed_section += "\n\n" + "\n\n".join(inserts) + "\n"
    migration = ("-- Second story tree growth. Updates only the four previous tips and inserts 12 new static events.\n"
                 "-- Old-value guards preserve custom edits; repeat execution is a no-op.\n"
                 "-- Back up the full database before applying; player and decision tables are untouched.\n\n"
                 + MARKER + "\n"
                 + "\n\n".join(statement + "\n" + guard + ";" for statement, guard in updates)
                 + "\n\n" + "\n\n".join(inserts) + "\n")
    existing_sql = SQL_SEED.read_text(encoding="utf-8")
    if MARKER in existing_sql:
        existing_sql = existing_sql[:existing_sql.index(MARKER)].rstrip() + "\n\n"
    SQL_SEED.write_text(existing_sql + seed_section, encoding="utf-8")
    MIGRATION.write_text(migration, encoding="utf-8")
    print(f"Generated {len(new_events)} events, {len(updates)} guarded tip updates, {len(inserts)} inserts")


if __name__ == "__main__":
    main()
