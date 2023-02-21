// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class RepackageCustomMethodHandleTest extends RepackageTestBase {

  private static final String EXPECTED = "InvokeCustom::foo";

  @Parameters(name = "{1}, kind: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters()
            .withCfRuntimes()
            .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.O_MR1)
            .build());
  }

  public RepackageCustomMethodHandleTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(InvokeCustom.class)
        .addProgramClassFileData(
            transformer(Main.class).addClassTransformer(generateCallSiteInvoke()).transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test()
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(InvokeCustom.class)
        .addProgramClassFileData(
            transformer(Main.class).addClassTransformer(generateCallSiteInvoke()).transform())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Main.class)
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(InvokeCustom.class, isRepackaged(inspector)))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private ClassTransformer generateCallSiteInvoke() {
    return new ClassTransformer() {
      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        MethodVisitor mv =
            cv.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                "main",
                "([Ljava/lang/String;)V",
                null,
                null);
        MethodType mt =
            MethodType.methodType(
                CallSite.class,
                MethodHandles.Lookup.class,
                String.class,
                MethodType.class,
                MethodHandle.class);
        Handle bootstrap =
            new Handle(
                Opcodes.H_INVOKESTATIC,
                Type.getInternalName(InvokeCustom.class),
                "createCallSite",
                mt.toMethodDescriptorString(),
                false);
        mv.visitTypeInsn(Opcodes.NEW, Type.getInternalName(InvokeCustom.class));
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            Type.getInternalName(InvokeCustom.class),
            "<init>",
            "()V",
            false);
        mv.visitInvokeDynamicInsn(
            "foo",
            "(" + DescriptorUtils.javaTypeToDescriptor(InvokeCustom.class.getTypeName()) + ")V",
            bootstrap,
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                Type.getInternalName(InvokeCustom.class),
                "foo",
                "()V",
                false));
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(10, 10);
      }
    };
  }

  @NeverClassInline
  public static class InvokeCustom {

    @NeverInline
    public void foo() {
      System.out.println("InvokeCustom::foo");
    }

    public static CallSite createCallSite(
        MethodHandles.Lookup caller, String name, MethodType type, MethodHandle mh) {
      return new ConstantCallSite(mh);
    }
  }

  public static class Main {

    /*
    public static void main(String[] args) {
      CallSite cs = { InvokeCustom::foo, args: InvokeCustom }
      cs.invoke(new InvokeCustom());
    }
     */
  }
}
