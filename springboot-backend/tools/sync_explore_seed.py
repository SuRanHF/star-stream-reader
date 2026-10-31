"""Compare or synchronize the development database with db/seed_explore.json.

Requires mysql CLI and DB_USERNAME / DB_PASSWORD environment variables.
Dry run: python tools/sync_explore_seed.py --dry-run --report path/to/diff.md
Apply:   python tools/sync_explore_seed.py --apply --backup path/to/backup.sql
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "src/main/resources/db/seed_explore.json"
TABLES = {
    "location": ("locations", "location_key", {
        "location_key": "location_key", "name": "name", "description": "description",
        "unlock_conditions": "unlock_conditions_json", "event_rates": "event_rates_json",
        "min_level": "min_level", "danger_level": "danger_level",
        "recommended_rank": "recommended_rank", "is_default": "is_default",
    }),
    "event": ("exploration_events", "event_key", {
        "event_key": "event_key", "event_type": "event_type", "stage_key": "stage_key",
        "location_key": "location_key", "name": "name", "description": "description",
        "weight": "weight", "stamina_cost": "stamina_cost", "repeatable": "repeatable",
        "required_conditions": "required_conditions_json", "rewards": "rewards_json",
        "risks_json": "risks_json", "progress_effects": "progress_effects_json",
        "choices": "choices_json", "log_template": "log_template",
    }),
}
JSON_COLUMNS = {
    "unlock_conditions_json", "event_rates_json", "required_conditions_json",
    "rewards_json", "risks_json", "progress_effects_json", "choices_json",
}


def mysql(sql):
    username = os.environ.get("DB_USERNAME")
    password = os.environ.get("DB_PASSWORD")
    if not username or password is None:
        raise RuntimeError("DB_USERNAME and DB_PASSWORD must be set")
    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    command = ["mysql", "--protocol=TCP", "--host=localhost", "--port=3306",
               "--database=reader_game", "--default-character-set=utf8mb4",
               "--batch", "--raw", "--skip-column-names", "--user=" + username]
    result = subprocess.run(command, input=sql.encode("utf-8"), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8").splitlines()


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def canonical(value, column):
    if column in JSON_COLUMNS and value is not None:
        if isinstance(value, str):
            value = json.loads(value)
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return value


def read_seed():
    result = {}
    for kind, (table, key, mapping) in TABLES.items():
        result[table] = {}
    for item in json.loads(SEED.read_text(encoding="utf-8")):
        kind = item["type"]
        if kind not in TABLES:
            raise RuntimeError("Unrecognized seed type: " + kind)
        table, key, mapping = TABLES[kind]
        row = {column: item.get(source) for source, column in mapping.items()}
        if kind == "event":
            row["enabled"] = 1
        if row[key] in result[table]:
            raise RuntimeError("Duplicate seed key: " + row[key])
        result[table][row[key]] = row
    return result


def read_database(seed):
    result = {}
    for kind, (table, key, mapping) in TABLES.items():
        columns = list(next(iter(seed[table].values())))
        pairs = ", ".join(sql_literal(column) + ", `" + column + "`" for column in columns)
        lines = mysql("SELECT JSON_OBJECT(" + pairs + ") FROM `" + table + "` ORDER BY `" + key + "`;\n")
        result[table] = {}
        for line in lines:
            row = json.loads(line)
            result[table][row[key]] = row
    return result


def diff(seed, database):
    changes = []
    for kind, (table, key, mapping) in TABLES.items():
        for row_key, expected in seed[table].items():
            actual = database[table].get(row_key)
            if actual is None:
                changes.append((table, row_key, "missing", None))
                continue
            fields = [column for column, value in expected.items()
                      if canonical(value, column) != canonical(actual[column], column)]
            if fields:
                changes.append((table, row_key, "update", fields))
        for row_key in database[table].keys() - seed[table].keys():
            changes.append((table, row_key, "extra", None))
    return sorted(changes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true")
    mode.add_argument("--apply", action="store_true")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--backup", type=Path)
    args = parser.parse_args()
    if args.apply and (not args.backup or not args.backup.is_file() or args.backup.stat().st_size == 0):
        parser.error("--apply requires an existing, non-empty --backup SQL file")
    seed = read_seed()
    changes = diff(seed, read_database(seed))
    lines = ["# 开发库重同步差异清单", "", f"种子：`{SEED.relative_to(ROOT)}`；库：`localhost:3306/reader_game`。",
             "只涉及 `locations` 与 `exploration_events`；不访问玩家数据表。", "",
             f"差异条数：**{len(changes)}**（按静态表主键计）。", ""]
    for table, row_key, action, fields in changes:
        detail = "，字段：" + ", ".join(fields) if fields else ""
        lines.append(f"- `{table}` / `{row_key}`：{action}{detail}")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("差异条数:", len(changes))
    for line in lines[7:]:
        print(line)
    if not args.apply:
        return
    statements = ["SET SESSION sql_mode = CONCAT(@@sql_mode, ',NO_BACKSLASH_ESCAPES');", "START TRANSACTION;"]
    for table, row_key, action, fields in changes:
        key = "location_key" if table == "locations" else "event_key"
        if action == "extra":
            statements.append(f"DELETE FROM `{table}` WHERE `{key}` = {sql_literal(row_key)};")
        elif action == "missing":
            row = seed[table][row_key]
            columns = ", ".join("`" + column + "`" for column in row)
            values = ", ".join(sql_literal(value) for value in row.values())
            statements.append(f"INSERT INTO `{table}` ({columns}) VALUES ({values});")
        else:
            row = seed[table][row_key]
            assignments = ", ".join(f"`{column}` = {sql_literal(row[column])}" for column in fields)
            statements.append(f"UPDATE `{table}` SET {assignments} WHERE `{key}` = {sql_literal(row_key)};")
    statements.append("COMMIT;")
    mysql("\n".join(statements) + "\n")
    remaining = diff(seed, read_database(seed))
    print("执行后差异条数:", len(remaining))
    if remaining:
        raise RuntimeError("Post-sync verification failed: " + repr(remaining[:5]))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
