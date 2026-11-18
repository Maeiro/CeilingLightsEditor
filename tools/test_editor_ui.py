from pathlib import Path
from lupa import LuaRuntime

root = Path(__file__).resolve().parents[1]
source = (root / "content/mods/CeilingLightsEditor/42/media/lua/client/CeilingLightSourcesEditor.lua").read_text(encoding="utf-8")
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(r"""
require = function() end
UIFont = {Small=1,Medium=2}
Keyboard = {KEY_F7=65}
local function event()
    local e={handlers={}}
    function e.Add(fn) table.insert(e.handlers,fn) end
    function e.Remove(fn) for i=#e.handlers,1,-1 do if e.handlers[i]==fn then table.remove(e.handlers,i) end end end
    return e
end
Events={OnKeyPressed=event(),OnPreUIDraw=event(),OnGameStart=event(),OnMainMenuEnter=event()}
function emit(e,...) for _,fn in ipairs(e.handlers) do fn(...) end end
local Base={}
Base.__index=Base
function Base:derive() local c={} c.__index=c setmetatable(c,{__index=self}) return c end
function Base:initialise() self.children={} end
function Base:instantiate() if self.made then return end self.made=true if self.createChildren then self:createChildren() end end
function Base:createChildren() end
function Base:addChild(child) child:instantiate() table.insert(self.children,child) end
function Base:addToUIManager() self.visible=true end
function Base:removeFromUIManager() self.visible=false end
function Base:setVisible(v) self.visible=v end
function Base:prerender() end
function Base:drawRect() end
function Base:drawText() end
function Base:drawTextRight(text) lastDistanceText=text end
function Base:setFont() end
function Base:setText(s) self.text=s end
function Base:getText() return self.text end
function Base:setTitle(s) self.title=s end
function Base:setOnMouseDownFunction(target,fn) self.target=target self.clicked=fn end
function Base:addOptionWithData(text,data) table.insert(self.options,{text=text,data=data}) end
function Base:getOptionData(i) return self.options[i].data end
function Base:clear() self.items={} end
function Base:addItem(text,item) table.insert(self.items,{text=text,item=item,index=#self.items+1}) end
ISPanel=Base:derive()
function ISPanel:new(x,y,w,h) return setmetatable({x=x,y=y,width=w,height=h,children={}},self) end
ISButton=Base:derive()
function ISButton:new(x,y,w,h,title,target,fn) return setmetatable({x=x,y=y,width=w,height=h,title=title,target=target,fn=fn},self) end
ISScrollingListBox=ISPanel:derive()
function ISScrollingListBox:new(x,y,w,h) local o=ISPanel.new(self,x,y,w,h) o.items={} return o end
ISComboBox=ISPanel:derive()
function ISComboBox:new(x,y,w,h) local o=ISPanel.new(self,x,y,w,h) o.options={} o.selected=1 return o end
ISTextEntryBox=ISPanel:derive()
function ISTextEntryBox:new(text,x,y,w,h) local o=ISPanel.new(self,x,y,w,h) o.text=text return o end
local player={}
function player:getX()return 100 end
function player:getY()return 200 end
function getPlayer()return player end
function getCore()return {getScreenWidth=function()return 1920 end,getScreenHeight=function()return 1080 end}end
function getTextManager()return {MeasureStringX=function(_,font,text)return #text*6 end}end
local now=0
function getTimestampMs()now=now+50 return now end
calls={cursorOpen=0,cursorClose=0,adjust=0,configured=0,leave=0}
Viewpoint={Addons={}}
Viewpoint.Addons.viewState=function()return {on=true,crosshair={x=100,y=200,z=0,dx=1,dy=0,dz=.5},crosshairHit={x=104,y=200,z=1}}end
Viewpoint.Addons["maeiro.ceiling-editor-cursor"]={CeilingEditorCursor={setOpen=function(open)if open then calls.cursorOpen=calls.cursorOpen+1 else calls.cursorClose=calls.cursorClose+1 end return true end}}
local row={id="auto-test",model="CeilingLights_dome",x=104,y=200,z=0,height=2.1,yaw=0,intensity=1,reach=6,distance=3.5,added=false,hidden=false}
function changeRoom() row={id="auto-other",model="CeilingLights_dome",x=110,y=200,z=0,height=2.1,yaw=0,intensity=1,reach=6,distance=1.25,added=false,hidden=false} end
CeilingLightEditor={
    openWorld=function()return true end,
    leaveWorld=function()calls.leave=calls.leave+1 end,
    lights=function()return {row}end,
    pick=function()return row.id end,
    status=function()return "Saved"end,
    heartbeat=function()return true end,
    adjust=function(id,dx,dy,dz,turn)calls.adjust=calls.adjust+1 assert(id==row.id)assert(dx==-.25 and dy==0 and dz==0) return true end,
    configure=function()calls.configured=calls.configured+1 return true end,
}
function press(title)
    for _,button in ipairs(CeilingLightSourcesUI.window.children) do
        if button.title==title then button.fn(button.target) return end
    end
    error("missing button "..title)
end
""")
lua.execute(source)
lua.execute(r"""
emit(Events.OnGameStart)
emit(Events.OnKeyPressed,Keyboard.KEY_F7)
assert(CeilingLightSourcesUI.window and CeilingLightSourcesUI.window.selected.id=="auto-test")
assert(calls.cursorOpen>0)
local w=CeilingLightSourcesUI.window
for _,child in ipairs(w.children)do assert(child.x>=0 and child.y>=0 and child.x+child.width<=w.width and child.y+child.height<=w.height,"Control outside panel")end
w:prerender()
w.list:doDrawItem(0,w.list.items[1],false)
assert(lastDistanceText=="3.5 sq")
press("West -X")
assert(calls.adjust==1 and w.selected.id=="auto-test")
w.intensity:setText("invalid")
press("Apply model / intensity / reach")
assert(calls.configured==0 and string.find(w.message,"Enter numbers"))
changeRoom()
press("Refresh room")
assert(w.selected.id=="auto-other" and #w.list.items==1,"Refresh must replace old room and selection")
w.list:doDrawItem(0,w.list.items[1],false)
assert(lastDistanceText=="1.2 sq","Refresh must display newly calculated distance")
assert(string.find(w.message,"distances refreshed"))
press("X")
assert(CeilingLightSourcesUI.window==nil and calls.cursorClose>0)
emit(Events.OnKeyPressed,Keyboard.KEY_F7)
assert(CeilingLightSourcesUI.window)
""")
lua.execute(source)
lua.execute(r"""
assert(CeilingLightSourcesUI.window==nil,"Lua reload must close old UI")
assert(#Events.OnKeyPressed.handlers==1 and #Events.OnPreUIDraw.handlers==1,"Lua reload duplicated handlers")
emit(Events.OnKeyPressed,Keyboard.KEY_F7)
emit(Events.OnMainMenuEnter)
assert(CeilingLightSourcesUI.window==nil and calls.leave==1,"World exit must release cursor and world layout")
""")
print("Editor UI lifecycle passed: initialization, bounded controls, ray selection, move command, invalid input, close, reload, and world exit.")
