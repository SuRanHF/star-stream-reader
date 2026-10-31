"""Seed based, reproducible exploration baseline; prints a Markdown table.

Run: python tools/simulate_route_trace.py [runs]
This is a draw/cost simulation, not an account, combat, rest or XP simulator.
"""

import json
import math
import random
import statistics
import sys
from pathlib import Path

SEED = 20261101
DATA = json.loads((Path(__file__).parents[1] / "src/main/resources/db/seed_explore.json")
                  .read_text(encoding="utf-8"))
EVENTS = [item for item in DATA if item["type"] == "event"]
LOCATIONS = {item["location_key"]: item for item in DATA if item["type"] == "location"}
BY_KEY = {event["event_key"]: event for event in EVENTS}
ROUTES = {
    "A0": (["station_first_trace", "dongmyo_market_story", "dongmyo_whisperer_story",
            "chungmuro_iron_king_story", "chungmuro_shadow_king_story",
            "chungmuro_conspiracy_story"], [0, 0, 1, 0, 1, 0], [1, 2, 2, 5, 5, 5]),
    "A1": (["station_first_trace", "dongmyo_market_story", "dongmyo_whisperer_story",
            "chungmuro_iron_king_story", "chungmuro_underground_resistance",
            "chungmuro_compromise_ledger"], [0, 1, 2, 1, 0, 1], [1, 2, 2, 5, 5, 5]),
    "U0": (["station_first_trace", "geumho_shelter_story", "geumho_underground_story",
            "chungmuro_unnamed_crossing", "chungmuro_revelation_story",
            "chungmuro_script_blank"], [1, 0, 1, 0, 1, 0], [1, 3, 3, 5, 5, 5]),
    "U1": (["station_first_trace", "geumho_shelter_story", "geumho_underground_story",
            "chungmuro_unnamed_crossing", "chungmuro_nameless_drift",
            "chungmuro_hidden_reader_wall"], [1, 1, 0, 1, 1, 1], [1, 3, 3, 5, 5, 5]),
}


def matches(gate, decisions):
    return decisions.get(gate.get("event_key")) in gate.get("choice_indices", [])


def eligible(event, location_key, level, decisions, triggered, triggered_side, pending_tip):
    if event.get("location_key") != location_key or event.get("enabled", 1) == 0:
        return False
    story = event.get("event_type") == "story"
    seen = triggered if story else triggered_side
    if event["event_key"] in seen and event.get("repeatable", 1) == 0:
        return False
    location = LOCATIONS[location_key]
    access = location.get("unlock_conditions") or {}
    if level < max(location.get("min_level", 1), access.get("required_level", 0)):
        return False
    if not set(access.get("prerequisite_events", [])).issubset(triggered):
        return False
    any_prerequisites = access.get("prerequisite_events_any", [])
    if any_prerequisites and not any(key in triggered for key in any_prerequisites):
        return False
    conditions = event.get("required_conditions") or {}
    if level < conditions.get("required_level", 0):
        return False
    if not set(conditions.get("required_events", [])).issubset(triggered):
        return False
    if not set(conditions.get("required_side_events", [])).issubset(triggered_side):
        return False
    if any(not matches(gate, decisions) for gate in conditions.get("required_choices", [])):
        return False
    unlockers = conditions.get("choice_unlockers", [])
    if unlockers and not any(matches(gate, decisions) for gate in unlockers):
        return False
    if story and pending_tip:
        if pending_tip not in conditions.get("required_events", []) and not any(
                gate.get("event_key") == pending_tip for gate in unlockers):
            return False
    return True


def trace_story(event, decisions, pending_tip):
    if event.get("event_type") != "story":
        return False
    conditions = event.get("required_conditions") or {}
    if pending_tip and (pending_tip in conditions.get("required_events", []) or any(
            gate.get("event_key") == pending_tip for gate in conditions.get("choice_unlockers", []))):
        return True
    return any(matches(gate, decisions) for field in ("choice_unlockers", "required_choices")
               for gate in conditions.get(field, []))


def draw(candidates, location_key, pity, traced, rng):
    if pity >= 8 and any(event["event_type"] == "story" for event in candidates):
        candidates = [event for event in candidates if event["event_type"] == "story"]
    rates = LOCATIONS[location_key].get("event_rates") or {}
    weights = []
    for event in candidates:
        weight = event.get("weight", 10)
        if event["event_type"] in rates:
            # Java Math.round for positive values.
            weight = max(1, math.floor(weight * rates[event["event_type"]] / 100 + 0.5))
        if event["event_type"] == "story":
            weight += pity * max(1, weight // 2)
        if event["event_key"] in traced:
            weight *= 3
        weights.append(weight)
    roll = rng.random() * sum(weights)
    for event, weight in zip(candidates, weights):
        roll -= weight
        if roll < 0:
            return event
    return candidates[-1]


def run(route, trace_enabled, seed):
    keys, choices, levels = ROUTES[route]
    rng = random.Random(seed)
    decisions = {}
    triggered = set()
    triggered_side = set()
    pending_tip = None
    actions = stamina = pity = 0
    for key, choice, level in zip(keys, choices, levels):
        location_key = BY_KEY[key]["location_key"]
        for _ in range(500):
            candidates = [event for event in EVENTS if eligible(
                event, location_key, level, decisions, triggered, triggered_side, pending_tip)]
            if not candidates:
                raise RuntimeError(f"{route}: no candidates for {key}")
            traced = {event["event_key"] for event in candidates if trace_story(
                event, decisions, pending_tip)} if trace_enabled else set()
            event = draw(candidates, location_key, pity, traced, rng)
            base = event.get("stamina_cost", 5)
            stamina += base + (max(1, math.ceil(base * 0.5)) if traced else 0)
            actions += 1
            if event["event_type"] == "story":
                triggered.add(event["event_key"])
                pity = 0
                if event["event_key"] == key:
                    decisions[key] = choice
                    option = event.get("choices", [])[choice]
                    if option.get("route_pending"):
                        pending_tip = key
                    break
                raise RuntimeError(f"{route}: drew unexpected story {event['event_key']} before {key}")
            triggered_side.add(event["event_key"])
            pity += 1
        else:
            raise RuntimeError(f"{route}: >500 draws before {key}")
    assert pending_tip == keys[-1], f"{route}: did not reach pending tip"
    return actions, stamina


def percentile(values, fraction):
    values = sorted(values)
    return values[math.ceil(fraction * len(values)) - 1]


def main():
    runs = int(sys.argv[1]) if len(sys.argv) > 1 else 2000
    if runs < 1:
        raise ValueError("runs must be positive")
    print(f"seed={SEED}; runs={runs} per route/mode; weightMultiplier=3; extraCostRatio=0.5")
    print("| 路线 | 寻踪 | 场景次数 P50 | 场景次数 P95 | 行动力消耗 P50 | 行动力消耗 P95 |")
    print("|---|---|---:|---:|---:|---:|")
    for route in ROUTES:
        for enabled in (False, True):
            results = [run(route, enabled, SEED + index) for index in range(runs)]
            actions = [value[0] for value in results]
            stamina = [value[1] for value in results]
            print(f"| {route} | {'开' if enabled else '关'} | "
                  f"{percentile(actions, 0.5)} | {percentile(actions, 0.95)} | "
                  f"{percentile(stamina, 0.5)} | {percentile(stamina, 0.95)} |")


if __name__ == "__main__":
    main()
