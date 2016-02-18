package de.bodden.tamiflex.playin;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * An extension of {@link URLClassLoader} that searches only in the URL's given
 * and does not search the system or bootstrap classpaths.
 *
 * @author Timothy Hoffman
 */
public class RestrictedURLClassLoader extends URLClassLoader {

    public RestrictedURLClassLoader(URL[] urls) {
        super(urls, null);
    }

    @Override
    public URL getResource(String name) {
        return findResource(name);
    }
}
