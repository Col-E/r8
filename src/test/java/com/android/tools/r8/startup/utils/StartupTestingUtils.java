// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.utils;

import static com.android.tools.r8.TestBase.transformer;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.experimental.startup.StartupConfigurationParser;
import com.android.tools.r8.experimental.startup.StartupItem;
import com.android.tools.r8.experimental.startup.StartupProfile;
import com.android.tools.r8.experimental.startup.instrumentation.StartupInstrumentationOptions;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

public class StartupTestingUtils {

  private static String startupInstrumentationTag = "startup";

  private enum AppVariant {
    ORIGINAL,
    OPTIMIZED;

    boolean isOriginal() {
      return this == ORIGINAL;
    }
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOriginalAppUsingFile(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.ORIGINAL, false);
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOriginalAppUsingLogcat(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.ORIGINAL, true);
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOptimizedAppUsingFile(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.OPTIMIZED, false);
  }

  public static ThrowableConsumer<D8TestBuilder>
      enableStartupInstrumentationForOptimizedAppUsingLogcat(TestParameters parameters) {
    return testBuilder ->
        enableStartupInstrumentation(testBuilder, parameters, AppVariant.OPTIMIZED, true);
  }

  private static void enableStartupInstrumentation(
      D8TestBuilder testBuilder, TestParameters parameters, AppVariant appVariant, boolean logcat)
      throws IOException {
    testBuilder
        .addOptionsModification(
            options -> {
              StartupInstrumentationOptions startupInstrumentationOptions =
                  options
                      .getStartupInstrumentationOptions()
                      .setEnableStartupInstrumentation()
                      .setEnableGeneralizationOfSyntheticsToSyntheticContext(
                          appVariant.isOriginal());
              if (logcat) {
                startupInstrumentationOptions.setStartupInstrumentationTag(
                    startupInstrumentationTag);
              }
            })
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addLibraryClassFileData(getTransformedAndroidUtilLog());
  }

  public static Path getAndroidUtilLog(TemporaryFolder temporaryFolder)
      throws CompilationFailedException, IOException {
    return TestBase.testForD8(temporaryFolder)
        .addProgramClassFileData(getTransformedAndroidUtilLog())
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .writeToZip();
  }

  public static void readStartupListFromFile(
      Path path, Consumer<StartupItem<ClassReference, MethodReference, ?>> startupItemConsumer)
      throws IOException {
    StartupConfigurationParser.createReferenceParser()
        .parseLines(
            Files.readAllLines(path),
            startupItemConsumer,
            startupItemConsumer,
            error -> fail("Unexpected parse error: " + error));
  }

  public static ThrowingConsumer<D8TestRunResult, RuntimeException> removeStartupListFromStdout(
      Consumer<StartupItem<ClassReference, MethodReference, ?>> startupItemConsumer) {
    return runResult -> removeStartupListFromStdout(runResult, startupItemConsumer);
  }

  public static void removeStartupListFromStdout(
      D8TestRunResult runResult,
      Consumer<StartupItem<ClassReference, MethodReference, ?>> startupItemConsumer) {
    StartupConfigurationParser<ClassReference, MethodReference, TypeReference> parser =
        StartupConfigurationParser.createReferenceParser();
    StringBuilder stdoutBuilder = new StringBuilder();
    String startupDescriptorPrefix = "[" + startupInstrumentationTag + "] ";
    for (String line : StringUtils.splitLines(runResult.getStdOut(), true)) {
      if (line.startsWith(startupDescriptorPrefix)) {
        String message = line.substring(startupDescriptorPrefix.length());
        parser.parseLine(
            message,
            startupItemConsumer,
            startupItemConsumer,
            error -> fail("Unexpected parse error: " + error));
      } else {
        stdoutBuilder.append(line).append(System.lineSeparator());
      }
    }
    runResult.getResult().setStdout(stdoutBuilder.toString());
  }

  public static void setStartupConfiguration(
      TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder,
      List<StartupItem<ClassReference, MethodReference, ?>> startupItems) {
    testBuilder.addOptionsModification(
        options -> {
          DexItemFactory dexItemFactory = options.dexItemFactory();
          StartupProfile startupProfile =
              StartupProfile.builder()
                  .apply(
                      builder ->
                          startupItems.forEach(
                              startupItem ->
                                  builder.addStartupItem(
                                      convertStartupItemToDex(startupItem, dexItemFactory))))
                  .build();
          StartupProfileProvider startupProfileProvider =
              new StartupProfileProvider() {
                @Override
                public String get() {
                  return startupProfile.serializeToString();
                }

                @Override
                public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
                  throw new Unimplemented();
                }

                @Override
                public Origin getOrigin() {
                  return Origin.unknown();
                }
              };
          options.getStartupOptions().setStartupProfileProvider(startupProfileProvider);
        });
  }

  private static StartupItem<DexType, DexMethod, ?> convertStartupItemToDex(
      StartupItem<ClassReference, MethodReference, ?> startupItem, DexItemFactory dexItemFactory) {
    return StartupItem.dexBuilder()
        .applyIf(
            startupItem.isStartupClass(),
            builder ->
                builder.setClassReference(
                    ClassReferenceUtils.toDexType(
                        startupItem.asStartupClass().getReference(), dexItemFactory)),
            builder ->
                builder.setMethodReference(
                    MethodReferenceUtils.toDexMethod(
                        startupItem.asStartupMethod().getReference(), dexItemFactory)))
        .setFlags(startupItem.getFlags())
        .build();
  }

  private static byte[] getTransformedAndroidUtilLog() throws IOException {
    return transformer(Log.class).setClassDescriptor("Landroid/util/Log;").transform();
  }

  public static class Log {

    public static int i(String tag, String msg) {
      System.out.println("[" + tag + "] " + msg);
      return 42;
    }
  }
}
