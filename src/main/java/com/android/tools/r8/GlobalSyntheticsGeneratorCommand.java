// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.BaseCommand.LibraryInputOrigin;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

/**
 * Immutable command structure for an invocation of the {@link GlobalSyntheticsGenerator} compiler.
 */
@Keep
public final class GlobalSyntheticsGeneratorCommand {

  private final ProgramConsumer programConsumer;
  private final Reporter reporter;
  private final int minApiLevel;

  private final boolean printHelp;
  private final boolean printVersion;

  private final AndroidApp inputApp;

  private final DexItemFactory factory = new DexItemFactory();

  private GlobalSyntheticsGeneratorCommand(
      AndroidApp inputApp, ProgramConsumer programConsumer, Reporter reporter, int minApiLevel) {
    this.inputApp = inputApp;
    this.programConsumer = programConsumer;
    this.minApiLevel = minApiLevel;
    this.reporter = reporter;
    this.printHelp = false;
    this.printVersion = false;
  }

  private GlobalSyntheticsGeneratorCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;

    this.inputApp = null;
    this.programConsumer = null;
    this.minApiLevel = AndroidApiLevel.B.getLevel();

    reporter = new Reporter();
  }

  public AndroidApp getInputApp() {
    return inputApp;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  /**
   * Parse the GlobalSyntheticsGenerator command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return GlobalSyntheticsGenerator command builder with state according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return GlobalSyntheticsGeneratorCommandParser.parse(args, origin);
  }

  /**
   * Parse the GlobalSyntheticsGenerator command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return GlobalSyntheticsGenerator command builder with state according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return GlobalSyntheticsGeneratorCommandParser.parse(args, origin, handler);
  }

  protected static class DefaultR8DiagnosticsHandler implements DiagnosticsHandler {

    @Override
    public void error(Diagnostic error) {
      if (error instanceof DexFileOverflowDiagnostic) {
        DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
        DiagnosticsHandler.super.error(
            new StringDiagnostic(
                overflowDiagnostic.getDiagnosticMessage()
                    + ". Library too large. GlobalSyntheticsGenerator can only produce a single"
                    + " .dex file"));
        return;
      }
      DiagnosticsHandler.super.error(error);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, reporter);
    assert !internal.debug;
    assert !internal.minimalMainDex;
    internal.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(minApiLevel));
    assert !internal.intermediate;
    assert internal.retainCompileTimeAnnotations;
    internal.programConsumer = programConsumer;

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;

    internal.tool = Tool.GlobalSyntheticsGenerator;
    internal.desugarState = DesugarState.ON;
    internal.enableVarHandleDesugaring = true;

    internal.getArtProfileOptions().setEnableCompletenessCheckForTesting(false);

    return internal;
  }

  /**
   * Builder for constructing a GlobalSyntheticsGeneratorCommand.
   *
   * <p>A builder is obtained by calling {@link GlobalSyntheticsGeneratorCommand#builder}.
   */
  public static class Builder {

    private ProgramConsumer programConsumer = null;
    private final Reporter reporter;
    private int minApiLevel = AndroidApiLevel.B.getLevel();
    private boolean printHelp = false;
    private boolean printVersion = false;
    private final AndroidApp.Builder appBuilder = AndroidApp.builder();

    private Builder() {
      this(new DefaultR8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    /** Set the min api level. */
    public Builder setMinApiLevel(int minApiLevel) {
      this.minApiLevel = minApiLevel;
      return this;
    }

    /** Set the value of the print-help flag. */
    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    /** Set the value of the print-version flag. */
    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    /** Add library file resources. */
    public Builder addLibraryFiles(Path... files) {
      addLibraryFiles(Arrays.asList(files));
      return this;
    }

    /** Add library file resources. */
    public Builder addLibraryFiles(Collection<Path> files) {
      guard(
          () -> {
            for (Path path : files) {
              try {
                appBuilder.addLibraryFile(path);
              } catch (CompilationError e) {
                error(new LibraryInputOrigin(path), e);
              }
            }
          });
      return this;
    }

    /** Set an output path to consume the resulting program. */
    public Builder setProgramConsumerOutput(Path path) {
      return setProgramConsumer(
          FileUtils.isArchive(path)
              ? new DexIndexedConsumer.ArchiveConsumer(path, false)
              : new DexIndexedConsumer.DirectoryConsumer(path, false));
    }

    /** Set a consumer for obtaining the resulting program. */
    public Builder setProgramConsumer(ProgramConsumer programConsumer) {
      this.programConsumer = programConsumer;
      return this;
    }

    public GlobalSyntheticsGeneratorCommand build() {
      validate();
      if (isPrintHelpOrPrintVersion()) {
        return new GlobalSyntheticsGeneratorCommand(printHelp, printVersion);
      }
      return new GlobalSyntheticsGeneratorCommand(
          appBuilder.build(), programConsumer, reporter, minApiLevel);
    }

    private boolean isPrintHelpOrPrintVersion() {
      return printHelp || printVersion;
    }

    private void validate() {
      if (isPrintHelpOrPrintVersion()) {
        return;
      }
      if (!(programConsumer instanceof DexIndexedConsumer)) {
        reporter.error(
            "GlobalSyntheticsGenerator does not support compiling to dex per class or class files");
      }
    }

    // Helper to guard and handle exceptions.
    private void guard(Runnable action) {
      try {
        action.run();
      } catch (CompilationError e) {
        reporter.error(e.toStringDiagnostic());
      } catch (AbortException e) {
        // Error was reported and exception will be thrown by build.
      }
    }

    /** Signal an error. */
    public void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    // Helper to signify an error.
    public void error(Origin origin, Throwable throwable) {
      reporter.error(new ExceptionDiagnostic(throwable, origin));
    }
  }
}
