import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * Decode (seed, tableClassName) pairs read from stdin (tab-separated).
 * Caches table instances by class to avoid reflection overhead.
 */
public class StringfogMulti {
    public static void main(String[] args) throws Exception {
        URL url = new File("/tmp/xiaodash-jar/xiaodash.jar").toURI().toURL();
        URLClassLoader cl = new URLClassLoader(new URL[]{url}, ClassLoader.getSystemClassLoader());
        Class<?> sf = Class.forName("com.dalvik.b1.OO00000OOOOOOOO0000O", true, cl);
        Method decode2 = sf.getMethod("OOOOOOO0OOOOO0O00OO0", long.class, String[].class);

        Map<String, String[]> tableCache = new HashMap<>();

        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t");
            if (parts.length < 2) continue;
            long seed;
            try { seed = Long.parseLong(parts[0]); }
            catch (NumberFormatException e) { System.out.println(line + "\tBAD-SEED"); continue; }
            String tableRef = parts[1];
            // tableRef format: "<package>.<class>.<field>"
            // The actual class name is everything BEFORE the last dot.
            int lastDot = tableRef.lastIndexOf('.');
            String tableClass = tableRef.substring(0, lastDot);
            String fieldName  = tableRef.substring(lastDot + 1);
            try {
                String[] table = tableCache.get(tableRef);
                if (table == null) {
                    Class<?> tc = Class.forName(tableClass, true, cl);
                    Field f = tc.getField(fieldName);
                    table = (String[]) f.get(null);
                    tableCache.put(tableRef, table);
                }
                Object out = decode2.invoke(null, seed, table);
                String s = String.valueOf(out);
                s = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
                System.out.println(seed + "\t" + tableRef + "\t" + s);
            } catch (Throwable t) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                System.out.println(seed + "\t" + tableRef + "\tERROR " + c.getClass().getSimpleName());
            }
        }
    }
}
