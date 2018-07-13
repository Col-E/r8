// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceA;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceB;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceC;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceD;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass.IfaceNoImpl;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class InlinerTest extends TestBase {
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
    String artOutput = runOnArt(app, InterfaceTargetsTestClass.class);
    assertEquals(javaOutput, artOutput);

    DexInspector inspector = new DexInspector(app);
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
        compileWithR8(app, getProguardConfig(mainClass.getCanonicalName()), this::configure);

    // Materialize file for execution.
    Path generatedDexFile = temp.getRoot().toPath().resolve("classes.jar");
    compiled.writeToZip(generatedDexFile, OutputMode.DexIndexed);

    // Run with ART.
    String artOutput = ToolHelper.runArtNoVerificationErrors(
        generatedDexFile.toString(), mainClass.getCanonicalName());

    // Compare with Java.
    ProcessResult javaResult = ToolHelper.runJava(
        ToolHelper.getClassPathForTests(), mainClass.getCanonicalName());

    if (javaResult.exitCode != 0) {
      System.out.println(javaResult.stdout);
      System.err.println(javaResult.stderr);
      fail("JVM failed for: " + mainClass);
    }
    assertEquals("JVM and ART output differ", javaResult.stdout, artOutput);

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
