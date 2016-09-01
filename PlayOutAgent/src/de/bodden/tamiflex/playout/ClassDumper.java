/* *****************************************************************************
 * Copyright (c) 2010 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package de.bodden.tamiflex.playout;

import static de.bodden.tamiflex.normalizer.Hasher.*;
import de.bodden.tamiflex.normalizer.NameExtractor;
import static de.bodden.tamiflex.playout.rt.ShutdownStatus.hasShutDown;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ClassDumper implements ClassFileTransformer {

    private static final boolean PRINT_CLASS_LOADERS = false;
    private static final boolean PRINT_LOADED_CLASSES = true;

    protected final File outDir;

    /**
     * It is important that this be a <i>linked</i> hash map because we need to
     * generate hash numbers for the classes in the order in which they are
     * loaded. This is because a generated class <i>a</i> may reference other
     * generated classes, and when determining a hash code for <i>a</i>, the
     * hash code for those referenced classes must already have been computed.
     */
    protected final LinkedHashMap<String, byte[]> classNameToBytes = new LinkedHashMap<>();

    private final boolean verbose;

    private final boolean dontReallyDump;

    public int newClasses;

    public ClassDumper(File outDir, boolean dontReallyDump, boolean verbose) {
        this.outDir = outDir;
        this.dontReallyDump = dontReallyDump;
        this.verbose = verbose;
        this.newClasses = 0;
        if (PRINT_CLASS_LOADERS) {
            ClassLoadInfoPrinter.println();
            ClassLoadInfoPrinter.printLoaderAndParents("ClassDumper", "ClassDumper", ClassDumper.class.getClassLoader());
            ClassLoadInfoPrinter.printLoaderAndParents("ClassDumper", "Context", Thread.currentThread().getContextClassLoader());
            ClassLoadInfoPrinter.println();
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            className = NameExtractor.extractName(classfileBuffer);
        }
        if (PRINT_CLASS_LOADERS) {
            ClassLoadInfoPrinter.printLoaderAndParents("ClassDumper", className.replace("/", "."), loader);
        }

        if (hasShutDown) {
            return null;
        }
        if (className.startsWith(Agent.PKGNAME)) {
            return null;
        }

        byte[] oldBytes;
        synchronized (this) {
            oldBytes = classNameToBytes.put(className, classfileBuffer);
            if (PRINT_LOADED_CLASSES) {
                ClassLoadInfoPrinter.print("[ClassDumper] " + className + " (v" + classfileBuffer[7] + ") ");
                try {
                    ClassLoadInfoPrinter.println("loaded from " + protectionDomain.getCodeSource().getLocation());
                } catch (NullPointerException ex) {
                    ClassLoadInfoPrinter.println("loaded from (null)");
                }
            }
        }

        if (verbose && oldBytes != null && !Arrays.equals(classfileBuffer, oldBytes)) {
            System.err.println("WARNING: There exist two different classes with name " + className);
        }

        return null;
    }

    public void writeClassesToDisk() {
        synchronized (this) {
            Set<Entry<String, byte[]>> entrySet = classNameToBytes.entrySet();
            for (Map.Entry<String, byte[]> entry : entrySet) {
                String className = entry.getKey();
                byte[] classfileBuffer = entry.getValue();

                if (containsGeneratedClassName(className)) {
                    generateHashNumber(className, classfileBuffer);
                    className = hashedClassNameForGeneratedClassName(className);
                    classfileBuffer = replaceGeneratedClassNamesByHashedNames(classfileBuffer);
                }

                if (dontReallyDump) {
                    continue; //don't dump
                }
                File localOutDir = outDir;

                localOutDir.mkdirs();

                String simpleName = className;

                if (className.contains("/")) {
                    String packageName = className.substring(0, className.lastIndexOf('/'));
                    simpleName = className.substring(className.lastIndexOf('/') + 1);

                    localOutDir = new File(localOutDir, packageName);
                    localOutDir.mkdirs();
                }

                String fileName = simpleName + ".class";

                File outFile = new File(localOutDir, fileName);
                if (outFile.exists()) {
                    outFile.delete();
                } else {
                    newClasses++;
                }
                FileOutputStream fos = null;
                try {
                    outFile.createNewFile();
                    fos = new FileOutputStream(outFile);
                    fos.write(classfileBuffer);
                } catch (IOException e) {
                    e.printStackTrace(Agent.err());
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace(Agent.err());
                        }
                    }
                }
            }
        }
    }
}
