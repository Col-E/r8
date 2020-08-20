// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DeterministicPrintUsagesTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DeterministicPrintUsagesTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public static class UsageConsumer implements StringConsumer {

    List<String> strings = new ArrayList<>();

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      strings.add(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      strings = Collections.unmodifiableList(strings);
    }
  }

  @Test
  public void test() throws Exception {
    List<String> names = getClassNames();
    List<byte[]> classes = getClasses(names);
    UsageConsumer usage1 = getUsageInfo(classes);
    UsageConsumer usage2 = getUsageInfo(classes);
    assertEquals(usage1.strings, usage2.strings);
    String content = String.join("", usage1.strings);
    for (String name : names) {
      assertThat(content, containsString(name));
    }
    for (int i = 0; i < 10; i++) {
      assertThat(content, containsString("int f" + i));
      assertThat(content, containsString("void m" + i + "()"));
    }
  }

  private List<String> getClassNames() {
    List<String> descriptors = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      descriptors.add("a.A" + i);
    }
    return descriptors;
  }

  private List<byte[]> getClasses(List<String> names) throws Exception {
    List<byte[]> classes = new ArrayList<>();
    for (String name : names) {
      classes.add(
          transformer(A.class)
              .setClassDescriptor(DescriptorUtils.javaTypeToDescriptor(name))
              .transform());
    }
    return classes;
  }

  private UsageConsumer getUsageInfo(List<byte[]> classes) throws Exception {
    UsageConsumer consumer = new UsageConsumer();
    testForR8(Backend.CF)
        .addProgramClassFileData(classes)
        .addProgramClassFileData(
            transformer(TestClass.class)
                .addClassTransformer(
                    new ClassTransformer() {

                      @Override
                      public FieldVisitor visitField(
                          int access,
                          String name,
                          String descriptor,
                          String signature,
                          Object value) {
                        assertEquals("f", name);
                        for (int i = 0; i < 10; i++) {
                          FieldVisitor fv =
                              super.visitField(access, name + i, descriptor, signature, value);
                          fv.visitEnd();
                        }
                        return null;
                      }

                      @Override
                      public MethodVisitor visitMethod(
                          int access,
                          String name,
                          String descriptor,
                          String signature,
                          String[] exceptions) {
                        if (name.equals("m")) {
                          for (int i = 0; i < 10; i++) {
                            MethodVisitor mv =
                                super.visitMethod(
                                    access, name + i, descriptor, signature, exceptions);
                            mv.visitCode();
                            mv.visitInsn(Opcodes.RETURN);
                            mv.visitEnd();
                          }
                          return null;
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                      }
                    })
                .transform())
        .addKeepMainRule(TestClass.class)
        .apply(b -> b.getBuilder().setProguardUsageConsumer(consumer))
        .compile()
        .assertNoMessages();
    return consumer;
  }

  // Repeated with a transformer.
  static class A {}

  static class TestClass {

    // Repeated with a transformer.
    static int f;

    // Repeated with a transformer.
    static void m() {}

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
