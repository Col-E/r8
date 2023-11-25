// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Immutable command structure for an invocation of the {@link BackportedMethodList} tool.
 *
 * <p>To build a BackportedMethodList command use the {@link BackportedMethodListCommand.Builder}
 * class. For example:
 *
 * <pre>
 *   BackportedMethodListCommand command = BackportedMethodListCommand.builder()
 *     .setMinApiLevel(apiLevel)
 *     .setOutputPath(Paths.get("methods-list.txt"))
 *     .build();
 * </pre>
 */
@KeepForApi
public class BackportedMethodListCommand {

  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;
  private final int minApiLevel;
  private final boolean androidPlatformBuild;
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final AndroidApp app;
  private final StringConsumer backportedMethodListConsumer;
  private final DexItemFactory factory;

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  public boolean isAndroidPlatformBuild() {
    return androidPlatformBuild;
  }

  Reporter getReporter() {
    return reporter;
  }

  public int getMinApiLevel() {
    return minApiLevel;
  }

  public DesugaredLibrarySpecification getDesugaredLibraryConfiguration() {
    return desugaredLibrarySpecification;
  }

  public StringConsumer getBackportedMethodListConsumer() {
    return backportedMethodListConsumer;
  }

  AndroidApp getInputApp() {
    return app;
  }

