import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
/** Offline independent reflection runner; NOT JUnit Platform/Jupiter. */
public class OfflineTestRunner {
  public static void main(String[] classes) throws Exception {
    int count=0, failed=0;
    List<String> failures = new ArrayList<>();
    for (String name : classes) {
      Class<?> type=Class.forName(name);
      for (Method m:type.getDeclaredMethods()) {
        if (!m.isAnnotationPresent(Test.class)) continue;
        var ctor=type.getDeclaredConstructor(); ctor.setAccessible(true);
        Object instance=ctor.newInstance(); m.setAccessible(true); count++;
        try { m.invoke(instance); }
        catch (InvocationTargetException exc) {
          Throwable actual=exc.getCause(); failed++;
          failures.add(type.getSimpleName()+"."+m.getName()+": "+actual);
        }
      }
    }
    failures.forEach(System.out::println);
    System.out.printf("NON-JUNIT REFLECTION ASSERTION SWEEP: %d executed, %d passed, %d failed%n",
                     count, count-failed, failed);
    if (failed!=0) System.exit(1);
  }
}
