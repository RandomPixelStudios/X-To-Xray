--[[xtoxray
{
  "format": 1,
  "id": "mining-stats",
  "name": "Mining Stats",
  "version": "1.1.0",
  "description": "Counts the ores you mine and keeps your all-time totals",
  "author": "OreGoblin42",
  "categories": ["Mining", "Utility"],
  "permissions": ["chat", "storage", "hud"]
}
]]

local FILE = "stats.txt"
local SAVE_EVERY = 10

xtx.ui.toggle("enabled", "Enabled", true)
xtx.ui.toggle("all_blocks", "Count All Blocks", false)
xtx.ui.toggle("show_hud", "Show On HUD", true)
xtx.ui.slider("report_every", "Chat Report Every X Blocks", 1, 500, 50)

local totals = {}
local session = 0
local unsaved = 0

local saved = xtx.storage.read(FILE)
if saved then
  for id, count in saved:gmatch("([^=\n]+)=(%d+)") do
    totals[id] = tonumber(count)
  end
end

local function save()
  local lines = {}
  for id, count in pairs(totals) do
    table.insert(lines, id .. "=" .. count)
  end
  table.sort(lines)
  xtx.storage.write(FILE, table.concat(lines, "\n"))
  unsaved = 0
end

local function is_ore(block)
  return block:find("_ore", 1, true) ~= nil or block == "minecraft:ancient_debris"
end

local function short_name(block)
  return (block:gsub("^minecraft:", ""):gsub("_", " "))
end

local function report()
  local ranked = {}
  local all_time = 0
  for id, count in pairs(totals) do
    table.insert(ranked, { id = id, count = count })
    all_time = all_time + count
  end
  table.sort(ranked, function(a, b)
    if a.count ~= b.count then return a.count > b.count end
    return a.id < b.id
  end)
  xtx.chat("Mining Stats: " .. session .. " this session, " .. all_time .. " all time")
  for i = 1, math.min(3, #ranked) do
    xtx.chat(i .. ". " .. short_name(ranked[i].id) .. ": " .. ranked[i].count)
  end
end

xtx.on("block_mined", function(block)
  if not xtx.setting("enabled") then return end
  if not xtx.setting("all_blocks") and not is_ore(block) then return end
  totals[block] = (totals[block] or 0) + 1
  session = session + 1
  unsaved = unsaved + 1
  if unsaved >= SAVE_EVERY then save() end
  xtx.actionbar("+1 " .. short_name(block) .. " (" .. totals[block] .. ")")
  if session % xtx.setting("report_every") == 0 then report() end
end)

xtx.on("hud", function()
  if not xtx.setting("enabled") or not xtx.setting("show_hud") then return end
  xtx.hud.rect(2, 2, 104, 15, 0x80000000)
  xtx.hud.text("Session Ores: " .. session, 5, 5, 0xFFFFD700)
end)

xtx.command("stats", function()
  report()
end)

xtx.on("leave", save)
xtx.on("shutdown", save)
