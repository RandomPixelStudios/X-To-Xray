--[[xtoxray
{
  "format": 1,
  "id": "hitboxes",
  "name": "Hitboxes",
  "version": "1.0.0",
  "description": "View Hitboxes!",
  "author": "Random Pixel Studios",
  "categories": ["Utility"],
  "permissions": ["entities.read", "render"]
}
]]

local COLORS = {
  Red = 0xFFFF0000, Orange = 0xFFFF8800, Yellow = 0xFFFFFF00, Green = 0xFF00FF00, Cyan = 0xFF00FFFF,
  Blue = 0xFF0088FF, ["Dark Blue"] = 0xFF0000FF, Purple = 0xFF8800FF, Pink = 0xFFFF00FF, White = 0xFFFFFFFF,
}
local COLOR_NAMES = { "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Dark Blue", "Purple", "Pink", "White" }
local KINDS = { player = "Players", hostile = "Hostile Mobs", friendly = "Friendly Mobs", other = "Other" }

xtx.ui.toggle("enabled", "Enabled", true)
xtx.ui.slider("distance", "Max Distance", 8, 256, 64)
xtx.ui.toggle("walls", "See Through Walls", true)
for _, kind in ipairs({ "player", "hostile", "friendly", "other" }) do
  xtx.ui.toggle("show_" .. kind, "Show " .. KINDS[kind], true)
  xtx.ui.cycle("color_" .. kind, KINDS[kind] .. " Color", COLOR_NAMES, "Red")
end

xtx.on("render", function()
  if not xtx.setting("enabled") then return end
  local walls = xtx.setting("walls")
  for _, e in ipairs(xtx.world.entities(xtx.setting("distance"))) do
    if xtx.setting("show_" .. e.kind) then
      xtx.draw.box(e.min_x, e.min_y, e.min_z, e.max_x, e.max_y, e.max_z, COLORS[xtx.setting("color_" .. e.kind)], walls)
    end
  end
end)
