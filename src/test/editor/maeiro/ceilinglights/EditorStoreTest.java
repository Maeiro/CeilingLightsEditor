package maeiro.ceilinglights;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.io.OutputStream;
import maeiro.ceilinglights.LightRuntime.Fixture;
import maeiro.ceilinglights.EditorStore.Edit;

public final class EditorStoreTest {
    private static int assertions;
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);
        Path a=root.resolve("world-a"), b=root.resolve("world-b"), export=root.resolve("shared.properties");
        EditorStore.enter(a);
        Fixture base=new Fixture(100.5f,200.5f,1,2.1f,"CeilingLights_dome");
        Fixture placed=new Fixture(103.25f,202.5f,1,1.9f,"CeilingLights_fluorescent_x",base.id(),90);
        Edit moved=new Edit(base,placed,false,false,1.5f,8);
        EditorStore.put(moved);
        check(EditorStore.effective(base).equals(placed),"edited model and source share a placement");
        check(EditorStore.at(EditorStore.tile(placed)).size()==1,"model is indexed in destination tile, across chunks");
        check(EditorStore.at(EditorStore.tile(base)).isEmpty(),"moved model is not reinjected at old location");
        EditorStore.leave(); EditorStore.enter(a);
        check(EditorStore.get(base.id()).equals(moved),"position, rotation, model and light settings survive restart");
        EditorStore.put(new Edit(base,placed,true,false,1.5f,8));
        check(EditorStore.effective(base)==null,"hidden automatic light has no source");
        check(EditorStore.at(EditorStore.tile(placed)).isEmpty(),"hidden automatic light has no model");
        EditorStore.remove(base.id());
        check(EditorStore.effective(base).equals(base),"restore removes override without deleting original");
        Edit extra=EditorStore.added(placed,1,6);
        EditorStore.put(extra);
        EditorStore.exportTo(export);
        EditorStore.enter(b);
        check(EditorStore.edits().isEmpty(),"worlds have independent layouts");
        EditorStore.importFrom(export);
        check(EditorStore.edits().size()==1,"layout imports into another world");
        EditorStore.importFrom(export);
        check(EditorStore.edits().size()==1,"repeated import is idempotent");
        EditorStore.put(moved);
        EditorStore.importFrom(export);
        check(EditorStore.edits().size()==2,"import merges and preserves unrelated edits");
        Edit invalid=new Edit(base,new Fixture(Float.NaN,200,1,2,base.model(),base.id(),0),false,false,1,6);
        reject(() -> EditorStore.put(invalid));
        check(EditorStore.get(base.id()).equals(moved),"invalid change preserves current equipment layout");
        reject(() -> EditorStore.put(new Edit(base,new Fixture(100,200,2,2,base.model(),base.id(),0),false,false,1,6)));
        reject(() -> EditorStore.put(new Edit(base,new Fixture(100,200,1,2,"unknown",base.id(),0),false,false,1,6)));
        reject(() -> EditorStore.put(new Edit(base,placed,false,false,Double.valueOf(Double.POSITIVE_INFINITY).floatValue(),6)));
        Path corrupt=root.resolve("invalid-import.properties");
        Properties data=new Properties(); data.setProperty("format","999"); data.setProperty("count","1");
        try(OutputStream out=Files.newOutputStream(corrupt)){data.store(out,"");}
        reject(() -> EditorStore.importFrom(corrupt));
        check(EditorStore.edits().size()==2,"malformed import cannot partially replace live layout");
        data.setProperty("format","1"); data.setProperty("count","513");
        try(OutputStream out=Files.newOutputStream(corrupt)){data.store(out,"");}
        reject(() -> EditorStore.importFrom(corrupt));
        check(Files.exists(EditorStore.file().resolveSibling("viewpoint-ceiling-lights.properties.bak")),"previous saved layout is backed up");
        EditorStore.enter(root.resolve("write-failure"));
        Files.createDirectories(EditorStore.file()); Files.writeString(EditorStore.file().resolve("keep.txt"),"keep");
        reject(() -> EditorStore.put(extra));
        check(EditorStore.edits().isEmpty(),"failed disk write cannot mutate live state");
        check(Files.readString(EditorStore.file().resolve("keep.txt")).equals("keep"),"failed save preserves existing destination");
        EditorStore.enter(a);
        check(EditorStore.edits().size()==1,"switch back reloads original world's saved manual light");
        EditorStore.remove(extra.base().id());
        check(EditorStore.edits().isEmpty(),"deleting manual light removes persisted record");
        System.out.println("Editor storage tests passed: "+assertions+" assertions.");
    }
    private static void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private static void reject(Action action)throws Exception{assertions++;try{action.run();}catch(Exception e){return;}throw new AssertionError("invalid edit was accepted");}
    private interface Action{void run()throws Exception;}
}
