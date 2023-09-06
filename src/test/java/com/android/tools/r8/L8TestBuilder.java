// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.errors.UnusedProguardKeepRuleDiagnostic;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class L8TestBuilder {

  private final AndroidApiLevel apiLevel;
  private final Backend backend;
  private final L8Command.Builder l8Builder;
  private final TestState state;

  private CompilationMode mode = CompilationMode.RELEASE;
  private String generatedKeepRules = null;
  private List<String> keepRules = new ArrayList<>();
  private List<Path> programFiles = new ArrayList<>();
  private List<byte[]> programClassFileData = new ArrayList<>();
  private Consumer<InternalOptions> optionsModifier = ConsumerUtils.emptyConsumer();
  private StringResource desugaredLibrarySpecification = null;
  private List<Path> libraryFiles = new ArrayList<>();
  private ProgramConsumer programConsumer;
  private boolean finalPrefixVerification = true;

  private L8TestBuilder(AndroidApiLevel apiLevel, Backend backend, TestState state) {
    this.apiLevel = apiLevel;
    this.backend = backend;
    this.state = state;
    this.l8Builder = L8Command.builder(state.getDiagnosticsHandler());
  }

  public static L8TestBuilder create(AndroidApiLevel apiLevel, Backend backend, TestState state) {
    return new L8TestBuilder(apiLevel, backend, state);
  }

  public L8TestBuilder ignoreFinalPrefixVerification() {
    finalPrefixVerification = false;
    return this;
  }

  public L8TestBuilder addProgramFiles(Path... programFiles) {
    this.programFiles.addAll(Arrays.asList(programFiles));
    return this;
  }

  public L8TestBuilder addProgramFiles(Collection<Path> programFiles) {
    this.programFiles.addAll(programFiles);
    return this;
  }

  public L8TestBuilder addProgramClassFileData(byte[]... classes) {
    this.programClassFileData.addAll(Arrays.asList(classes));
    return this;
  }

  public L8TestBuilder addLibraryFiles(Collection<Path> libraryFiles) {
    this.libraryFiles.addAll(libraryFiles);
    return this;
  }

  public L8TestBuilder addLibraryFiles(Path... libraryFiles) {
    Collections.addAll(this.libraryFiles, libraryFiles);
    return this;
  }

  public L8TestBuilder addGeneratedKeepRules(String generatedKeepRules) {
    assertNull(this.generatedKeepRules);
    this.generatedKeepRules = generatedKeepRules;
    return this;
  }

  public L8TestBuilder addKeepRules(String keepRule) {
    this.keepRules.add(keepRule);
    return this;
  }

  public L8TestBuilder addKeepRuleFile(Path keepRuleFile) throws IOException {
    this.keepRules.add(FileUtils.readTextFile(keepRuleFile, StandardCharsets.UTF_8));
    return this;
  }

  public L8TestBuilder addKeepRuleFiles(Collection<Path> keepRuleFiles) throws IOException {
    for (Path keepRuleFile : keepRuleFiles) {
      addKeepRuleFile(keepRuleFile);
    }
    return this;
  }

  public L8TestBuilder addOptionsModifier(Consumer<InternalOptions> optionsModifier) {
    this.optionsModifier = this.optionsModifier.andThen(optionsModifier);
    return this;
  }

  public L8TestBuilder apply(ThrowableConsumer<L8TestBuilder> thenConsumer) {
    thenConsumer.acceptWithRuntimeException(this);
    return this;
  }

  public L8TestBuilder applyIf(boolean condition, ThrowableConsumer<L8TestBuilder> thenConsumer) {
    return applyIf(condition, thenConsumer, ThrowableConsumer.empty());
  }

  public L8TestBuilder applyIf(
      boolean condition,
      ThrowableConsumer<L8TestBuilder> thenConsumer,
      ThrowableConsumer<L8TestBuilder> elseConsumer) {
    if (condition) {
      thenConsumer.acceptWithRuntimeException(this);
    } else {
      elseConsumer.acceptWithRuntimeException(this);
    }
    return this;
  }

  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  public L8TestBuilder setDebug() {
    this.mode = CompilationMode.DEBUG;
    return this;
  }

  public L8TestBuilder setProgramConsumer(ProgramConsumer programConsumer) {
    this.programConsumer = programConsumer;
    return this;
  }

  public L8TestBuilder setDesugaredLibrarySpecification(Path path) {
    this.desugaredLibrarySpecification = StringResource.fromFile(path);
    return this;
  }

  private ProgramConsumer computeProgramConsumer(AndroidAppConsumers sink) {
    if (programConsumer != null) {
      return programConsumer;
    }
    return backend.isCf()
        ? sink.wrapProgramConsumer(ClassFileConsumer.emptyConsumer())
        : sink.wrapProgramConsumer(DexIndexedConsumer.emptyConsumer());
  }

  public L8TestCompileResult compile() throws IOException, CompilationFailedException {
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    AndroidAppConsumers sink = new AndroidAppConsumers();
    l8Builder
        .addProgramFiles(programFiles)
        .addLibraryFiles(getLibraryFiles())
        .setMode(mode)
        .setIncludeClassesChecksum(true)
        .addDesugaredLibraryConfiguration(desugaredLibrarySpecification)
        .setMinApiLevel(apiLevel.getLevel())
        .setProgramConsumer(computeProgramConsumer(sink));
    addProgramClassFileData(l8Builder);
    Path mapping = null;
    ImmutableList<String> allKeepRules = null;
    if (!keepRules.isEmpty() || generatedKeepRules != null) {
      mapping = state.getNewTempFile("mapping.txt");
      allKeepRules =
          ImmutableList.<String>builder()
              .addAll(keepRules)
              .addAll(
                  generatedKeepRules != null && !generatedKeepRules.isEmpty()
                      ? ImmutableList.of(generatedKeepRules)
                      : Collections.emptyList())
              .build();
      l8Builder
          .addProguardConfiguration(allKeepRules, Origin.unknown())
          .setProguardMapOutputPath(mapping);
    }
    ToolHelper.runL8(l8Builder.build(), optionsModifier);
    // With special program consumer we may not be able to build the resulting app.
    if (programConsumer != null) {
      return null;
    }
    assertNoUnexpectedDiagnosticMessages();
    return new L8TestCompileResult(
            sink.build(),
            apiLevel,
            allKeepRules,
            generatedKeepRules,
            mapping,
            state,
            backend.isCf() ? OutputMode.ClassFile : OutputMode.DexIndexed)
        .applyIf(finalPrefixVerification, this::validatePrefix);
  }

  private void validatePrefix(L8TestCompileResult compileResult) throws IOException {
    InternalOptions options = new InternalOptions();
    DesugaredLibrarySpecification specification =
        DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
            this.desugaredLibrarySpecification,
            options.dexItemFactory(),
            options.reporter,
            true,
            apiLevel.getLevel());
    Set<String> maintainTypeOrPrefix = specification.getMaintainTypeOrPrefixForTesting();
    compileResult.inspect(
        inspector ->
            inspector.forAllClasses(
                clazz -> {
                  String finalName = clazz.getFinalName();
                  if (finalName.startsWith("java.")) {
                    assertTrue(maintainTypeOrPrefix.stream().anyMatch(finalName::startsWith));
                  } else {
                    assertTrue(finalName.startsWith("j$."));
                  }
                }));
  }

  private void assertNoUnexpectedDiagnosticMessages() {
    TestDiagnosticMessages diagnosticsMessages = state.getDiagnosticsMessages();
    diagnosticsMessages.assertNoErrors();
    List<Diagnostic> warnings = diagnosticsMessages.getWarnings();
    // We allow warnings exclusively when using the extended version for JDK11 testing.
    // In this case, all warnings should apply to org.testng.Assert types which are not present
    // in the vanilla desugared library.
    // Vanilla desugared library compilation should have no warnings.
    assertTrue(
        warnings.stream().map(Diagnostic::getDiagnosticMessage).collect(Collectors.joining()),
        warnings.isEmpty()
            || warnings.stream()
                .allMatch(warn -> warn.getDiagnosticMessage().contains("org.testng.Assert")));
    List<Diagnostic> infos = diagnosticsMessages.getInfos();
    for (Diagnostic info : infos) {
      // The rewriting confuses the generic signatures in some methods. Such signatures are never
      // used by tools (they use the non library desugared version) and are stripped when compiling
      // with R8 anyway.
      if (info.getDiagnosticMessage()
          .equals("Running R8 version " + Version.LABEL + " with assertions enabled.")) {
        continue;
      }
      if (info.getDiagnosticMessage().startsWith("Dumped compilation inputs to:")) {
        continue;
      }
      if (info instanceof UnusedProguardKeepRuleDiagnostic) {
        // The default keep rules on desugared library may be unused. They should all be defined
        // with keepclassmembers or keep,allowshrinking.
        if (info.getDiagnosticMessage().contains("keepclassmembers")
            || info.getDiagnosticMessage().contains("keep,allowshrinking")) {
          continue;
        }
        // We allow info regarding the extended version of desugared library for JDK11 testing.
        if (info.getDiagnosticMessage().contains("org.testng.")) {
          continue;
        }
        fail("Unexpected unused proguard keep rule diagnostic: " + info.getDiagnosticMessage());
      }
      // TODO(b/243483320): Investigate the Invalid signature.
      if (info.getDiagnosticMessage().contains("Invalid signature ")) {
        continue;
      }
      fail("Unexpected info diagnostic: " + info.getDiagnosticMessage());
    }
  }

  private L8Command.Builder addProgramClassFileData(L8Command.Builder builder) {
    programClassFileData.forEach(data -> builder.addClassProgramData(data, Origin.unknown()));
    return builder;
  }

  private Collection<Path> getLibraryFiles() {
    return libraryFiles;
  }

  public L8TestBuilder addArtProfileForRewriting(
      ArtProfileProvider artProfileProvider, ArtProfileConsumer residualArtProfileConsumer) {
    l8Builder.addArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer);
    return this;
  }
}
