// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.LibraryModeledPredicate;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest extends AsmTestBase {

  public interface A {
    default void f() {}
  }

  public interface B extends A {
    // Made static using ASM.
    /*static*/ default void f() {}
  }

  public static class C implements B {
    public void f() {
      System.out.println("Called C.f");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new C();
      b.f();
    }
  }

  public static class BDump implements Opcodes {

    static String prefix(String suffix) {
      return VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest.class
              .getTypeName()
              .replace('.', '/')
          + suffix;
    }

    public static byte[] dump() {
      ClassWriter cw = new ClassWriter(0);
      cw.visit(
          V1_8,
          ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE,
          prefix("$B"),
          null,
          "java/lang/Object",
          null);
      {
        // Added ACC_STATIC
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "f", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
  }

  public static List<Class<?>> CLASSES = ImmutableList.of(A.class, C.class, Main.class);

  public static byte[] DUMP = BDump.dump();

  private static AppView<AppInfoWithLiveness> appView;
  private static AppInfoWithLiveness appInfo;

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    appView =
        computeAppViewWithLiveness(
            buildClasses(CLASSES)
                .addClassProgramData(DUMP)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            Main.class);
    appInfo = appView.appInfo();
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return buildNullaryVoidMethod(clazz, name, appView.dexItemFactory());
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;
  private final DexMethod methodOnAReference = buildMethod(A.class, "f");
  private final DexMethod methodOnBReference = buildMethod(B.class, "f");
  private final DexMethod methodOnCReference = buildMethod(C.class, "f");

  public VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void lookupSingleTarget() {
    DexProgramClass bClass = appInfo.definitionForProgramType(methodOnBReference.holder);
    ProgramMethod methodOnB = bClass.lookupProgramMethod(methodOnBReference);
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnInterfaceLegacy(methodOnBReference.holder, methodOnBReference);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnBReference, resolved.getReference());
    assertFalse(resolutionResult.isVirtualTarget());
    DexClassAndMethod singleVirtualTarget =
        appInfo.lookupSingleVirtualTargetForTesting(
            appView,
            methodOnBReference,
            methodOnB,
            false,
            LibraryModeledPredicate.alwaysFalse(),
            DynamicType.unknown());
    Assert.assertNull(singleVirtualTarget);
  }

  @Test
  public void lookupVirtualTargets() {
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnInterfaceLegacy(methodOnBReference.holder, methodOnBReference);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnBReference, resolved.getReference());
    assertFalse(resolutionResult.isVirtualTarget());
  }

  @Test
  public void runJvmAndD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DUMP)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void runR8() throws Exception {
    runR8(true);
  }

  @Test
  public void runR8NoMerging() throws Exception {
    runR8(false);
  }

  public void runR8(boolean enableVerticalClassMerging) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DUMP)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addOptionsModification(
            options ->
                options.getVerticalClassMergerOptions().setEnabled(enableVerticalClassMerging))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }
}
