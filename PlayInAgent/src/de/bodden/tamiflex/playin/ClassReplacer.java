/* *****************************************************************************
 * Copyright (c) 2010 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eric Bodden - initial API and implementation
 *****************************************************************************/
package de.bodden.tamiflex.playin;

import de.bodden.tamiflex.normalizer.ClassRenamer;
import de.bodden.tamiflex.normalizer.Hasher;
import static de.bodden.tamiflex.normalizer.Hasher.containsGeneratedClassName;
import de.bodden.tamiflex.normalizer.NameExtractor;
import static de.bodden.tamiflex.normalizer.ReferencedGeneratedClasses.nameOfGeneratedClassReferenced;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassVisitor;

public class ClassReplacer implements ClassFileTransformer {

    /**
     * Thrown when bytes cannot be replaced for a commonly known reason.
     */
    private static class CannotReplace extends Throwable {

        enum Reason {
            ASM, TamiFlex, NotFound
        };

        public final Reason reason;

        CannotReplace(Reason r) {
            reason = r;
        }
    }

    private static final String ASM_PKGNAME = ClassVisitor.class.getPackage().getName().replace('.', '/');

    /**
     * The class loader that ought to be used to replace classes.
     */
    protected final ClassLoader loader;

    /**
     * If true, the agent will issue no warnings. *
     */
    protected final boolean verbose;

    /**
     * A mapping from class names of generated classes to the original class
     * bytes of the respective class, i.e., the bytes as they were just about to
     * be loaded on the current execution. See
     * {@link Hasher#containsGeneratedClassName(String)} to determine if a class
     * is generated in this sense.
     */
    protected Map<String, byte[]> generatedClassNameToOriginalBytes = new HashMap<>();

    public long numInvoked = 0, numSuccess = 0, numFailed = 0;
    public long numJava = 0, numSun = 0, numASM = 0, numTFlex = 0, numNotFound = 0;

    public ClassReplacer(String srcPath, boolean verbose) {
        this.verbose = verbose;
        this.loader = createClassLoader(srcPath);
    }

    @Override
    public byte[] transform(ClassLoader ldr, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        numInvoked++;
        if (className == null) {
            className = NameExtractor.extractName(classfileBuffer);
        }
        if (className.startsWith("java/")) {
            numJava++;
        } else if (className.startsWith("sun/")) {
            numSun++;
        }
        try {
            if (containsGeneratedClassName(className)) {
                storeClassBytesOfGeneratedClass(className, classfileBuffer);
            }

            byte[] newClassBytes = tryToReplaceClassBytes(className);
            if (verbose) {
                Logger.printInfo(className, true, !Arrays.equals(newClassBytes, classfileBuffer), "");
            }
            numSuccess++;
            return newClassBytes;
        } catch (CannotReplace e) {
            if (verbose) {
                Logger.printInfo(className, false, false, e.reason.toString());
            }
            numFailed++;
            switch (e.reason) {
                case ASM:
                    numASM++;
                    break;
                case TamiFlex:
                    numTFlex++;
                    break;
                case NotFound:
                    numNotFound++;
                    break;
            }
            return null;
        } catch (RuntimeException | Error e) {
            e.printStackTrace(Agent.err());
            throw e;
        }
    }

