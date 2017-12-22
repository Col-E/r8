// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Base class for commands and command builders for applications/tools which take an Android
 * application (and a main-dex list) as input.
 */
abstract class BaseCommand {

  private final boolean printHelp;
  private final boolean printVersion;

  private final AndroidApp app;

  BaseCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    // All other fields are initialized with stub/invalid values.
    this.app = null;
  }

  BaseCommand(AndroidApp app) {
    assert app != null;
    this.app = app;
    // Print options are not set.
    printHelp = false;
    printVersion = false;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  // Internal access to the input resources.
  AndroidApp getInputApp() {
    return app;
  }

  // Internal access to the internal options.
  abstract InternalOptions getInternalOptions();

  abstract public static class Builder<C extends BaseCommand, B extends Builder<C, B>> {

    protected final Reporter reporter;
    private boolean printHelp = false;
    private boolean printVersion = false;
    private final AndroidApp.Builder app;

    protected List<Path> programFiles = new ArrayList<>();

    protected Builder() {
      this(AndroidApp.builder(), new DefaultDiagnosticsHandler());
    }

    protected Builder(DiagnosticsHandler handler) {
      this(AndroidApp.builder(), handler);
    }

    protected Builder(AndroidApp.Builder builder) {
      this(builder, new DefaultDiagnosticsHandler());
    }

    protected Builder(AndroidApp.Builder builder, DiagnosticsHandler handler) {
      this.app = builder;
      this.reporter = new Reporter(handler);
    }

    abstract B self();

    public final C build() throws CompilationFailedException {
      try {
        validate();
        C c = makeCommand();
        reporter.failIfPendingErrors();
        return c;
      } catch (AbortException e) {
        throw new CompilationFailedException(e);
      }
    }

    protected abstract C makeCommand();

    // Internal accessor for the application resources.
    AndroidApp.Builder getAppBuilder() {
      return app;
    }

    /** Add program file resources. */
    public B addProgramFiles(Path... files) {
      addProgramFiles(Arrays.asList(files));
      return self();
    }

    Reporter getReporter() {
      return reporter;
    }

    /** Add program file resources. */
    public B addProgramFiles(Collection<Path> files) {
      guard(
          () -> {
            files.forEach(
                path -> {
                  try {
                    app.addProgramFile(path);
                    programFiles.add(path);
                  } catch (IOException | CompilationError e) {
                    error("Error with input file: ", path, e);
                  }
                });
          });
      return self();
    }

    public B addProgramResourceProvider(ProgramResourceProvider programProvider) {
      app.addProgramResourceProvider(programProvider);
      return self();
    }

    /** Add library file resource provider. */
    public B addLibraryResourceProvider(ClassFileResourceProvider provider) {
      guard(() -> getAppBuilder().addLibraryResourceProvider(provider));
      return self();
    }

    /** Add library file resources. */
    public B addLibraryFiles(Path... files) {
      addLibraryFiles(Arrays.asList(files));
      return self();
    }

    /** Add library file resources. */
    public B addLibraryFiles(Collection<Path> files) {
      guard(
          () -> {
            files.forEach(
                path -> {
                  try {
                    app.addLibraryFile(path);
                  } catch (IOException | CompilationError e) {
                    error("Error with library file: ", path, e);
                  }
                });
          });
      return self();
    }

    /** Add Java-bytecode program-data. */
    public B addClassProgramData(byte[] data, Origin origin) {
      guard(() -> app.addClassProgramData(data, origin));
      return self();
    }

    /** Add dex program-data. */
    public B addDexProgramData(byte[] data, Origin origin) {
      guard(() -> app.addDexProgramData(data, origin));
      return self();
    }

    /**
     * Add main-dex list files.
     *
     * Each line in each of the files specifies one class to keep in the primary dex file
     * (<code>classes.dex</code>).
     *
     * A class is specified using the following format: "com/example/MyClass.class". That is
     * "/" as separator between package components, and a trailing ".class".
     */
    public B addMainDexListFiles(Path... files) {
      guard(() -> {
        try {
          app.addMainDexListFiles(files);
        } catch (NoSuchFileException e) {
          reporter.error(new StringDiagnostic(
              "Main-dex-list file does not exist", new PathOrigin(Paths.get(e.getFile()))));
        }
      });
      return self();
    }

    /**
     * Add main-dex list files.
     *
     * @see #addMainDexListFiles(Path...)
     */
    public B addMainDexListFiles(Collection<Path> files) {
      guard(() -> {
        try {
          app.addMainDexListFiles(files);
        } catch (NoSuchFileException e) {
          reporter.error(new StringDiagnostic(
              "Main-dex-ist file does not exist", new PathOrigin(Paths.get(e.getFile()))));
        }
      });
      return self();
    }

    /**
     * Add main-dex classes.
     *
     * Add classes to keep in the primary dex file (<code>classes.dex</code>).
     *
     * NOTE: The name of the classes is specified using the Java fully qualified names format
     * (e.g. "com.example.MyClass"), and <i>not</i> the format used by the main-dex list file.
     */
    public B addMainDexClasses(String... classes) {
      guard(() -> app.addMainDexClasses(classes));
      return self();
    }

    /**
     * Add main-dex classes.
     *
     * Add classes to keep in the primary dex file (<code>classes.dex</code>).
     *
     * NOTE: The name of the classes is specified using the Java fully qualified names format
     * (e.g. "com.example.MyClass"), and <i>not</i> the format used by the main-dex list file.
     */
    public B addMainDexClasses(Collection<String> classes) {
      guard(() -> app.addMainDexClasses(classes));
      return self();
    }

    /** True if the print-help flag is enabled. */
    public boolean isPrintHelp() {
      return printHelp;
    }

    /** Set the value of the print-help flag. */
    public B setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return self();
    }

    /** True if the print-version flag is enabled. */
    public boolean isPrintVersion() {
      return printVersion;
    }

    /** Set the value of the print-version flag. */
    public B setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return self();
    }

    protected B setIgnoreDexInArchive(boolean value) {
      guard(() -> app.setIgnoreDexInArchive(value));
      return self();
    }

    protected void validate() {
    }

    protected void error(String baseMessage, Path path, Throwable throwable) {
      reporter.error(new StringDiagnostic(
          baseMessage + throwable.getMessage(), new PathOrigin(path)), throwable);
    }


    protected void guard(Runnable action) {
      try {
        action.run();
      } catch (CompilationError e) {
        reporter.error(e);
      } catch (AbortException e) {
        // Error was reported and exception will be thrown by build.
      }
    }

  }
}
