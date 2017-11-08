// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Resource;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import jasmin.ClassFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class JasminTestBase {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  public static String getPathFromDescriptor(String classDescriptor) {
    assert classDescriptor.startsWith("L");
    assert classDescriptor.endsWith(";");
    return classDescriptor.substring(1, classDescriptor.length() - 1) + ".class";
  }

  protected ProcessResult runOnJavaRaw(JasminBuilder builder, String main) throws Exception {
    return runOnJavaRaw(builder.build(), main);
  }

  protected ProcessResult runOnJavaRaw(AndroidApp app, String main) throws Exception {
    File out = temp.newFolder();
    for (Resource clazz : app.getClassProgramResources()) {
      assert clazz.getClassDescriptors().size() == 1;
      String desc = clazz.getClassDescriptors().iterator().next();
      Path path = out.toPath().resolve(getPathFromDescriptor(desc));
      Files.createDirectories(path.getParent());
      try (InputStream input = clazz.getStream();
          OutputStream output = Files.newOutputStream(path)) {
        ByteStreams.copy(input, output);
      }
    }
    return ToolHelper.runJava(ImmutableList.of(out.getPath()), main);
  }

  protected String runOnJava(JasminBuilder builder, String main) throws Exception {
    return runOnJava(builder.build(), main);
  }

  protected String runOnJava(AndroidApp app, String main) throws Exception {
    ProcessResult result = runOnJavaRaw(app, main);
    if (result.exitCode != 0) {
      System.out.println("Std out:");
      System.out.println(result.stdout);
      System.out.println("Std err:");
      System.out.println(result.stderr);
      assertEquals(0, result.exitCode);
    }
    return result.stdout;
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

  protected AndroidApp compileWithR8(JasminBuilder builder,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    return ToolHelper.runR8(builder.build(), optionsConsumer);
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

  private ProcessResult runDx(JasminBuilder builder, File classes, Path dex) throws Exception {
    for (ClassBuilder clazz : builder.getClasses()) {
      ClassFile file = new ClassFile();
      file.readJasmin(new StringReader(clazz.toString()), clazz.name, false);
      try (OutputStream outputStream =
          Files.newOutputStream(classes.toPath().resolve(clazz.name + ".class"))) {
        file.write(outputStream);
      }
    }
    List<String> args = new ArrayList<>();
    args.add("--output=" + dex.toString());
    args.add(classes.toString());
    System.out.println("running: dx " + StringUtils.join(args, " "));
    return ToolHelper.runDX(args.toArray(new String[args.size()]));
  }

  protected ProcessResult runOnArtDxRaw(JasminBuilder builder) throws Exception {
    return runDx(builder, temp.newFolder("classes_for_dx"),
        temp.getRoot().toPath().resolve("classes.dex"));
  }

  protected String runOnArtDx(JasminBuilder builder, String main) throws Exception {
    Path dex = temp.getRoot().toPath().resolve("classes.dex");
    ProcessResult result = runDx(builder, temp.newFolder("classes_for_dx"), dex);
    if (result.exitCode != 0) {
      System.out.println("Std out:");
      System.out.println(result.stdout);
      System.out.println("Std err:");
      System.out.println(result.stderr);
      assertEquals(0, result.exitCode);
    }
    return ToolHelper.runArtNoVerificationErrors(dex.toString(), main);
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

  protected AndroidApp buildApplication(JasminBuilder builder) {
    try {
      return AndroidApp.fromClassProgramData(builder.buildClasses());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected DexEncodedMethod getMethod(AndroidApp application, String clazz,
      MethodSignature signature) {
    return getMethod(application,
        clazz, signature.type, signature.name, signature.parameters);
  }

  protected DexEncodedMethod getMethod(AndroidApp application, String className,
      String returnType, String methodName, String[] parameters) {
    try {
      DexInspector inspector = new DexInspector(application);
      ClassSubject clazz = inspector.clazz(className);
      assertTrue(clazz.isPresent());
      MethodSubject method = clazz.method(returnType, methodName, Arrays.asList(parameters));
      assertTrue(method.isPresent());
      return method.getMethod();
    } catch (Exception e) {
      return null;
    }
  }
}