  private BackportedMethodListCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = new Reporter();
    this.minApiLevel = -1;
    this.androidPlatformBuild = false;
    this.desugaredLibrarySpecification = null;
    this.app = null;
    this.backportedMethodListConsumer = null;
    this.factory = null;
  }

  private BackportedMethodListCommand(
      Reporter reporter,
      int minApiLevel,
      boolean androidPlatformBuild,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      AndroidApp app,
      StringConsumer backportedMethodListConsumer,
      DexItemFactory factory) {
    this.printHelp = false;
    this.printVersion = false;
    this.reporter = reporter;
    this.minApiLevel = minApiLevel;
    this.androidPlatformBuild = androidPlatformBuild;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.app = app;
    this.backportedMethodListConsumer = backportedMethodListConsumer;
    this.factory = factory;
  }

  InternalOptions getInternalOptions() {
    InternalOptions options = new InternalOptions(factory, getReporter());
    options.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(minApiLevel));
    options.setDesugaredLibrarySpecification(desugaredLibrarySpecification);
    return options;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public static Builder parse(String[] args) {
    final Set<String> OPTIONS_WITH_PARAMETER =
        ImmutableSet.of("--output", "--min-api", "--desugared-lib", "--lib");

    boolean hasDefinedApiLevel = false;
    Builder builder = builder();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_PARAMETER.contains(arg)) {
        if (++i < args.length) {
          nextArg = args[i];
        } else {
          builder.error(new StringDiagnostic("Missing parameter for " + args[i - 1] + "."));
          break;
        }
      }
      if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--android-platform-build")) {
        builder.setAndroidPlatformBuild(true);
      } else if (arg.equals("--min-api")) {
        if (hasDefinedApiLevel) {
          builder.error(new StringDiagnostic("Cannot set multiple --min-api options"));
        } else {
          parseMinApi(builder, nextArg);
          hasDefinedApiLevel = true;
        }
      } else if (arg.equals("--desugared-lib")) {
        builder.addDesugaredLibraryConfiguration(StringResource.fromFile(Paths.get(nextArg)));
      } else if (arg.equals("--lib")) {
        builder.addLibraryFiles(Paths.get(nextArg));
      } else if (arg.equals("--output")) {
        builder.setOutputPath(Paths.get(nextArg));
      } else {
        builder.error(new StringDiagnostic("Unknown option: " + arg));
      }
    }
    return builder;
  }

  private static void parseMinApi(Builder builder, String minApiString) {
    int minApi;
    try {
      minApi = Integer.parseInt(minApiString);
    } catch (NumberFormatException e) {
      builder.error(new StringDiagnostic("Invalid argument to --min-api: " + minApiString));
      return;
    }
    if (minApi < 1) {
      builder.error(new StringDiagnostic("Invalid argument to --min-api: " + minApiString));
      return;
    }
    builder.setMinApiLevel(minApi);
  }

  @KeepForApi
  public static class Builder {

    private final Reporter reporter;
    private int minApiLevel = AndroidApiLevel.B.getLevel();
    private List<StringResource> desugaredLibrarySpecificationResources = new ArrayList<>();
    private final AndroidApp.Builder app;
    private StringConsumer backportedMethodListConsumer;
    private boolean printHelp = false;
    private boolean printVersion = false;
    private boolean androidPlatformBuild = false;

    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.app = AndroidApp.builder();
      this.reporter = new Reporter(diagnosticsHandler);
    }

    /**
     * Set the minimum API level for the application compiled.
     *
     * <p>The tool will only report backported methods which are not present at this API level.
     *
     * <p>The default is 1 if never set.
     */
    public Builder setMinApiLevel(int minApiLevel) {
      if (minApiLevel <= 0) {
        reporter.error(new StringDiagnostic("Invalid minApiLevel: " + minApiLevel));
      } else {
        this.minApiLevel = minApiLevel;
      }
      return this;
    }

    public int getMinApiLevel() {
      return minApiLevel;
    }

    /** Desugared library configuration */
    public Builder addDesugaredLibraryConfiguration(StringResource configuration) {
      desugaredLibrarySpecificationResources.add(configuration);
      return this;
    }

    /** Desugared library configuration */
    public Builder addDesugaredLibraryConfiguration(String configuration) {
      return addDesugaredLibraryConfiguration(
          StringResource.fromString(configuration, Origin.unknown()));
    }

    /** The compilation SDK library (android.jar) */
    public Builder addLibraryResourceProvider(ClassFileResourceProvider provider) {
      app.addLibraryResourceProvider(provider);
      return this;
    }

    /** The compilation SDK library (android.jar) */
    public Builder addLibraryFiles(Path... files) {
      addLibraryFiles(Arrays.asList(files));
      return this;
    }

    /** The compilation SDK library (android.jar) */
    public Builder addLibraryFiles(Collection<Path> files) {
      for (Path path : files) {
        app.addLibraryFile(path);
      }
      return this;
    }

    DesugaredLibrarySpecification getDesugaredLibraryConfiguration(DexItemFactory factory) {
      if (desugaredLibrarySpecificationResources.isEmpty()) {
        return HumanDesugaredLibrarySpecification.empty();
      }
      if (desugaredLibrarySpecificationResources.size() > 1) {
        reporter.fatalError("Only one desugared library configuration is supported.");
      }
      StringResource desugaredLibrarySpecificationResource =
          desugaredLibrarySpecificationResources.get(0);
      return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
          desugaredLibrarySpecificationResource, factory, null, false, getMinApiLevel());
    }

    /** Output file for the backported method list */
    public Builder setOutputPath(Path outputPath) {
      backportedMethodListConsumer =
          new StringConsumer.FileConsumer(outputPath) {
            @Override
            public void accept(String string, DiagnosticsHandler handler) {
              super.accept(string, handler);
              super.accept(System.lineSeparator(), handler);
            }
          };
      return this;
    }

    /** Consumer receiving the the backported method list */
    public Builder setConsumer(StringConsumer consumer) {
      this.backportedMethodListConsumer = consumer;
      return this;
    }

    /** True if the print-help flag is enabled. */
    public boolean isPrintHelp() {
      return printHelp;
    }

    /** Set the value of the print-help flag. */
    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public Builder setAndroidPlatformBuild(boolean androidPlatformBuild) {
      this.androidPlatformBuild = androidPlatformBuild;
      return this;
    }

    /** True if the print-version flag is enabled. */
    public boolean isPrintVersion() {
      return printVersion;
    }

    /** Set the value of the print-version flag. */
    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    private void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    public BackportedMethodListCommand build() {
      AndroidApp library = app.build();
      if (!desugaredLibrarySpecificationResources.isEmpty()
          && library.getLibraryResourceProviders().isEmpty()) {
        reporter.error(
            new StringDiagnostic("With desugared library configuration a library is required"));
      }

      if (isPrintHelp() || isPrintVersion()) {
        return new BackportedMethodListCommand(isPrintHelp(), isPrintVersion());
      }

      if (backportedMethodListConsumer == null) {
        backportedMethodListConsumer =
            new StringConsumer() {
              @Override
              public void accept(String string, DiagnosticsHandler handler) {
                System.out.println(string);
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            };
      }
      DexItemFactory factory = new DexItemFactory();
      return new BackportedMethodListCommand(
          reporter,
          minApiLevel,
          androidPlatformBuild,
          getDesugaredLibraryConfiguration(factory),
          library,
          backportedMethodListConsumer,
          factory);
    }
  }
}
