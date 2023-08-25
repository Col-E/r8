// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import com.android.tools.r8.BaseCompilerCommandParser;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardPathList;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

@Keep
public class RelocatorCommand {

  private static final String THREAD_COUNT_FLAG = "--thread-count";

  private static final Set<String> OPTIONS_WITH_PARAMETER =
      ImmutableSet.of("--output", "--input", "--map", THREAD_COUNT_FLAG);

  static final String USAGE_MESSAGE =
      String.join(
          "\n",
          Iterables.concat(
              Arrays.asList(
                  "The Relocator CLI is EXPERIMENTAL and is subject to change",
                  "Usage: relocator [options]",
                  " where options are:",
                  "  --input <file>          # Input file to remap, class, zip or jar.",
                  "  --output <file>         # Output result in <outfile>.",
                  "  --map <from->to>        # Registers a mapping.",
                  "  --thread-count <number> # A specified number of threads to run with.",
                  "  --version               # Print the version of d8.",
                  "  --help                  # Print this message.")));

  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;
  private final DexItemFactory factory;
  private final ClassFileConsumer consumer;
  private final AndroidApp app;
  private final RelocatorMapping mapping;
  private final int threadCount;

  private RelocatorCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    reporter = null;
    factory = null;
    consumer = null;
    app = null;
    mapping = null;
    threadCount = ThreadUtils.NOT_SPECIFIED;
  }

  private RelocatorCommand(
      RelocatorMapping mapping,
      AndroidApp app,
      Reporter reporter,
      DexItemFactory factory,
      ClassFileConsumer consumer,
      int threadCount) {
    this.printHelp = false;
    this.printVersion = false;
    this.mapping = mapping;
    this.app = app;
    this.reporter = reporter;
    this.factory = factory;
    this.consumer = consumer;
    this.threadCount = threadCount;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder parse(String[] args, Origin origin) {
    return Builder.parse(args, origin);
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public Reporter getReporter() {
    return reporter;
  }

  public DexItemFactory getFactory() {
    return factory;
  }

  public ClassFileConsumer getConsumer() {
    return consumer;
  }

  public int getThreadCount() {
    return threadCount;
  }

  public AndroidApp getApp() {
    return app;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  public InternalOptions getInternalOptions() {
    // We are using the proguard configuration for adapting resources.
    InternalOptions options =
        new InternalOptions(
            // Set debug to ensure that we are writing all information to the application writer.
            CompilationMode.DEBUG,
            ProguardConfiguration.builder(factory, getReporter())
                .disableShrinking()
                .disableObfuscation()
                .disableOptimization()
                .addKeepAttributePatterns(ImmutableList.of("*"))
                .addAdaptResourceFilenames(ProguardPathList.builder().addFileName("**").build())
                .build(),
            getReporter());
    options.relocatorCompilation = true;
    options.threadCount = getThreadCount();
    options.programConsumer = consumer;
    assert consumer != null;
    options.dataResourceConsumer = consumer.getDataResourceConsumer();
    return options;
  }

  public RelocatorMapping getMapping() {
    return mapping;
  }

  @Keep
  public static class Builder {

    private final AndroidApp.Builder app;
    private final Reporter reporter;
    private final ImmutableMap.Builder<PackageReference, PackageReference> packageMapping =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<ClassReference, ClassReference> classMapping =
        ImmutableMap.builder();
    private final ImmutableMap.Builder<PackageReference, PackageReference> subPackageMapping =
        ImmutableMap.builder();
    private ClassFileConsumer consumer = null;
    private int threadCount = ThreadUtils.NOT_SPECIFIED;
    private boolean printVersion;
    private boolean printHelp;

    Builder() {
      this(AndroidApp.builder());
    }

    Builder(DiagnosticsHandler handler) {
      this(AndroidApp.builder(new Reporter(handler)));
    }

    Builder(AndroidApp.Builder builder) {
      this.app = builder;
      this.reporter = builder.getReporter();
    }

    /**
     * Setting output to a path.
     *
     * <p>Setting the output path will override any previous set consumer or any previous set output
     * path.
     *
     * @param outputPath Output path to write output to. A null argument will clear the program
     *     consumer / output.
     */
    public Builder setOutputPath(Path outputPath) {
      if (outputPath == null) {
        this.consumer = null;
        return this;
      }
      this.consumer = new ArchiveConsumer(outputPath, true);
      return this;
    }

    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    /** Signal an error. */
    public void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    /** Set the number of threads to use for the compilation */
    public Builder setThreadCount(int threadCount) {
      if (threadCount <= 0) {
        reporter.error("Invalid threadCount: " + threadCount);
      } else {
        this.threadCount = threadCount;
      }
      return this;
    }

    /** Add program file resources. */
    public Builder addProgramFiles(Path... files) {
      return addProgramFiles(Arrays.asList(files));
    }

    /** Add program file resources. */
    public Builder addProgramFiles(Collection<Path> files) {
      guard(
          () -> {
            for (Path path : files) {
              try {
                app.addProgramFile(path);
              } catch (CompilationError e) {
                error(new PathOrigin(path), e);
              }
            }
          });
      return this;
    }

    /** Add program file resource. */
    public Builder addProgramFile(Path file) {
      guard(
          () -> {
            try {
              app.addProgramFile(file);
            } catch (CompilationError e) {
              error(new PathOrigin(file), e);
            }
          });
      return this;
    }

    public Builder addPackageMapping(PackageReference source, PackageReference destination) {
      packageMapping.put(source, destination);
      return this;
    }

    public Builder addSubPackageMapping(PackageReference source, PackageReference destination) {
      subPackageMapping.put(source, destination);
      return this;
    }

    public Builder addClassMapping(ClassReference source, ClassReference destination) {
      classMapping.put(source, destination);
      return this;
    }

    /**
     * Set the program consumer.
     *
     * <p>Setting the program consumer will override any previous set consumer or any previous set
     * output path.
     *
     * @param consumer ClassFile consumer to set as current. A null argument will clear the program
     *     consumer / output.
     */
    public Builder setConsumer(ClassFileConsumer consumer) {
      // Setting an explicit program consumer resets any output-path/mode setup.
      this.consumer = consumer;
      return this;
    }

    private void validate() {
      if (consumer == null) {
        reporter.error(new StringDiagnostic("No output path or consumer has been specified"));
      }
    }

    public RelocatorCommand build() throws CompilationFailedException {
      Box<RelocatorCommand> result = new Box<>();
      ExceptionUtils.withCompilationHandler(
          reporter,
          () -> {
            if (printHelp || printVersion) {
              result.set(new RelocatorCommand(printHelp, printVersion));
              return;
            }
            reporter.failIfPendingErrors();
            validate();
            reporter.failIfPendingErrors();
            DexItemFactory factory = new DexItemFactory();
            result.set(
                new RelocatorCommand(
                    RelocatorMapping.create(
                        packageMapping.build(), classMapping.build(), subPackageMapping.build()),
                    app.build(),
                    reporter,
                    factory,
                    consumer,
                    threadCount));
          });
      return result.get();
    }

    // Helper to signify an error.
    void error(Origin origin, Throwable throwable) {
      reporter.error(new ExceptionDiagnostic(throwable, origin));
    }

    // Helper to guard and handle exceptions.
    void guard(Runnable action) {
      try {
        action.run();
      } catch (CompilationError e) {
        reporter.error(e.toStringDiagnostic());
      } catch (AbortException e) {
        // Error was reported and exception will be thrown by build.
      }
    }

    /**
     * Parse the Relocator command-line.
     *
     * <p>Parsing will set the supplied options or their default value if they have any.
     *
     * @param args Command-line arguments array.
     * @param origin Origin description of the command-line arguments.
     * @return Relocator command builder with state set up according to parsed command line.
     */
    public static Builder parse(String[] args, Origin origin) {
      return parse(args, origin, RelocatorCommand.builder());
    }

    /**
     * Parse the Relocator command-line.
     *
     * <p>Parsing will set the supplied options or their default value if they have any.
     *
     * @param args Command-line arguments array.
     * @param origin Origin description of the command-line arguments.
     * @param handler Custom defined diagnostics handler.
     * @return Relocator command builder with state set up according to parsed command line.
     */
    public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
      return parse(args, origin, RelocatorCommand.builder(handler));
    }

    private static Builder parse(String[] args, Origin origin, Builder builder) {
      String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
      Path outputPath = null;
      for (int i = 0; i < expandedArgs.length; i++) {
        String arg = expandedArgs[i].trim();
        String nextArg = null;
        if (OPTIONS_WITH_PARAMETER.contains(arg)) {
          if (++i < expandedArgs.length) {
            nextArg = expandedArgs[i];
          } else {
            builder.error(
                new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
            break;
          }
        }
        if (arg.length() == 0) {
          continue;
        }
        switch (arg) {
          case "--help":
            builder.setPrintHelp(true);
            break;
          case "--version":
            builder.setPrintVersion(true);
            break;
          case "--output":
            assert nextArg != null;
            if (outputPath != null) {
              builder.error(
                  new StringDiagnostic(
                      "Cannot output both to '" + outputPath.toString() + "' and '" + nextArg + "'",
                      origin));
              continue;
            }
            outputPath = Paths.get(nextArg);
            break;
          case "--input":
            assert nextArg != null;
            builder.addProgramFile(Paths.get(nextArg));
            break;
          case THREAD_COUNT_FLAG:
            BaseCompilerCommandParser.parsePositiveIntArgument(
                builder::error, arg, nextArg, origin, builder::setThreadCount);
            break;
          case "--map":
            assert nextArg != null;
            int separator = nextArg.indexOf("->");
            if (separator < 0) {
              builder.error(
                  new StringDiagnostic("--map " + nextArg + " is not on the form from->to"));
              continue;
            }
            String source = nextArg.substring(0, separator);
            String destination = nextArg.substring(separator + 2);
            addMapping(source, destination, builder);
            break;
          default:
            builder.error(new StringDiagnostic("Unknown argument: " + arg, origin));
        }
      }
      if (outputPath == null) {
        outputPath = Paths.get(".");
      }
      builder.setOutputPath(outputPath);
      return builder;
    }

    public static void addMapping(String source, String destination, Builder builder) {
      if (source.endsWith(".**")) {
        builder.addSubPackageMapping(
            Reference.packageFromString(source.substring(0, source.length() - 3)),
            Reference.packageFromString(destination));
      } else if (source.endsWith(".*")) {
        builder.addPackageMapping(
            Reference.packageFromString(source.substring(0, source.length() - 2)),
            Reference.packageFromString(destination));
      } else {
        builder.addClassMapping(
            Reference.classFromTypeName(source), Reference.classFromTypeName(destination));
      }
    }
  }
}
