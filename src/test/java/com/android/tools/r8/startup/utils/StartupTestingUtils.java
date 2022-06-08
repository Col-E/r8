// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.utils;

import static com.android.tools.r8.TestBase.transformer;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.experimental.startup.StartupClass;
import com.android.tools.r8.experimental.startup.StartupConfiguration;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

public class StartupTestingUtils {

  private static String startupInstrumentationTag = "startup";

  public static ThrowableConsumer<D8TestBuilder> enableStartupInstrumentation(
      TestParameters parameters) {
    return testBuilder -> enableStartupInstrumentation(testBuilder, parameters);
  }

  public static void enableStartupInstrumentation(
      D8TestBuilder testBuilder, TestParameters parameters) throws IOException {
    testBuilder
        .addOptionsModification(
            options ->
                options
                    .getStartupOptions()
                    .setEnableStartupInstrumentation()
                    .setStartupInstrumentationTag(startupInstrumentationTag))
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

  public static ThrowingConsumer<D8TestRunResult, RuntimeException> removeStartupClassesFromStdout(
      Consumer<StartupClass<ClassReference>> startupClassConsumer) {
    return runResult -> removeStartupClassesFromStdout(runResult, startupClassConsumer);
  }

  public static void removeStartupClassesFromStdout(
      D8TestRunResult runResult, Consumer<StartupClass<ClassReference>> startupClassConsumer) {
    StringBuilder stdoutBuilder = new StringBuilder();
    String startupDescriptorPrefix = "[" + startupInstrumentationTag + "] ";
    for (String line : StringUtils.splitLines(runResult.getStdOut(), true)) {
      if (line.startsWith(startupDescriptorPrefix)) {
        StartupClass.Builder<ClassReference> startupClassBuilder = StartupClass.builder();
        String message = line.substring(startupDescriptorPrefix.length());
        message = StartupConfiguration.parseSyntheticFlag(message, startupClassBuilder);
        startupClassBuilder.setReference(Reference.classFromDescriptor(message));
        startupClassConsumer.accept(startupClassBuilder.build());
      } else {
        stdoutBuilder.append(line).append(System.lineSeparator());
      }
    }
    runResult.getResult().setStdout(stdoutBuilder.toString());
  }

  public static void setStartupConfiguration(
      R8TestBuilder<?> testBuilder, List<StartupClass<ClassReference>> startupClasses) {
    testBuilder.addOptionsModification(
        options -> {
          DexItemFactory dexItemFactory = options.dexItemFactory();
          options
              .getStartupOptions()
              .setStartupConfiguration(
                  StartupConfiguration.builder()
                      .apply(
                          builder ->
                              startupClasses.forEach(
                                  startupClass ->
                                      builder.addStartupClass(
                                          StartupClass.<DexType>builder()
                                              .setFlags(startupClass.getFlags())
                                              .setReference(
                                                  dexItemFactory.createType(
                                                      startupClass.getReference().getDescriptor()))
                                              .build())))
                      .build());
        });
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
