// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.MethodAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.keepanno.ast.AnnotationConstants.MethodAccess;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class KeepAccessFlagsOnMethodsTest extends TestBase {

  private static final List<AccessFlagConfig> CONFIGS =
      ImmutableList.<AccessFlagConfig>builder()
          .addAll(AccessFlagConfig.MEMBER_CONFIGS)
          .add(new AccessFlagConfig(MethodAccess.NATIVE, Opcodes.ACC_NATIVE))
          .add(new AccessFlagConfig(MethodAccess.ABSTRACT, Opcodes.ACC_ABSTRACT))
          .add(new AccessFlagConfig(MethodAccess.STRICT_FP, Opcodes.ACC_STRICT))
          .build();

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public AccessFlagConfig config;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    List<AccessFlagConfig> configs = new ArrayList<>(CONFIGS.size() * 2);
    CONFIGS.forEach(
        c -> {
          configs.add(c);
          configs.add(c.invert());
        });
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build(),
        configs);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .enableExperimentalKeepAnnotations()
        .addProgramClassFileData(getTargetClass())
        .addProgramClassFileData(getMainClass())
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspect(this::checkOutput);
  }

  public byte[] getTargetClass() throws Exception {
    return transformer(A.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (name.equals("x")) {
                  access = config.positive;
                }
                if (name.equals("y")) {
                  access = config.negative;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
              }
            })
        .transform();
  }

  public byte[] getMainClass() throws Exception {
    return transformer(TestClass.class)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                assertEquals(AnnotationConstants.UsesReflection.DESCRIPTOR, descriptor);
                return new AnnotationVisitor(
                    ASM_VERSION, super.visitAnnotation(descriptor, visible)) {
                  @Override
                  public AnnotationVisitor visitArray(String name) {
                    assertEquals(AnnotationConstants.UsesReflection.value, name);
                    return new AnnotationVisitor(ASM_VERSION, super.visitArray(name)) {
                      @Override
                      public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        assertEquals(AnnotationConstants.Target.DESCRIPTOR, descriptor);
                        return new AnnotationVisitor(
                            ASM_VERSION, super.visitAnnotation(name, descriptor)) {
                          @Override
                          public AnnotationVisitor visitArray(String name) {
                            assertEquals(AnnotationConstants.Item.methodAccess, name);
                            AnnotationVisitor visitor = super.visitArray(name);
                            visitor.visitEnum(
                                null,
                                AnnotationConstants.MethodAccess.DESCRIPTOR,
                                config.enumValue);
                            visitor.visitEnd();
                            return null;
                          }
                        };
                      }
                    };
                  }
                };
              }
            })
        .transform();
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("x"), isPresent());
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("y"), isAbsent());
  }

  static class A {
    int x() {
      return 1;
    }

    int y() {
      return 2;
    }
  }

  static class TestClass {

    @UsesReflection({
      @KeepTarget(
          classConstant = A.class,
          methodAccess = {MethodAccessFlags.PUBLIC})
    })
    public static void main(String[] args) throws Exception {
      Object o = System.nanoTime() > 0 ? new A() : null;
      for (Method m : o.getClass().getDeclaredMethods()) {
        System.out.println(m.getName());
      }
    }
  }
}
