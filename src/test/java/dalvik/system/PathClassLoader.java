package dalvik.system;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

// Implement the equivalent of the PathClassLoader for CF backend. This simply forwards to the
// URLClassLoader.
public class PathClassLoader extends URLClassLoader {

  public PathClassLoader(String dexPath, ClassLoader parent) throws MalformedURLException {
    super(new URL[] {new File(dexPath).toURI().toURL()}, parent);
  }

  public PathClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }
}
