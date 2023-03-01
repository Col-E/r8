// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumer.ForwardingConsumer;
import com.android.tools.r8.ClassFileConsumerData;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DesugarToClassFileDeprecatedAttribute extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private boolean isDeprecated(int access) {
    return (access & Opcodes.ACC_DEPRECATED) == Opcodes.ACC_DEPRECATED;
  }

  private void checkDeprecatedAttributes(byte[] classBytes) {
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(
        new ClassVisitor(InternalOptions.ASM_VERSION) {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            assertTrue(isDeprecated(access));
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String signature, String[] exceptions) {
            if (!name.equals("<init>")) {
              assertTrue(isDeprecated(access));
            }
            return super.visitMethod(access, name, desc, signature, exceptions);
          }

          @Override
          public FieldVisitor visitField(
              int access, String name, String descriptor, String signature, Object value) {
            assertTrue(isDeprecated(access));
            return super.visitField(access, name, descriptor, signature, value);
          }
        },
        0);
  }

  @Test
  public void test() throws Exception {
    checkDeprecatedAttributes(
        Files.readAllBytes(ToolHelper.getClassFileForTestClass(TestClass.class)));

    // Use D8 to desugar with Java classfile output.
    D8TestCompileResult desugarCompileResult =
        testForD8(Backend.CF)
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters)
            .setProgramConsumer(
                new ClassFileConsumer.ForwardingConsumer(null) {
                  @Override
                  public void acceptClassFile(ClassFileConsumerData data) {
                    checkDeprecatedAttributes(data.getByteDataView().getBuffer());
                  }
                })
            .compile();

    if (parameters.getRuntime().isCf()) {
      // Run on the JVM.
      desugarCompileResult
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!");
    } else {
      assert parameters.getRuntime().isDex();
      // Convert to DEX without desugaring.
      testForD8()
          .addProgramFiles(desugarCompileResult.writeToZip())
          .setMinApi(parameters)
          .disableDesugaring()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!");
    }
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .addKeepAllAttributes()
            .setMinApi(parameters);
    if (parameters.isCfRuntime()) {
      builder.setProgramConsumer(
          new ForwardingConsumer(null) {
            @Override
            public void acceptClassFile(ClassFileConsumerData data) {
              checkDeprecatedAttributes(data.getByteDataView().getBuffer());
            }
          });
    }
    builder
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Deprecated
  public static class TestClass {
    @Deprecated public Object object = new Object();

    @Deprecated
    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
