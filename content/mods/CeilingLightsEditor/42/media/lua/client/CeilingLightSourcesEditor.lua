require "ISUI/ISPanel"
require "ISUI/ISButton"
require "ISUI/ISScrollingListBox"
require "ISUI/ISComboBox"
require "ISUI/ISTextEntryBox"

local previous = CeilingLightSourcesUI
if previous and previous.close then previous.close() end
if previous and previous.keyHandler then Events.OnKeyPressed.Remove(previous.keyHandler) end
if previous and previous.drawHandler then Events.OnPreUIDraw.Remove(previous.drawHandler) end
if previous and previous.startHandler then Events.OnGameStart.Remove(previous.startHandler) end
if previous and previous.leaveHandler then Events.OnMainMenuEnter.Remove(previous.leaveHandler) end

CeilingLightSourcesUI = {}
local Editor = CeilingLightSourcesUI
local Panel = ISPanel:derive("ISCeilingLightSourcesEditor")
local models = {
    { "Dome", "CeilingLights_dome" }, { "Bathroom bowl", "CeilingLights_bowl" },
    { "Pendant", "CeilingLights_pendant" }, { "Ceiling fan", "CeilingLights_fan" },
    { "Chandelier", "CeilingLights_chandelier" }, { "Bare bulb", "CeilingLights_bulb" },
    { "Fluorescent X", "CeilingLights_fluorescent_x" }, { "Fluorescent Y", "CeilingLights_fluorescent_y" },
    { "Panel X", "CeilingLights_panel_x" }, { "Panel Y", "CeilingLights_panel_y" },
}

local function api()
    return CeilingLightEditor
end

local function cursor(open)
    local addons = Viewpoint and Viewpoint.Addons
    local addon = addons and addons["maeiro.ceiling-editor-cursor"]
    local bridge = addon and addon.CeilingEditorCursor
    if not bridge or type(bridge.setOpen) ~= "function" then return false end
    local ok, accepted = pcall(bridge.setOpen, open == true)
    return ok and accepted
end

local function state()
    if not Viewpoint or not Viewpoint.Addons or not Viewpoint.Addons.viewState then return nil end
    return Viewpoint.Addons.viewState()
end

local function modelName(name)
    for _, entry in ipairs(models) do if entry[2] == name then return entry[1] end end
    return tostring(name)
end

function Editor.close()
    if Editor.window then
        Editor.window:setVisible(false)
        Editor.window:removeFromUIManager()
        Editor.window = nil
    end
    cursor(false)
end

function Panel:new(x, y, width, height)
    local o = ISPanel.new(self, x, y, width, height)
    o.backgroundColor = { r = 0.075, g = 0.085, b = 0.09, a = 1 }
    o.borderColor = { r = 0.55, g = 0.44, b = 0.25, a = 1 }
    o.moveWithMouse = true
    o.message = "Changes are saved automatically for this world."
    return o
end

function Panel:button(x, y, width, text, callback)
    local button = ISButton:new(x, y, width, 28, text, self, callback)
    button:initialise()
    self:addChild(button)
    return button
end

