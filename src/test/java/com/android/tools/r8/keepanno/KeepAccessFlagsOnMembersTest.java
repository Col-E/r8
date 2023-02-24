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
import com.android.tools.r8.keepanno.annotations.MemberAccessFlags;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.ast.AnnotationConstants;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class KeepAccessFlagsOnMembersTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public AccessFlagConfig config;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    List<AccessFlagConfig> configs = new ArrayList<>(AccessFlagConfig.MEMBER_CONFIGS.size() * 2);
    AccessFlagConfig.MEMBER_CONFIGS.forEach(
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
        .setMinApi(parameters)
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
              public FieldVisitor visitField(
                  int access, String name, String descriptor, String signature, Object value) {
                if (name.equals("x")) {
                  access = config.positive;
                }
                if (name.equals("y")) {
                  access = config.negative;
                }
                return super.visitField(access, name, descriptor, signature, value);
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (name.equals("foo")) {
                  access = config.positive;
                }
                if (name.equals("bar")) {
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
                            assertEquals(AnnotationConstants.Item.memberAccess, name);
                            AnnotationVisitor visitor = super.visitArray(name);
                            visitor.visitEnum(
                                null,
                                AnnotationConstants.MemberAccess.DESCRIPTOR,
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
    assertThat(inspector.clazz(A.class).uniqueFieldWithOriginalName("x"), isPresent());
    assertThat(inspector.clazz(A.class).uniqueFieldWithOriginalName("y"), isAbsent());
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("bar"), isAbsent());
  }

  static class A {
    int x;

    int y;

    int foo() {
      return 1;
    }

    int bar() {
      return 2;
    }
  }

  static class TestClass {

    @UsesReflection({
      @KeepTarget(
          classConstant = A.class,
          memberAccess = {MemberAccessFlags.PUBLIC})
    })
    public static void main(String[] args) {
      Object o = System.nanoTime() > 0 ? new A() : null;
      for (Field f : o.getClass().getDeclaredFields()) {
        System.out.println(f.getName());
      }
      for (Method m : o.getClass().getDeclaredMethods()) {
        System.out.println(m.getName());
      }
    }
  }
}
