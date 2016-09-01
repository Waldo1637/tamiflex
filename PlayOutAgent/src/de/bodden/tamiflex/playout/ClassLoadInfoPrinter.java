package de.bodden.tamiflex.playout;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 *
 * @author Timothy Hoffman
 */
public class ClassLoadInfoPrinter {

    private ClassLoadInfoPrinter() {
    }

    private static final Path OUTPUT_FILE = Paths.get("ClassLoadInfo.txt");

    public static void println() {
        print("\n");
    }

    public static void println(String s) {
        print(s + "\n");
    }

    public static void print(String s) {
        try {
            Files.write(OUTPUT_FILE, s.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void printLoaderAndParents(String className, String message, ClassLoader c) {
        println(String.format("[%s] %s ClassLoader: %s", className, message, format(c)));
        if (c == ClassLoader.getSystemClassLoader()) {
            return;
        }
        for (c = c.getParent(); (c != null && c != ClassLoader.getSystemClassLoader()); c = c.getParent()) {
            println(String.format("[%s]                         parent: %s", className, format(c)));
        }
    }

    private static String format(ClassLoader c) {
        if (c == null) {
            return "BOOTSTRAP";
        } else if (c == ClassLoader.getSystemClassLoader()) {
            return "SYSTEM";
        } else if (c instanceof URLClassLoader) {
            URLClassLoader uc = (URLClassLoader) c;
            if (uc.toString().contains("URLClassLoader")) {
                return String.format("[%s@%s]%s", c.getClass().getName(), System.identityHashCode(c), Arrays.toString(uc.getURLs()));
            } else {
                return String.format("[%s@%s](%s)%s", c.getClass().getName(), System.identityHashCode(c), uc, Arrays.toString(uc.getURLs()));
            }
        } else {
            return c.toString();
        }
    }
}
