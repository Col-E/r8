// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;

/** Immutable command structure for an invocation of the {@link L8} libray compiler. */
@Keep
public final class L8Command extends BaseCompilerCommand {

  private static class DefaultL8DiagnosticsHandler implements DiagnosticsHandler {

    @Override
    public void error(Diagnostic error) {
      if (error instanceof DexFileOverflowDiagnostic) {
        DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
        DiagnosticsHandler.super.error(
            new StringDiagnostic(
                overflowDiagnostic.getDiagnosticMessage()
                    + ". Library too large. L8 can only produce a single .dex file"));
        return;
      }
      DiagnosticsHandler.super.error(error);
    }
  }

  /**
   * Builder for constructing a L8Command.
   *
   * <p>A builder is obtained by calling {@link L8Command#builder}.
   */
  @Keep
  public static class Builder extends BaseCompilerCommand.Builder<L8Command, Builder> {

    private Builder() {
      this(new DefaultL8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    /** Add dex program-data. */
    @Override
    public Builder addDexProgramData(byte[] data, Origin origin) {
      guard(() -> getAppBuilder().addDexProgramData(data, origin));
      return self();
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.DEBUG;
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (getSpecialLibraryConfiguration() == null) {
        reporter.error("L8 requires a special library configuration");
      } else if (!getSpecialLibraryConfiguration().equals("default")) {
        reporter.error("L8 currently requires the special library configuration to be \"default\"");
      }
      if (getProgramConsumer() instanceof ClassFileConsumer) {
        reporter.error("L8 does not support compiling to Java class files");
      }
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("L8 does not support compiling to dex per class");
      }
      if (getAppBuilder().hasMainDexList()) {
        reporter.error("L8 does not support a main dex list");
      } else if (getMainDexListConsumer() != null) {
        reporter.error("L8 does not support generating a main dex list");
      }
      super.validate();
    }

    @Override
    L8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new L8Command(isPrintHelp(), isPrintVersion());
      }

      return new L8Command(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMainDexListConsumer(),
          getMinApiLevel(),
          getReporter(),
          getSpecialLibraryConfiguration());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  private L8Command(
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      StringConsumer mainDexListConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      String specialLibraryConfiguration) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        diagnosticsHandler,
        true,
        false,
        specialLibraryConfiguration,
        false,
        (name, checksum) -> true);
  }

  private L8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
  }

  private void configureLibraryDesugaring(InternalOptions options) {
    SpecialLibraryConfiguration.configureLibraryDesugaringForLibraryCompilation(options);
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(new DexItemFactory(), getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    assert internal.mainDexListConsumer == null;
    assert !internal.minimalMainDex;
    internal.minApiLevel = getMinApiLevel();
    assert !internal.intermediate;
    assert internal.readCompileTimeAnnotations;

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;

    // Assert some of R8 optimizations are disabled.
    assert !internal.enableDynamicTypeOptimization;
    assert !internal.enableInlining;
    assert !internal.enableClassInlining;
    assert !internal.enableHorizontalClassMerging;
    assert !internal.enableVerticalClassMerging;
    assert !internal.enableClassStaticizer;
    assert !internal.enableEnumValueOptimization;
    assert !internal.outline.enabled;
    assert !internal.enableValuePropagation;
    assert !internal.enableLambdaMerging;
    assert !internal.enableTreeShakingOfLibraryMethodOverrides;

    // TODO(b/137168535) Disable non-null tracking for now.
    internal.enableNonNullTracking = false;
    assert internal.enableDesugaring;
    assert internal.enableInheritanceClassInDexDistributor;
    internal.enableInheritanceClassInDexDistributor = false;

    // TODO(134732760): This is still work in progress.
    assert internal.rewritePrefix.isEmpty();
    assert internal.emulateLibraryInterface.isEmpty();
    assert internal.retargetCoreLibMember.isEmpty();
    assert internal.backportCoreLibraryMembers.isEmpty();
    assert internal.dontRewriteInvocations.isEmpty();
    assert getSpecialLibraryConfiguration().equals("default");
    configureLibraryDesugaring(internal);

    return internal;
  }
}