    private byte[] tryToReplaceClassBytes(final String className) throws CannotReplace {
        try {
            if (className.startsWith(Agent.PKGNAME)) {
                throw new CannotReplace(CannotReplace.Reason.TamiFlex);
            }
            if (className.startsWith(ASM_PKGNAME)) {
                throw new CannotReplace(CannotReplace.Reason.ASM);
            }

            //check if the class is generated, and if so generate a hashed class name and use that name in what follows
            String classNameInFileSystem;
            boolean isGeneratedClass;
            byte[] originalBytes;
            synchronized (this) {
                originalBytes = generatedClassNameToOriginalBytes.get(className);
                isGeneratedClass = Hasher.containsGeneratedClassName(className);
                if (isGeneratedClass) {
                    byte[] originalClassBytes = originalBytes;

                    //generate hash numbers based on the contents of all generated classes seen so far 
                    Hasher.generateHashNumber(className, originalClassBytes);
                    //we will load the class file using the hashed name
                    classNameInFileSystem = Hasher.hashedClassNameForGeneratedClassName(className);
                } else {
                    classNameInFileSystem = className;
                }
            }

            String classFileName = classNameInFileSystem + ".class";
            InputStream is = loader.getResourceAsStream(classFileName);
            if (is == null) {
                /*
                if (verbose) {
                    if (isGeneratedClass) {
                        System.err.println("WARNING: Cannot find class " + classNameInFileSystem + ". Will use original class " + className + " instead.");
                    } else {
                        System.err.println("WARNING: Cannot find class " + classNameInFileSystem + ". Will use original class instead.");
                    }
                }
                 */
                //leave bytecodes unchanged
                throw new CannotReplace(CannotReplace.Reason.NotFound);
            } else {
                try {
                    //read the class file from disk
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(is.available());
                    int bytesRead;
                    byte[] buffer = new byte[is.available()];
                    while ((bytesRead = is.read(buffer)) > -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    byte[] readBytes = bos.toByteArray();

                    //if the class is generated, replace...
                    //1) the hashed name of the class itself by className, the name that the context expects, and
                    //2) the hashed names of all other referenced generated classes by the actual names that
                    //   the context provides
                    if (isGeneratedClass) {
                        String refOrig = nameOfGeneratedClassReferenced(className, originalBytes);
                        String refHashed = nameOfGeneratedClassReferenced(classNameInFileSystem, readBytes);

                        Map<String, String> fromTo = new HashMap<>();
                        //rename declaring class
                        fromTo.put(classNameInFileSystem, className);
                        //rename referenced class, if any 
                        if (refOrig != null) {
                            assert refHashed != null : "Class " + refOrig + " references " + refOrig + " but hashed class " + classNameInFileSystem + " has no references!?";
                            fromTo.put(refHashed, refOrig);
                        }

                        synchronized (this) {
                            readBytes = ClassRenamer.replaceClassNamesInBytes(fromTo, readBytes);
                        }
                    }

                    return readBytes;
                } finally {
                    is.close();
                }
            }
        } catch (Exception e) {
            //print the exception before we re-throw it because otherwise the 
            //transformation framework may just swallow it
            RuntimeException e2 = new RuntimeException("Exception in class replacer", e);
            e2.printStackTrace(Agent.err());
            throw e2;
        }
    }

    /**
     * Returns a class loader that loads classes <i>only</i> from the provided
     * <code>srcPath</code>. This path has to be in standard classpath format,
     * separated by {@link File#pathSeparator}. Note that this class loader does
     * <i>not</i> delegate!
     *
     * @param srcPath The path to load from.
     *
     * @return the class loader
     */
    private URLClassLoader createClassLoader(String srcPath) {
        String[] pathSegments = srcPath.split(File.pathSeparator);
        URL[] urls = new URL[pathSegments.length];
        int i = 0;
        for (String segment : pathSegments) {
            try {
                urls[i++] = new File(segment).toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace(Agent.err());
            }
        }
        return new RestrictedURLClassLoader(urls);
    }

    /**
     * Stores a mapping from <code>className</code> to a copy of
     * <code>classfileBuffer</code> into
     * {@link #generatedClassNameToOriginalBytes}. In cases where this map
     * already holds a mapping for <code>className</code> and this mapping
     * points to an array with different contents, then this method issues a
     * warning. The warning is only issued if {@link #verbose} is true.
     */
    private synchronized void storeClassBytesOfGeneratedClass(final String className, byte[] classfileBuffer) {
        if (generatedClassNameToOriginalBytes.containsKey(className) && !Arrays.equals(classfileBuffer, generatedClassNameToOriginalBytes.get(className))) {
            if (verbose) {
                System.err.println("WARNING: There exist two different classes with name " + className);
            }
        } else {
            byte[] copy = new byte[classfileBuffer.length];
            System.arraycopy(classfileBuffer, 0, copy, 0, classfileBuffer.length);
            generatedClassNameToOriginalBytes.put(className, copy);
        }
    }
}
