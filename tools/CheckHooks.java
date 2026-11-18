import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import static net.bytebuddy.matcher.ElementMatchers.*;

public final class CheckHooks {
    public static void main(String[] args) throws Exception {
        Map<String,byte[]> rewritten = new HashMap<>();
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(CheckHooks.class.getClassLoader(),
                new Class<?>[]{Instrumentation.class}, (proxy, method, values) -> {
                    if (method.getName().equals("redefineClasses")) {
                        for (ClassDefinition definition : (ClassDefinition[]) values[0]) {
                            rewritten.put(definition.getDefinitionClass().getName(), definition.getDefinitionClassFile());
                        }
                    }
                    return null;
                });
        Method transform=Class.forName("me.zed_0xff.zombie_buddy.PatchTransformer")
                .getDeclaredMethod("transformPatchClass",Class.class,Instrumentation.class,int.class,boolean.class);
        transform.setAccessible(true);
        ClassLoader loader=CheckHooks.class.getClassLoader();
        ClassFileLocator locator=ClassFileLocator.ForClassLoader.of(loader);
        TypePool pool=TypePool.Default.of(locator);
        for(String name: new String[]{"CapturePatch","PartPatch","UpdatePatch","RoomPatch","RegistryPatch","SelectionPatch","ForgetPatch","RotationPatch"}) {
            Class<?> patch=Class.forName("maeiro.ceilinglights."+name);
            Patch spec=patch.getAnnotation(Patch.class);
            transform.invoke(null,patch,instrumentation,0,false);
            byte[] bytes=rewritten.get(patch.getName());
            if(bytes==null)throw new AssertionError("ZombieBuddy failed to transform "+name);
            ClassLoader adviceLoader=new ClassLoader(loader) {
                @Override public Class<?> loadClass(String type, boolean resolve) throws ClassNotFoundException {
                    if(!type.equals(patch.getName()))return super.loadClass(type,resolve);
                    Class<?> result=findLoadedClass(type);
                    if(result==null) result=defineClass(type,bytes,0,bytes.length);
                    if(resolve)resolveClass(result);
                    return result;
                }
            };
            Class<?> advice=adviceLoader.loadClass(patch.getName());
            TypeDescription target=pool.describe(spec.className()).resolve();
            var methods=target.getDeclaredMethods().filter(named(spec.methodName()));
            if(methods.isEmpty())throw new AssertionError("Missing target "+spec.className());
            net.bytebuddy.matcher.ElementMatcher.Junction<net.bytebuddy.description.method.MethodDescription> matcher = named(spec.methodName());
            if (name.equals("SelectionPatch")) matcher = matcher.and(takesArguments(java.util.List.class));
            new ByteBuddy().redefine(target,locator).visit(Advice.to(advice).on(matcher)).make();
            System.out.println("Hook verified: "+spec.className()+"."+spec.methodName()+" ("+methods.size()+" overloads)");
        }
        System.out.println("All hooks weave into the installed classes with the installed ZombieBuddy transformer.");
    }
}
