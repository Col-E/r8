// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.fail;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.StringUtils;
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
    return ToolHelper.runJava(ImmutableList.of(out.toString()), main);
  }

  protected ProcessResult runOnJavaNoVerifyRaw(JasminBuilder builder, String main)
      throws Exception {
    Path out = temp.newFolder().toPath();
    builder.writeClassFiles(out);
    return ToolHelper.runJavaNoVerify(ImmutableList.of(out.toString()), main);
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
    return runOnArt(compileWithD8(builder), main);
  }

  protected ProcessResult runOnArtD8Raw(JasminBuilder builder, String main) throws Exception {
    return runOnArtRaw(compileWithD8(builder), main);
  }

  protected AndroidApp compileWithR8(JasminBuilder builder) throws Exception {
    return compileWithR8(builder, null);
  }

  protected AndroidApp compileWithR8(JasminBuilder builder,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    return ToolHelper.runR8(builder.build(), optionsConsumer);
  }

  protected AndroidApp compileWithR8(JasminBuilder builder, String proguardConfig,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(builder.build())
            .addProguardConfiguration(ImmutableList.of(proguardConfig))
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

  protected ProcessResult runOnArtR8Raw(JasminBuilder builder, String main, String proguardConfig,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    AndroidApp result = compileWithR8(builder, proguardConfig, optionsConsumer);
    return runOnArtRaw(result, main);
  }

  private ProcessResult runDx(JasminBuilder builder, File classes, Path dex) throws Exception {
    builder.writeClassFiles(classes.toPath());
    List<String> args = new ArrayList<>();
    args.add("--output=" + dex.toString());
    args.add(classes.toString());
    System.out.println("running: dx " + StringUtils.join(args, " "));
    return ToolHelper.runDX(args.toArray(new String[args.size()]));
  }

  protected ProcessResult runDX(JasminBuilder builder) throws Exception {
    return runDx(builder, temp.newFolder("classes_for_dx"),
        temp.getRoot().toPath().resolve("classes.dex"));
  }

  protected String runOnArtDx(JasminBuilder builder, String main) throws Exception {
    Path dex = temp.getRoot().toPath().resolve("classes.dex");
    ProcessResult result = runDx(builder, temp.newFolder("classes_for_dx"), dex);
    assertNormalExitAndGetStdout(result);
    return ToolHelper.runArtNoVerificationErrors(dex.toString(), main);
  }

  protected ProcessResult runOnArtDxRaw(JasminBuilder builder, String main) throws Exception {
    Path dex = temp.getRoot().toPath().resolve("classes.dex");
    ProcessResult result = runDx(builder, temp.newFolder("classes_for_dx"), dex);
    assertNormalExitAndGetStdout(result);
    return ToolHelper.runArtRaw(dex.toString(), main);
  }

  protected ProcessResult runOnArtRaw(AndroidApp app, String main) throws IOException {
    Path out = temp.getRoot().toPath().resolve("out.zip");
    app.writeToZip(out, OutputMode.Indexed);
    return ToolHelper.runArtRaw(out.toString(), main);
  }

  protected String runOnArt(AndroidApp app, String main) throws IOException {
    Path out = temp.getRoot().toPath().resolve("out.zip");
    app.writeToZip(out, OutputMode.Indexed);
    return ToolHelper.runArtNoVerificationErrors(out.toString(), main);
  }

  protected DexEncodedMethod getMethod(AndroidApp application, String clazz,
      MethodSignature signature) {
    return getMethod(application,
        clazz, signature.type, signature.name, Arrays.asList(signature.parameters));
  }
}
