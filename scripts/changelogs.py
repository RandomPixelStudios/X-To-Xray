# Writes Changelogs/index.json: every .md file in Changelogs/ (except README.md), newest first.
import json, os, re

DIR = "Changelogs"


def sort_key(name):
    return [int(p) if p.isdigit() else p for p in re.split(r"(\d+)", name)]


entries = []
for name in os.listdir(DIR):
    if not name.lower().endswith(".md") or name.lower() == "readme.md":
        continue
    title = name[:-3]
    with open(os.path.join(DIR, name), encoding="utf-8") as f:
        for line in f:
            if line.startswith("# "):
                title = line[2:].strip()
                break
    entries.append({"file": name, "title": title})

entries.sort(key=lambda e: sort_key(e["file"]), reverse=True)
with open(os.path.join(DIR, "index.json"), "w", encoding="utf-8") as f:
    json.dump(entries, f, ensure_ascii=False, indent=2)
    f.write("\n")
