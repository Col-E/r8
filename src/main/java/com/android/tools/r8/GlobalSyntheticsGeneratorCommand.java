// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Immutable command structure for an invocation of the {@link GlobalSyntheticsGenerator} compiler.
 */
public final class GlobalSyntheticsGeneratorCommand extends BaseCommand {

  private final ProgramConsumer programConsumer;
  private final StringConsumer classNameConsumer;
  private final Reporter reporter;
  private final int minApiLevel;

  private final DexItemFactory factory = new DexItemFactory();

  private GlobalSyntheticsGeneratorCommand(
      AndroidApp androidApp,
      ProgramConsumer programConsumer,
      StringConsumer ClassNameConsumer,
      Reporter reporter,
      int minApiLevel) {
    super(androidApp);
    this.programConsumer = programConsumer;
    this.classNameConsumer = ClassNameConsumer;
    this.minApiLevel = minApiLevel;
    this.reporter = reporter;
  }

  private GlobalSyntheticsGeneratorCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    this.programConsumer = null;
    this.classNameConsumer = null;
    this.minApiLevel = AndroidApiLevel.B.getLevel();

    reporter = new Reporter();
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

  @Override
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

    return internal;
  }

  /**
   * Builder for constructing a GlobalSyntheticsGeneratorCommand.
   *
   * <p>A builder is obtained by calling {@link GlobalSyntheticsGeneratorCommand#builder}.
   */
  public static class Builder
      extends BaseCommand.Builder<GlobalSyntheticsGeneratorCommand, Builder> {

    private ProgramConsumer programConsumer = null;
    private StringConsumer globalSyntheticClassesListConsumer = null;
    private Reporter reporter;
    private int minApiLevel = AndroidApiLevel.B.getLevel();

    private Builder() {
      this(new DefaultR8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    @Override
    Builder self() {
      return this;
    }

    public Builder setReporter(Reporter reporter) {
      this.reporter = reporter;
      return self();
    }

    public Builder setMinApiLevel(int minApiLevel) {
      this.minApiLevel = minApiLevel;
      return self();
    }

    @Override
    void validate() {
      if (isPrintHelp() || isPrintVersion()) {
        return;
      }
      if (!(programConsumer instanceof DexIndexedConsumer)) {
        reporter.error("G8 does not support compiling to dex per class or class files");
      }
    }

    @Override
    public GlobalSyntheticsGeneratorCommand makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new GlobalSyntheticsGeneratorCommand(isPrintHelp(), isPrintVersion());
      }
      validate();
      return new GlobalSyntheticsGeneratorCommand(
          getAppBuilder().build(),
          programConsumer,
          globalSyntheticClassesListConsumer,
          reporter,
          minApiLevel);
    }

    public Builder setGlobalSyntheticClassesListOutput(Path path) {
      return setGlobalSyntheticClassesListConsumer(new StringConsumer.FileConsumer(path));
    }

    public Builder setGlobalSyntheticClassesListConsumer(
        StringConsumer globalSyntheticClassesListOutput) {
      this.globalSyntheticClassesListConsumer = globalSyntheticClassesListOutput;
      return self();
    }

    public Builder setProgramConsumerOutput(Path path) {
      return setProgramConsumer(
          FileUtils.isArchive(path)
              ? new DexIndexedConsumer.ArchiveConsumer(path, false)
              : new DexIndexedConsumer.DirectoryConsumer(path, false));
    }

    public Builder setProgramConsumer(ProgramConsumer programConsumer) {
      this.programConsumer = programConsumer;
      return self();
    }

    @Override
    public Builder addProgramFiles(Collection<Path> files) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addProgramResourceProvider(ProgramResourceProvider programProvider) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addClasspathFiles(Path... files) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addClasspathFiles(Collection<Path> files) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addClassProgramData(byte[] data, Origin origin) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    Builder addDexProgramData(byte[] data, Origin origin) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addMainDexListFiles(Path... files) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addMainDexListFiles(Collection<Path> files) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addMainDexClasses(String... classes) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }

    @Override
    public Builder addMainDexClasses(Collection<String> classes) {
      throw new Unreachable("Should not be used for global synthetics generation");
    }
  }
}
