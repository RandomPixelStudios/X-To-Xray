--[[xtoxray
{
  "format": 1,
  "id": "veinminer",
  "name": "Veinminer",
  "version": "1.0.0",
  "description": "Mine Whole Ore-Veins At Once!",
  "author": "Random Pixel Studios",
  "categories": ["Mining"],
  "permissions": ["world.read", "world.mine"]
}
]]

local ores = {}
for _, ore in ipairs({ "coal", "iron", "copper", "gold", "redstone", "emerald", "lapis", "diamond" }) do
  table.insert(ores, "minecraft:" .. ore .. "_ore")
  table.insert(ores, "minecraft:deepslate_" .. ore .. "_ore")
end
table.insert(ores, "minecraft:nether_gold_ore")
table.insert(ores, "minecraft:nether_quartz_ore")
table.insert(ores, "minecraft:ancient_debris")

xtx.ui.toggle("enabled", "Enabled", true)
xtx.ui.slider("max", "Max Blocks", 1, 64, 10)
xtx.ui.cycle("silk", "Silk Touch", { "tool", "always", "never" }, "tool")
xtx.ui.slider("durability", "Durability Cost", 0, 10, 1)
xtx.ui.blocks("blocks", "Whitelist", ores)

local NEIGHBORS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } }

local function key(x, y, z)
  return x .. "," .. y .. "," .. z
end

local function find_vein(block, x, y, z, max)
  local found, queue, seen = {}, { { x = x, y = y, z = z } }, { [key(x, y, z)] = true }
  local head = 1
  while queue[head] and #found < max do
    local current = queue[head]
    head = head + 1
    for _, d in ipairs(NEIGHBORS) do
      local nx, ny, nz = current.x + d[1], current.y + d[2], current.z + d[3]
      local id = key(nx, ny, nz)
      if not seen[id] and #found < max then
        seen[id] = true
        if xtx.world.block(nx, ny, nz) == block then
          local pos = { x = nx, y = ny, z = nz }
          table.insert(found, pos)
          table.insert(queue, pos)
        end
      end
    end
  end
  return found
end

xtx.on("block_mined", function(block, x, y, z)
  if not xtx.setting("enabled") then return end
  local allowed = false
  for _, id in ipairs(xtx.setting("blocks")) do
    if id == block then allowed = true end
  end
  if not allowed then return end
  local vein = find_vein(block, x, y, z, xtx.setting("max"))
  if #vein > 0 then
    xtx.world.mine(vein, { silk = xtx.setting("silk"), durability = xtx.setting("durability") })
  end
end)
