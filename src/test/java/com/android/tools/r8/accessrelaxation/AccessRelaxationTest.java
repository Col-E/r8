// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static com.android.tools.r8.utils.DexInspectorMatchers.isPublic;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.accessrelaxation.privateinstance.Base;
import com.android.tools.r8.accessrelaxation.privateinstance.Sub1;
import com.android.tools.r8.accessrelaxation.privateinstance.Sub2;
import com.android.tools.r8.accessrelaxation.privateinstance.TestMain;
import com.android.tools.r8.accessrelaxation.privatestatic.A;
import com.android.tools.r8.accessrelaxation.privatestatic.B;
import com.android.tools.r8.accessrelaxation.privatestatic.BB;
import com.android.tools.r8.accessrelaxation.privatestatic.C;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class AccessRelaxationTest extends TestBase {
  private static final String STRING = "java.lang.String";

  private static R8Command.Builder loadProgramFiles(Package p, Class... classes) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestPackage(p));
    for (Class clazz : classes) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    return builder;
  }

  private void compareJvmAndArt(AndroidApp app, Class mainClass) throws Exception {
    // Run on Jvm.
    String jvmOutput = runOnJava(mainClass);

    // Run on Art to check generated code against verifier.
    String artOutput = runOnArt(app, mainClass);

    String adjustedArtOutput = artOutput.replace(
        "java.lang.IncompatibleClassChangeError", "java.lang.IllegalAccessError");
    assertEquals(jvmOutput, adjustedArtOutput);
  }

  private static void assertPublic(
      DexInspector dexInspector, Class clazz, MethodSignature signature) {
    ClassSubject classSubject = dexInspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(signature);
    assertThat(methodSubject, isPublic());
  }

  private static void assertNotPublic(
      DexInspector dexInspector, Class clazz, MethodSignature signature) {
    ClassSubject classSubject = dexInspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(signature);
    assertThat(methodSubject, not(isPublic()));
  }

  @Test
  public void testStaticMethodRelaxation() throws Exception {
    Class mainClass = C.class;
    R8Command.Builder builder = loadProgramFiles(mainClass.getPackage());

    // Note: we use '-checkdiscard' to indirectly check that the access relaxation is
    // done which leads to inlining of all pB*** methods so they are removed. Without
    // access relaxation inlining is not performed and method are kept.
    builder.addProguardConfiguration(
        ImmutableList.of(
            "-keep class " + mainClass.getCanonicalName() + "{",
            "  public static void main(java.lang.String[]);",
            "}",
            "",
            "-checkdiscard class " + A.class.getCanonicalName() + "{",
            "  *** pBaz();",
            "  *** pBar();",
            "  *** pBar1();",
            "  *** pBlah1();",
            "}",
            "",
            "-checkdiscard class " + B.class.getCanonicalName() + "{",
            "  *** pBlah1();",
            "}",
            "",
            "-checkdiscard class " + BB.class.getCanonicalName() + "{",
            "  *** pBlah1();",
            "}",
            "",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());

    AndroidApp app = ToolHelper.runR8(builder.build());
    compareJvmAndArt(app, mainClass);

    DexInspector dexInspector = new DexInspector(app);
    assertPublic(dexInspector, A.class,
        new MethodSignature("baz", STRING, ImmutableList.of()));
    assertPublic(dexInspector, A.class,
        new MethodSignature("bar", STRING, ImmutableList.of()));
    assertPublic(dexInspector, A.class,
        new MethodSignature("bar", STRING, ImmutableList.of("int")));
    assertPublic(dexInspector, A.class,
        new MethodSignature("blah", STRING, ImmutableList.of("int")));

    assertPublic(dexInspector, B.class,
        new MethodSignature("blah", STRING, ImmutableList.of("int")));

    assertPublic(dexInspector, BB.class,
        new MethodSignature("blah", STRING, ImmutableList.of("int")));
  }

  @Test
  public void testInstanceMethodRelaxation() throws Exception {
    Class mainClass = TestMain.class;
    R8Command.Builder builder = loadProgramFiles(mainClass.getPackage());

    builder.addProguardConfiguration(
        ImmutableList.of(
            "-keep class " + mainClass.getCanonicalName() + "{",
            "  public static void main(java.lang.String[]);",
            "}",
            "",
            "-checkdiscard class " + Base.class.getCanonicalName() + "{",
            "  *** p*();",
            "}",
            "",
            "-checkdiscard class " + Sub1.class.getCanonicalName() + "{",
            "  *** p*();",
            "}",
            "",
            "-checkdiscard class " + Sub2.class.getCanonicalName() + "{",
            "  *** p*();",
            "}",
            "",
            "-dontobfuscate",
            "-allowaccessmodification"
        ),
        Origin.unknown());

    AndroidApp app = ToolHelper.runR8(builder.build());
    compareJvmAndArt(app, mainClass);

    DexInspector dexInspector = new DexInspector(app);
    assertPublic(dexInspector, Base.class,
        new MethodSignature("foo", STRING, ImmutableList.of()));

    // Base#foo?() can't be publicized due to Itf<1>#foo<1>().
    assertNotPublic(dexInspector, Base.class,
        new MethodSignature("foo1", STRING, ImmutableList.of()));
    assertNotPublic(dexInspector, Base.class,
        new MethodSignature("foo2", STRING, ImmutableList.of()));

    // Sub1#bar1(int) can't be publicized due to Base#bar1(int).
    assertNotPublic(dexInspector, Sub1.class,
        new MethodSignature("bar1", STRING, ImmutableList.of("int")));

    assertPublic(dexInspector, Sub2.class,
        new MethodSignature("bar2", STRING, ImmutableList.of("int")));
  }
}
