// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.origin.Origin;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class L8TestBuilder {

  private final AndroidApiLevel apiLevel;
  private final Backend backend;
  private final TestState state;

  private CompilationMode mode = CompilationMode.RELEASE;
  private String generatedKeepRules = null;
  private List<String> keepRules = new ArrayList<>();
  private List<Path> additionalProgramFiles = new ArrayList<>();
  private List<byte[]> additionalProgramClassFileData = new ArrayList<>();
  private Consumer<InternalOptions> optionsModifier = ConsumerUtils.emptyConsumer();
  private Path desugarJDKLibs = ToolHelper.getDesugarJDKLibs();
  private Path desugarJDKLibsConfiguration = null;
  private StringResource desugaredLibraryConfiguration =
      StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting());
  private List<Path> libraryFiles = new ArrayList<>();

  private L8TestBuilder(AndroidApiLevel apiLevel, Backend backend, TestState state) {
    this.apiLevel = apiLevel;
    this.backend = backend;
    this.state = state;
  }

  public static L8TestBuilder create(AndroidApiLevel apiLevel, Backend backend, TestState state) {
    return new L8TestBuilder(apiLevel, backend, state);
  }

  public L8TestBuilder addProgramFiles(Collection<Path> programFiles) {
    this.additionalProgramFiles.addAll(programFiles);
    return this;
  }

  public L8TestBuilder addProgramClassFileData(byte[]... classes) {
    this.additionalProgramClassFileData.addAll(Arrays.asList(classes));
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

  public L8TestBuilder setDebug() {
    this.mode = CompilationMode.DEBUG;
    return this;
  }

  public L8TestBuilder setDesugarJDKLibs(Path desugarJDKLibs) {
    assert desugarJDKLibs != null : "Use noDefaultDesugarJDKLibs to clear the default.";
    this.desugarJDKLibs = desugarJDKLibs;
    return this;
  }

  public L8TestBuilder noDefaultDesugarJDKLibs() {
    this.desugarJDKLibs = null;
    return this;
  }

  public L8TestBuilder setDesugarJDKLibsConfiguration(Path desugarJDKLibsConfiguration) {
    this.desugarJDKLibsConfiguration = desugarJDKLibsConfiguration;
    return this;
  }

  public L8TestBuilder setDesugaredLibraryConfiguration(Path path) {
    this.desugaredLibraryConfiguration = StringResource.fromFile(path);
    return this;
  }

  public L8TestBuilder setDisableL8AnnotationRemoval(boolean disableL8AnnotationRemoval) {
    return addOptionsModifier(
        options -> options.testing.disableL8AnnotationRemoval = disableL8AnnotationRemoval);
  }

  public L8TestCompileResult compile()
      throws IOException, CompilationFailedException, ExecutionException {
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    AndroidAppConsumers sink = new AndroidAppConsumers();
    L8Command.Builder l8Builder =
        L8Command.builder(state.getDiagnosticsHandler())
            .addProgramFiles(getProgramFiles())
            .addLibraryFiles(getLibraryFiles())
            .setMode(mode)
            .addDesugaredLibraryConfiguration(desugaredLibraryConfiguration)
            .setMinApiLevel(apiLevel.getLevel())
            .setProgramConsumer(
                backend.isCf()
                    ? sink.wrapProgramConsumer(ClassFileConsumer.emptyConsumer())
                    : sink.wrapProgramConsumer(DexIndexedConsumer.emptyConsumer()));
    addProgramClassFileData(l8Builder);
    Path mapping = null;
    if (!keepRules.isEmpty() || generatedKeepRules != null) {
      mapping = state.getNewTempFile("mapping.txt");
      l8Builder
          .addProguardConfiguration(
              ImmutableList.<String>builder()
                  .addAll(keepRules)
                  .addAll(
                      generatedKeepRules != null
                          ? ImmutableList.of(generatedKeepRules)
                          : Collections.emptyList())
                  .build(),
              Origin.unknown())
          .setProguardMapOutputPath(mapping);
    }
    ToolHelper.runL8(l8Builder.build(), optionsModifier);
    return new L8TestCompileResult(sink.build(), apiLevel, generatedKeepRules, mapping, state)
        .inspect(
            inspector ->
                inspector.forAllClasses(
                    clazz -> assertTrue(clazz.getFinalName().startsWith("j$."))));
  }

  private Collection<Path> getProgramFiles() {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    if (desugarJDKLibs != null) {
      builder.add(desugarJDKLibs);
    }
    if (desugarJDKLibsConfiguration != null) {
      builder.add(desugarJDKLibsConfiguration);
    }
    return builder.addAll(additionalProgramFiles).build();
  }

  private L8Command.Builder addProgramClassFileData(L8Command.Builder builder) {
    additionalProgramClassFileData.forEach(
        data -> builder.addClassProgramData(data, Origin.unknown()));
    return builder;
  }

  private Collection<Path> getLibraryFiles() {
    return libraryFiles;
  }
}
