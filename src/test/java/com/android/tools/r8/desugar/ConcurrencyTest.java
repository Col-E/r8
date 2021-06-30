// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.objectweb.asm.Opcodes;

// Test for reproducing b/192310793.
@RunWith(Parameterized.class)
public class ConcurrencyTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesEndingAtExcluding(JDK11)
            .withDexRuntimes()
            .withApiLevel(AndroidApiLevel.B)
            .build());
  }

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    ClassFileTransformer transformer =
        withNest(Host.class)
            .setVersion(CfVersion.V1_8)
            .transformMethodInsnInMethod(
                "callPrivate",
                ((opcode, owner, name, descriptor, isInterface, continuation) -> {
                  continuation.visitMethodInsn(
                      name.equals("hello") ? Opcodes.INVOKEVIRTUAL : opcode,
                      owner,
                      name,
                      descriptor,
                      isInterface);
                }));
    for (String s : new String[] {"a", "b", "c", "d", "e"}) {
      for (int i = 0; i < 10; i++) {
        transformer.setPrivate(Host.class.getDeclaredMethod(s + "0" + i));
      }
    }

    return ImmutableList.of(
        transformer.transform(),
        withNest(A.class).transform(),
        withNest(B.class).transform(),
        withNest(C.class).transform(),
        withNest(D.class).transform(),
        withNest(E.class).transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    return transformer(clazz).setNest(Host.class, A.class, B.class, C.class, D.class, E.class);
  }

  @Test
  public void testD8() throws Exception {
    try {
      testForD8(parameters.getBackend())
          .addProgramClasses(getClasses())
          .addProgramClassFileData(getTransformedClasses())
          .compile();
    } catch (CompilationFailedException e) {
      if (e.getCause() instanceof ArrayIndexOutOfBoundsException) {
        // TODO(b/192310793): This should not happen.
        return;
      }
      throw e;
    }
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.getBackend().isDex());

    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(getClasses())
          .addProgramClassFileData(getTransformedClasses())
          .setMinApi(parameters.getApiLevel())
          .addKeepAllClassesRule()
          .compile();
    } catch (CompilationFailedException e) {
      if (e.getCause() instanceof AssertionError
          && e.getCause()
              .getStackTrace()[0]
              .getClassName()
              .equals(
                  "com.android.tools.r8.ir.desugar.NonEmptyCfInstructionDesugaringCollection")) {
        // TODO(b/192446461): This should not happen.
        return;
      }
      throw e;
    }
  }

  static class Host {
    /* will be private */ void a00() {}
    /* will be private */ void a01() {}
    /* will be private */ void a02() {}
    /* will be private */ void a03() {}
    /* will be private */ void a04() {}
    /* will be private */ void a05() {}
    /* will be private */ void a06() {}
    /* will be private */ void a07() {}
    /* will be private */ void a08() {}
    /* will be private */ void a09() {}

    /* will be private */ void b00() {}
    /* will be private */ void b01() {}
    /* will be private */ void b02() {}
    /* will be private */ void b03() {}
    /* will be private */ void b04() {}
    /* will be private */ void b05() {}
    /* will be private */ void b06() {}
    /* will be private */ void b07() {}
    /* will be private */ void b08() {}
    /* will be private */ void b09() {}

    /* will be private */ void c00() {}
    /* will be private */ void c01() {}
    /* will be private */ void c02() {}
    /* will be private */ void c03() {}
    /* will be private */ void c04() {}
    /* will be private */ void c05() {}
    /* will be private */ void c06() {}
    /* will be private */ void c07() {}
    /* will be private */ void c08() {}
    /* will be private */ void c09() {}

    /* will be private */ void d00() {}
    /* will be private */ void d01() {}
    /* will be private */ void d02() {}
    /* will be private */ void d03() {}
    /* will be private */ void d04() {}
    /* will be private */ void d05() {}
    /* will be private */ void d06() {}
    /* will be private */ void d07() {}
    /* will be private */ void d08() {}
    /* will be private */ void d09() {}

    /* will be private */ void e00() {}
    /* will be private */ void e01() {}
    /* will be private */ void e02() {}
    /* will be private */ void e03() {}
    /* will be private */ void e04() {}
    /* will be private */ void e05() {}
    /* will be private */ void e06() {}
    /* will be private */ void e07() {}
    /* will be private */ void e08() {}
    /* will be private */ void e09() {}

    private void hello() {}

    public static void callPrivate() {
      // The private method "hello" is called with invokevirtual.
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
      new Host().hello();
    }
  }

  static class A {
    public void foo() {
      // Will be virtual invoke to private methods.
      new Host().a00();
      new Host().a01();
      new Host().a02();
      new Host().a03();
      new Host().a04();
      new Host().a05();
      new Host().a06();
      new Host().a07();
      new Host().a08();
      new Host().a09();
    }
  }

  static class B {
    public void foo() {
      // Will be virtual invoke to private methods.
      new Host().b00();
      new Host().b01();
      new Host().b02();
      new Host().b03();
      new Host().b04();
      new Host().b05();
      new Host().b06();
      new Host().b07();
      new Host().b08();
      new Host().b09();
    }
  }

  static class C {
    public void foo() {
      // Will be virtual invoke to private methods.
      new Host().c00();
      new Host().c01();
      new Host().c02();
      new Host().c03();
      new Host().c04();
      new Host().c05();
      new Host().c06();
      new Host().c07();
      new Host().c08();
      new Host().c09();
    }
  }

  static class D {
    public void foo() {
      // Will be virtual invoke to private methods.
      new Host().d00();
      new Host().d01();
      new Host().d02();
      new Host().d03();
      new Host().d04();
      new Host().d05();
      new Host().d06();
      new Host().d07();
      new Host().d08();
      new Host().d09();
    }
  }

  static class E {
    public void foo() {
      // Will be virtual invoke to private methods.
      new Host().e00();
      new Host().e01();
      new Host().e02();
      new Host().e03();
      new Host().e04();
      new Host().e05();
      new Host().e06();
      new Host().e07();
      new Host().e08();
      new Host().e09();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new A().foo();
      new B().foo();
      new C().foo();
      new D().foo();
      new E().foo();
      Host.callPrivate();
    }
  }
}