function Panel:createChildren()
    ISPanel.createChildren(self)
    self:button(self.width - 46, 12, 30, "X", Editor.close)
    self.list = ISScrollingListBox:new(14, 102, 232, self.height - 208)
    self.list:initialise()
    self.list:setFont(UIFont.Small, 3)
    self.list.itemheight = 47
    self.list.doDrawItem = function(list, y, item, alternate)
        local row = item.item
        if item.index == list.selected then list:drawRect(0, y, list.width - 12, list.itemheight - 1, .75, .32, .27, .16) end
        local title = modelName(row.model)
        list:drawText(title, 10, y + 5, .92, .9, .82, 1, UIFont.Small)
        local distance = row.distance and string.format("%.1f sq", row.distance) or "--"
        list:drawTextRight(distance, list.width - 20, y + 5, .82, .76, .56, 1, UIFont.Small)
        local detail = row.added and "Added" or "Automatic"
        if row.hidden then detail = detail .. " | HIDDEN" elseif row.edited then detail = detail .. " | edited" end
        list:drawText(detail, 10, y + 24, .62, .65, .65, 1, UIFont.Small)
        return y + list.itemheight
    end
    self.list:setOnMouseDownFunction(self, function(panel, row) panel:select(row) end)
    self:addChild(self.list)

    local left = 264
    self.model = ISComboBox:new(left, 102, self.width - left - 16, 28)
    self.model:initialise()
    for _, entry in ipairs(models) do self.model:addOptionWithData(entry[1], entry[2]) end
    self:addChild(self.model)
    self.step = ISComboBox:new(left + 110, 157, 110, 28)
    self.step:initialise()
    for _, value in ipairs({ .05, .25, .5, 1 }) do self.step:addOptionWithData(tostring(value), value) end
    self.step.selected = 2
    self:addChild(self.step)

    local function move(dx, dy, dz, turn)
        return function(panel)
            if not panel.selected then return end
            local step = panel.step:getOptionData(panel.step.selected) or .25
            panel:run(function() return api().adjust(panel.selected.id, dx * step, dy * step, dz * step, turn or 0) end)
        end
    end
    self:button(left, 208, 86, "West -X", move(-1, 0, 0))
    self:button(left + 94, 208, 86, "East +X", move(1, 0, 0))
    self:button(left, 242, 86, "North -Y", move(0, -1, 0))
    self:button(left + 94, 242, 86, "South +Y", move(0, 1, 0))
    self:button(left + 188, 208, 100, "Up +Z", move(0, 0, 1))
    self:button(left + 188, 242, 100, "Down -Z", move(0, 0, -1))
    self:button(left, 285, 138, "Rotate -15 deg", move(0, 0, 0, -15))
    self:button(left + 146, 285, 142, "Rotate +15 deg", move(0, 0, 0, 15))

    self.intensity = ISTextEntryBox:new("1.0", left, 351, 125, 28)
    self.intensity:initialise()
    self.intensity:instantiate()
    self:addChild(self.intensity)
    self.reach = ISTextEntryBox:new("6", left + 145, 351, 125, 28)
    self.reach:initialise()
    self.reach:instantiate()
    self:addChild(self.reach)
    self:button(left, 389, 288, "Apply model / intensity / reach", function(panel)
        if not panel.selected then return end
        local intensity, reach = tonumber(panel.intensity:getText()), tonumber(panel.reach:getText())
        if not intensity or not reach then panel.message = "Enter numbers for intensity and reach." return end
        local model = panel.model:getOptionData(panel.model.selected)
        panel:run(function() return api().configure(panel.selected.id, model, intensity, reach) end)
    end)
    self:button(left, 433, 138, "Duplicate", function(panel)
        if not panel.selected then return end
        local id = api().duplicate(panel.selected.id)
        panel.message = api().status()
        panel:refresh(id ~= "" and id or panel.selected.id)
    end)
    self.delete = self:button(left + 146, 433, 142, "Hide / delete", function(panel)
        if panel.selected then panel:run(function() return api().hideOrDelete(panel.selected.id) end) end
    end)
    self:button(left, 467, 288, "Restore automatic position", function(panel)
        if panel.selected then panel:run(function() return api().restore(panel.selected.id) end) end
    end)

    self:button(14, self.height - 96, 232, "Add model at aimed ceiling", function(panel)
        local player = getPlayer()
        local hit = panel.aimHit
        local x, y = player:getX(), player:getY()
        if hit then x, y = hit.x, hit.y end
        local id = api().add(panel.model:getOptionData(panel.model.selected), x, y)
        panel.message = api().status()
        panel:refresh(id ~= "" and id or nil)
    end)
    self:button(14, self.height - 62, 111, "Export layout", function(panel)
        panel:run(function() return api().exportLayout() end)
    end)
    self:button(133, self.height - 62, 113, "Import layout", function(panel)
        panel:run(function() return api().importLayout() end)
    end)
    self:button(left, 509, 138, "Snap to grid", function(panel)
        if panel.selected then panel:run(function() return api().snap(panel.selected.id, panel.step:getOptionData(panel.step.selected)) end) end
    end)
    local refresh = self:button(left + 146, 509, 142, "Refresh room", function(panel)
        panel:refresh(panel.selected and panel.selected.id)
        panel.message = "Current room and horizontal distances refreshed."
    end)
    refresh.tooltip = "Reload the current room and recalculate horizontal distances in squares."
end

function Panel:run(fn)
    local id = self.selected and self.selected.id
    local ok, result = pcall(fn)
    self.message = ok and api().status() or "Editor error: " .. tostring(result)
    self:refresh(id)
end

function Panel:select(row)
    if not row then return end
    self.selected = row
    self.intensity:setText(tostring(row.intensity))
    self.reach:setText(tostring(row.reach))
    for i, entry in ipairs(models) do if entry[2] == row.model then self.model.selected = i end end
    self.delete:setTitle(row.added and "Delete" or row.hidden and "Show" or "Hide")
end

