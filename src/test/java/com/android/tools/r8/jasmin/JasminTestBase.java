// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class JasminTestBase extends TestBase {

  protected ProcessResult runOnJavaRaw(JasminBuilder builder, String main) throws Exception {
    Path out = temp.newFolder().toPath();
    builder.writeClassFiles(out);
    return ToolHelper.runJava(out, main);
  }

  protected ProcessResult runOnJavaNoVerifyRaw(JasminBuilder builder, String main)
      throws Exception {
    Path out = temp.newFolder().toPath();
    builder.writeClassFiles(out);
    return ToolHelper.runJavaNoVerify(out, main);
  }

  protected ProcessResult runOnJavaNoVerifyRaw(JasminBuilder program, JasminBuilder library,
      String main)
      throws Exception {
    Path out = temp.newFolder().toPath();
    program.writeClassFiles(out);
    Path libraryOut = temp.newFolder().toPath();
    library.writeClassFiles(libraryOut);
    return ToolHelper.runJavaNoVerify(ImmutableList.of(out, libraryOut), main);
  }

  private String assertNormalExitAndGetStdout(ProcessResult result) {
    if (result.exitCode != 0) {
      System.out.println("Std out:");
      System.out.println(result.stdout);
      System.out.println("Std err:");
      System.out.println(result.stderr);
      fail("Process terminated abnormally.");
    }
    return result.stdout;
  }

  protected String runOnJava(JasminBuilder builder, String main) throws Exception {
    return assertNormalExitAndGetStdout(runOnJavaRaw(builder, main));
  }

  protected AndroidApp compileWithD8(JasminBuilder builder) throws Exception {
    return ToolHelper.runD8(builder.build());
  }

  protected AndroidApp compileWithD8(
      JasminBuilder builder, Consumer<InternalOptions> optionsConsumer) throws Exception {
    return ToolHelper.runD8(builder.build(), optionsConsumer);
  }

  protected String runOnArtD8(JasminBuilder builder, String main) throws Exception {
    return runOnArtD8(builder, main, null);
  }

  protected String runOnArtD8(
      JasminBuilder builder, String main, Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    return runOnArt(compileWithD8(builder, optionsConsumer), main);
  }

  protected ProcessResult runOnArtD8Raw(JasminBuilder builder, String main) throws Exception {
    return runOnArtRaw(compileWithD8(builder), main);
  }

  protected ProcessResult runOnArtD8Raw(JasminBuilder program, JasminBuilder library, String main)
      throws Exception {
    return runOnArtRaw(compileWithD8(program), compileWithD8(library), main);
  }

  protected AndroidApp compileWithR8(JasminBuilder builder) throws Exception {
    return compileWithR8(builder, null);
  }

  protected AndroidApp compileWithR8(JasminBuilder builder,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    return ToolHelper.runR8(builder.build(), optionsConsumer);
  }

  protected AndroidApp compileWithR8(
      JasminBuilder builder,
      List<String> proguardConfigs,
      Consumer<InternalOptions> optionsConsumer,
      Backend backend)
      throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(builder.build(), emptyConsumer(backend))
            .addLibraryFiles(runtimeJar(backend))
            .addProguardConfiguration(proguardConfigs, Origin.unknown())
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  protected AndroidApp compileWithR8(JasminBuilder builder, String proguardConfig,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(builder.build())
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
            .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown())
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  protected AndroidApp compileWithR8(JasminBuilder program, Path library,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(program.build())
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
            .addLibraryFiles(library)
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  protected AndroidApp compileWithR8(JasminBuilder program, Path library,
      String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(program.build())
            .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown())
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
            .addLibraryFiles(library)
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  protected String runOnArtR8(JasminBuilder builder, String main) throws Exception {
    return runOnArtR8(builder, main, null);
  }

  protected String runOnArtR8(JasminBuilder builder, String main,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    AndroidApp result = compileWithR8(builder, optionsConsumer);
    return runOnArt(result, main);
  }

  protected String runOnArtR8(JasminBuilder builder, String main, String proguardConfig,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    AndroidApp result = compileWithR8(builder, proguardConfig, optionsConsumer);
    return runOnArt(result, main);
  }

  protected ProcessResult runOnArtR8Raw(JasminBuilder builder, String main,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    AndroidApp result = compileWithR8(builder, optionsConsumer);
    return runOnArtRaw(result, main);
  }

  protected ProcessResult runOnArtR8Raw(JasminBuilder builder, String main,
      String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    AndroidApp result = compileWithR8(builder, proguardConfig, optionsConsumer);
    return runOnArtRaw(result, main);
  }

  protected ProcessResult runOnArtR8Raw(JasminBuilder program, JasminBuilder library, String main,
      String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    Path libraryClasses = temp.newFolder().toPath();
    library.writeClassFiles(libraryClasses);
    AndroidApp result = proguardConfig == null
        ? compileWithR8(program, libraryClasses, optionsConsumer)
        : compileWithR8(program, libraryClasses, proguardConfig, optionsConsumer);
    AndroidApp libraryApp = compileWithR8(library);
    return runOnArtRaw(result, libraryApp, main);
  }

  @Override
  protected ProcessResult runOnArtRaw(AndroidApp app, String main) throws IOException {
    Path out = temp.getRoot().toPath().resolve("out.zip");
    app.writeToZipForTesting(out, OutputMode.DexIndexed);
    return ToolHelper.runArtRaw(out.toString(), main);
  }

  protected ProcessResult runOnArtRaw(AndroidApp program, AndroidApp library, String main)
      throws IOException {
    Path out = temp.getRoot().toPath().resolve("out.zip");
    program.writeToZipForTesting(out, OutputMode.DexIndexed);
    Path libraryOut = temp.getRoot().toPath().resolve("libraryOut.zip");
    library.writeToZipForTesting(libraryOut, OutputMode.DexIndexed);
    return ToolHelper.runArtRaw(ImmutableList.of(out.toString(), libraryOut.toString()), main,
        null);
  }

  protected String runOnArt(AndroidApp app, String main) throws IOException {
    Path out = temp.getRoot().toPath().resolve("out.zip");
    app.writeToZipForTesting(out, OutputMode.DexIndexed);
    return ToolHelper.runArtNoVerificationErrors(out.toString(), main);
  }

  protected DexEncodedMethod getMethod(
      AndroidApp application, String clazz, MethodSignature signature) {
    return getMethod(
        application, clazz, signature.type, signature.name, Arrays.asList(signature.parameters));
  }

  protected MethodSubject getMethodSubject(
      AndroidApp application, String clazz, MethodSignature signature) throws IOException {
    return new CodeInspector(application).clazz(clazz).method(signature);
  }
}
