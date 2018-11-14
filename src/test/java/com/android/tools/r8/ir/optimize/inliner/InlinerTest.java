// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ir.optimize.inliner.exceptionhandling.ExceptionHandlingTestClass;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceA;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceB;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceC;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceD;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceNoImpl;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InlinerTest extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public InlinerTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testExceptionHandling() throws Exception {
    String className = ExceptionHandlingTestClass.class.getName();
    AndroidApp inputApp = readClasses(ExceptionHandlingTestClass.class);
    List<String> proguardConfig =
        ImmutableList.of(
            "-keep public class " + className + "{",
            "  public static void main(...);",
            "}",
            "-forceinline public class " + className + "{",
            "  private static void inlinee*(...);",
            "}",
            "-neverinline public class " + className + "{",
            "  private static void *Test(...);",
            "}");
    R8Command.Builder commandBuilder =
        ToolHelper.prepareR8CommandBuilder(inputApp, emptyConsumer(backend))
            .addProguardConfiguration(proguardConfig, Origin.unknown())
            .addLibraryFiles(runtimeJar(backend));
    ToolHelper.allowTestProguardOptions(commandBuilder);
    AndroidApp outputApp = ToolHelper.runR8(commandBuilder.build(), this::configure);
    assert backend == Backend.DEX || backend == Backend.CF;
    assertEquals(
        runOnJava(ExceptionHandlingTestClass.class),
        backend == Backend.DEX
            ? runOnArt(outputApp, className)
            : runOnJava(outputApp, className, Collections.emptyList()));
  }

  @Test
  public void testInterfacesWithoutTargets() throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.IfaceNoImpl.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.IfaceA.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.BaseA.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.DerivedA.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.IfaceB.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.BaseB.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.DerivedB.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.IfaceC.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.IfaceC2.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.BaseC.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.DerivedC.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.IfaceD.class),
        ToolHelper.getClassAsBytes(InterfaceTargetsTestClass.BaseD.class)
    };
    AndroidApp app = runR8(buildAndroidApp(classes), InterfaceTargetsTestClass.class);

    String javaOutput = runOnJava(InterfaceTargetsTestClass.class);
    assert backend == Backend.DEX || backend == Backend.CF;
    String output =
        backend == Backend.DEX
            ? runOnArt(app, InterfaceTargetsTestClass.class)
            : runOnJava(app, InterfaceTargetsTestClass.class);
    assertEquals(javaOutput, output);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(InterfaceTargetsTestClass.class);

    assertFalse(getMethodSubject(clazz,
        "testInterfaceNoImpl", String.class, IfaceNoImpl.class).isPresent());
    assertFalse(getMethodSubject(clazz,
        "testInterfaceA", String.class, IfaceA.class).isPresent());
    assertFalse(getMethodSubject(clazz,
        "testInterfaceB", String.class, IfaceB.class).isPresent());
    assertFalse(getMethodSubject(clazz,
        "testInterfaceD", String.class, IfaceC.class).isPresent());
    assertFalse(getMethodSubject(clazz,
        "testInterfaceD", String.class, IfaceD.class).isPresent());
  }

  private MethodSubject getMethodSubject(
      ClassSubject clazz, String methodName, Class retValue, Class... params) {
    return clazz.method(new MethodSignature(methodName, retValue.getTypeName(),
        Stream.of(params).map(Class::getTypeName).collect(Collectors.toList())));
  }

  private AndroidApp runR8(AndroidApp app, Class mainClass) throws Exception {
    AndroidApp compiled =
        compileWithR8(
            app, getProguardConfig(mainClass.getCanonicalName()), this::configure, backend);

    // Materialize file for execution.
    Path generatedFile = temp.getRoot().toPath().resolve("classes.jar");
    assert backend == Backend.DEX || backend == Backend.CF;
    compiled.writeToZip(generatedFile, outputMode(backend));

    String output =
        backend == Backend.DEX
            ? ToolHelper.runArtNoVerificationErrors(
                generatedFile.toString(), mainClass.getCanonicalName())
            : ToolHelper.runJava(generatedFile, mainClass.getCanonicalName()).stdout;

    // Compare with Java.
    ProcessResult javaResult = ToolHelper.runJava(
        ToolHelper.getClassPathForTests(), mainClass.getCanonicalName());

    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }
    assertEquals(
        backend == Backend.DEX
            ? "JVM and ART output differ."
            : "Outputs of source and processed programs running on JVM differ.",
        javaResult.stdout,
        output);

    return compiled;
  }

  private String getProguardConfig(String main) {
    return keepMainProguardConfiguration(main)
        + "\n"
        + "-dontobfuscate\n"
        + "-allowaccessmodification";
  }

  private void configure(InternalOptions options) {
    options.enableClassInlining = false;
  }
}
