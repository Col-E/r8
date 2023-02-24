// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
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
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesEndingAtExcluding(JDK11)
        .withDexRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .build();
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
        transformer.setPrivate(Host.class.getDeclaredMethod(s + "0" + i, int.class));
        transformer.setPrivate(Host.class.getDeclaredField("f" + s + "0" + i));
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
    testForD8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .compile();
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.getBackend().isDex());

    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepAllClassesRule()
        .compile();
  }

  static class Host {
    /* will be private */ int fa00;
    /* will be private */ int fa01;
    /* will be private */ int fa02;
    /* will be private */ int fa03;
    /* will be private */ int fa04;
    /* will be private */ int fa05;
    /* will be private */ int fa06;
    /* will be private */ int fa07;
    /* will be private */ int fa08;
    /* will be private */ int fa09;

    /* will be private */ int fb00;
    /* will be private */ int fb01;
    /* will be private */ int fb02;
    /* will be private */ int fb03;
    /* will be private */ int fb04;
    /* will be private */ int fb05;
    /* will be private */ int fb06;
    /* will be private */ int fb07;
    /* will be private */ int fb08;
    /* will be private */ int fb09;

    /* will be private */ int fc00;
    /* will be private */ int fc01;
    /* will be private */ int fc02;
    /* will be private */ int fc03;
    /* will be private */ int fc04;
    /* will be private */ int fc05;
    /* will be private */ int fc06;
    /* will be private */ int fc07;
    /* will be private */ int fc08;
    /* will be private */ int fc09;

    /* will be private */ int fd00;
    /* will be private */ int fd01;
    /* will be private */ int fd02;
    /* will be private */ int fd03;
    /* will be private */ int fd04;
    /* will be private */ int fd05;
    /* will be private */ int fd06;
    /* will be private */ int fd07;
    /* will be private */ int fd08;
    /* will be private */ int fd09;

    /* will be private */ int fe00;
    /* will be private */ int fe01;
    /* will be private */ int fe02;
    /* will be private */ int fe03;
    /* will be private */ int fe04;
    /* will be private */ int fe05;
    /* will be private */ int fe06;
    /* will be private */ int fe07;
    /* will be private */ int fe08;
    /* will be private */ int fe09;

    /* will be private */ int a00(int x) {
      return x;
    }
    /* will be private */ int a01(int x) {
      return x;
    }
    /* will be private */ int a02(int x) {
      return x;
    }
    /* will be private */ int a03(int x) {
      return x;
    }
    /* will be private */ int a04(int x) {
      return x;
    }
    /* will be private */ int a05(int x) {
      return x;
    }
    /* will be private */ int a06(int x) {
      return x;
    }
    /* will be private */ int a07(int x) {
      return x;
    }
    /* will be private */ int a08(int x) {
      return x;
    }
    /* will be private */ int a09(int x) {
      return x;
    }

    /* will be private */ int b00(int x) {
      return x;
    }
    /* will be private */ int b01(int x) {
      return x;
    }
    /* will be private */ int b02(int x) {
      return x;
    }
    /* will be private */ int b03(int x) {
      return x;
    }
    /* will be private */ int b04(int x) {
      return x;
    }
    /* will be private */ int b05(int x) {
      return x;
    }
    /* will be private */ int b06(int x) {
      return x;
    }
    /* will be private */ int b07(int x) {
      return x;
    }
    /* will be private */ int b08(int x) {
      return x;
    }
    /* will be private */ int b09(int x) {
      return x;
    }

    /* will be private */ int c00(int x) {
      return x;
    }
    /* will be private */ int c01(int x) {
      return x;
    }
    /* will be private */ int c02(int x) {
      return x;
    }
    /* will be private */ int c03(int x) {
      return x;
    }
    /* will be private */ int c04(int x) {
      return x;
    }
    /* will be private */ int c05(int x) {
      return x;
    }
    /* will be private */ int c06(int x) {
      return x;
    }
    /* will be private */ int c07(int x) {
      return x;
    }
    /* will be private */ int c08(int x) {
      return x;
    }
    /* will be private */ int c09(int x) {
      return x;
    }

    /* will be private */ int d00(int x) {
      return x;
    }
    /* will be private */ int d01(int x) {
      return x;
    }
    /* will be private */ int d02(int x) {
      return x;
    }
    /* will be private */ int d03(int x) {
      return x;
    }
    /* will be private */ int d04(int x) {
      return x;
    }
    /* will be private */ int d05(int x) {
      return x;
    }
    /* will be private */ int d06(int x) {
      return x;
    }
    /* will be private */ int d07(int x) {
      return x;
    }
    /* will be private */ int d08(int x) {
      return x;
    }
    /* will be private */ int d09(int x) {
      return x;
    }

    /* will be private */ int e00(int x) {
      return x;
    }
    /* will be private */ int e01(int x) {
      return x;
    }
    /* will be private */ int e02(int x) {
      return x;
    }
    /* will be private */ int e03(int x) {
      return x;
    }
    /* will be private */ int e04(int x) {
      return x;
    }
    /* will be private */ int e05(int x) {
      return x;
    }
    /* will be private */ int e06(int x) {
      return x;
    }
    /* will be private */ int e07(int x) {
      return x;
    }
    /* will be private */ int e08(int x) {
      return x;
    }
    /* will be private */ int e09(int x) {
      return x;
    }

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
      Host host = new Host();
      host.fa00 = host.a00(host.fa00);
      host.fa01 = host.a01(host.fa01);
      host.fa02 = host.a02(host.fa02);
      host.fa03 = host.a03(host.fa03);
      host.fa04 = host.a04(host.fa04);
      host.fa05 = host.a05(host.fa05);
      host.fa06 = host.a06(host.fa06);
      host.fa07 = host.a07(host.fa07);
      host.fa08 = host.a08(host.fa08);
      host.fa09 = host.a09(host.fa09);
    }
  }

  static class B {
    public void foo() {
      // Will be virtual invoke to private methods.
      Host host = new Host();
      host.fb00 = host.b00(host.fb00);
      host.fb01 = host.b01(host.fb01);
      host.fb02 = host.b02(host.fb02);
      host.fb03 = host.b03(host.fb03);
      host.fb04 = host.b04(host.fb04);
      host.fb05 = host.b05(host.fb05);
      host.fb06 = host.b06(host.fb06);
      host.fb07 = host.b07(host.fb07);
      host.fb08 = host.b08(host.fb08);
      host.fb09 = host.b09(host.fb09);
    }
  }

  static class C {
    public void foo() {
      // Will be virtual invoke to private methods.
      Host host = new Host();
      host.fc00 = host.c00(host.fc00);
      host.fc01 = host.c01(host.fc01);
      host.fc02 = host.c02(host.fc02);
      host.fc03 = host.c03(host.fc03);
      host.fc04 = host.c04(host.fc04);
      host.fc05 = host.c05(host.fc05);
      host.fc06 = host.c06(host.fc06);
      host.fc07 = host.c07(host.fc07);
      host.fc08 = host.c08(host.fc08);
      host.fc09 = host.c09(host.fc09);
    }
  }

  static class D {
    public void foo() {
      // Will be virtual invoke to private methods.
      Host host = new Host();
      host.fd00 = host.d00(host.fd00);
      host.fd01 = host.d01(host.fd01);
      host.fd02 = host.d02(host.fd02);
      host.fd03 = host.d03(host.fd03);
      host.fd04 = host.d04(host.fd04);
      host.fd05 = host.d05(host.fd05);
      host.fd06 = host.d06(host.fd06);
      host.fd07 = host.d07(host.fd07);
      host.fd08 = host.d08(host.fd08);
      host.fd09 = host.d09(host.fd09);
    }
  }

  static class E {
    public void foo() {
      // Will be virtual invoke to private methods.
      Host host = new Host();
      host.fe00 = host.e00(host.fe00);
      host.fe01 = host.e01(host.fe01);
      host.fe02 = host.e02(host.fe02);
      host.fe03 = host.e03(host.fe03);
      host.fe04 = host.e04(host.fe04);
      host.fe05 = host.e05(host.fe05);
      host.fe06 = host.e06(host.fe06);
      host.fe07 = host.e07(host.fe07);
      host.fe08 = host.e08(host.fe08);
      host.fe09 = host.e09(host.fe09);
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