function Panel:refresh(selectedId)
    self.list:clear()
    self.selected = nil
    local rows = api().lights()
    local selectedIndex
    for i, row in ipairs(rows or {}) do
        self.list:addItem(modelName(row.model), row)
        if row.id == selectedId then selectedIndex = i end
    end
    self.list.selected = selectedIndex or (#self.list.items > 0 and 1 or 0)
    local item = self.list.items[self.list.selected]
    if item then self:select(item.item) end
end

function Panel:prerender()
    ISPanel.prerender(self)
    self:drawText("CEILING LIGHTS EDITOR", 16, 15, .92, .87, .72, 1, UIFont.Medium)
    self:drawText("F7: close / aim again  |  Client-only layout", 16, 43, .65, .68, .68, 1, UIFont.Small)
    self:drawRect(14, 65, self.width - 28, 1, 1, .55, .44, .25)
    self:drawText("CURRENT ROOM", 16, 79, .8, .8, .75, 1, UIFont.Small)
    local left = 264
    self:drawText("Model / new light type", left, 79, .8, .8, .75, 1, UIFont.Small)
    self:drawText("Move step", left, 161, .8, .8, .75, 1, UIFont.Small)
    self:drawText("Intensity 0.25 - 2", left, 329, .8, .8, .75, 1, UIFont.Small)
    self:drawText("Reach 3 - 12", left + 145, 329, .8, .8, .75, 1, UIFont.Small)
    if self.selected then
        local f = self.selected
        local text = string.format("X %.2f  Y %.2f", f.x, f.y)
        self:drawText(text, left, 137, .68, .73, .73, 1, UIFont.Small)
        self:drawText(string.format("Height %.2f  |  Turn %.0f deg", f.height, f.yaw), left, 187, .68, .73, .73, 1, UIFont.Small)
    else
        self:drawText("Aim at a light and press F7, or select one.", left, 137, .68, .73, .73, 1, UIFont.Small)
    end
    local warning = self.cursorReady and self.message or "Enable Ceiling Light Editor Cursor in Viewpoint > Advanced > Addons."
    local width = self.width - 32
    local text = tostring(warning or "")
    while #text > 0 and getTextManager():MeasureStringX(UIFont.Small, text) > width do text = string.sub(text, 1, #text - 1) end
    self:drawText(text, 16, self.height - 24, .83, .76, .56, 1, UIFont.Small)
end

local function open()
    if Editor.window then Editor.close() return end
    if not api() or not getPlayer() then return end
    local v = state()
    if not v or not v.on or not api().openWorld() then return end
    local selectedId
    local ray = v.crosshair
    local hit = v.crosshairHit
    if ray then
        local distance = 32
        if hit then
            local x, y, z = hit.x - ray.x, hit.y - ray.y, (hit.z - ray.z) * math.sqrt(6)
            distance = math.sqrt(x * x + y * y + z * z)
        end
        selectedId = api().pick(ray.x, ray.y, ray.z, ray.dx, ray.dy, ray.dz, distance)
    end
    local width, height = 610, 650
    local core = getCore()
    local x = math.max(0, (core:getScreenWidth() - width) / 2)
    local y = math.max(0, (core:getScreenHeight() - height) / 2)
    local window = Panel:new(x, y, width, height)
    window:initialise()
    window:instantiate()
    window.aimHit = hit
    window.cursorReady = cursor(true)
    window:addToUIManager()
    window:refresh(selectedId)
    Editor.window = window
end

Editor.keyHandler = function(key)
    if key == Keyboard.KEY_F7 then open() end
end
Editor.drawHandler = function()
    local now = getTimestampMs()
    if not Editor.worldReady and api() and getPlayer() and now >= (Editor.nextWorldCheck or 0) then
        Editor.nextWorldCheck = now + 1000
        Editor.worldReady = api().openWorld()
    end
    if Editor.window then
        if not getPlayer() then Editor.close() return end
        if now >= (Editor.nextLightRefresh or 0) then
            Editor.nextLightRefresh = now + 200
            api().heartbeat()
        end
        if now >= (Editor.nextCursorSignal or 0) then
            Editor.nextCursorSignal = now + 500
            Editor.window.cursorReady = cursor(true)
        end
    end
end
Editor.startHandler = function() Editor.worldReady = api() and api().openWorld() or false end
Editor.leaveHandler = function() Editor.close() Editor.worldReady = false if api() then api().leaveWorld() end end
Events.OnKeyPressed.Add(Editor.keyHandler)
Events.OnPreUIDraw.Add(Editor.drawHandler)
Events.OnGameStart.Add(Editor.startHandler)
Events.OnMainMenuEnter.Add(Editor.leaveHandler)
