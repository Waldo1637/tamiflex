package de.bodden.tamiflex.playin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 *
 * @author Timothy Hoffman
 */
public class Logger {

    private static final Path LOG_FILE = Paths.get("PIA.log");

    public static void printInfo(String className, boolean replaced, boolean modified, String failReason) {
        StringBuilder sb = new StringBuilder(className);
        sb.append(",").append(replaced ? "replaced" : "not replaced");
        sb.append(",").append(modified ? "modified" : "not modified");
        sb.append(",").append(failReason);
        println(sb.toString());
    }

    public static void println() {
        print("\n");
    }

    public static void println(String s) {
        print(s + "\n");
    }

    public static void print(String s) {
        try {
            Files.write(LOG_FILE, s.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Logger() {
    }
}
