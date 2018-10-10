// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IndirectSuperInterfaceTest extends TestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(
          InterfaceA.class, InterfaceASub.class, A.class,
          InterfaceB.class, InterfaceBSub.class, B.class,
          InterfaceC.class, InterfaceCSub.class, C.class,
          InterfaceD.class, InterfaceDSub.class, D.class,
          TestClass.class);

  // Test A: class A extends an empty class that implements a non-empty interface.

  interface InterfaceA {
    @NeverInline
    default String method() {
      return "InterfaceA::method";
    }
  }

  static class InterfaceASub implements InterfaceA {
    // Intentionally empty.
  }

  static class A extends InterfaceASub {
    @Override
    public String method() {
      return "A::method -> " + super.method();
    }
  }

  // Test B: class B implements an empty interface that extends a non-empty interface.

  interface InterfaceB {
    @NeverInline
    default String method() {
      return "InterfaceB::method";
    }
  }

  interface InterfaceBSub extends InterfaceB {
    // Intentionally empty.
  }

  static class B implements InterfaceBSub {
    @Override
    public String method() {
      return "B::method -> " + InterfaceBSub.super.method();
    }
  }

  // Test C: class C extends a non-empty class that implements a non-empty interface.

  interface InterfaceC {
    @NeverInline
    default String method() {
      return "InterfaceC::method";
    }
  }

  static class InterfaceCSub implements InterfaceC {
    // This method is intentionally not annotated with @NeverInline. If we were to inline this
    // method we would risk introducing a super-invocation to InterfaceC.method() in C.method,
    // which would lead to an IncompatibleClassChangeError on the JVM.
    // (See also Art978_virtual_interfaceTest.)
    @Override
    public String method() {
      return InterfaceC.super.method();
    }
  }

  static class C extends InterfaceCSub {
    @Override
    public String method() {
      return "C::method -> " + super.method();
    }
  }

  // Test D: class D implements a non-empty empty interface that extends a non-empty interface.

  interface InterfaceD {
    @NeverInline
    default String method() {
      return "InterfaceD::method";
    }
  }

  interface InterfaceDSub extends InterfaceD {
    // This method is intentionally not annotated with @NeverInline. If we were to inline this
    // method we would risk introducing a super-invocation to InterfaceC.method() in C.method,
    // which would lead to an IncompatibleClassChangeError on the JVM.
    // (See also Art978_virtual_interfaceTest.)
    @Override
    default String method() {
      return InterfaceD.super.method();
    }
  }

  static class D implements InterfaceDSub {
    @Override
    public String method() {
      return "D::method -> " + InterfaceDSub.super.method();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A().method());
      System.out.println(new B().method());
      System.out.println(new C().method());
      System.out.print(new D().method());
    }
  }

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] setup() {
    return Backend.values();
  }

  public IndirectSuperInterfaceTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected =
        String.join(
            System.lineSeparator(),
            "A::method -> InterfaceA::method",
            "B::method -> InterfaceB::method",
            "C::method -> InterfaceC::method",
            "D::method -> InterfaceD::method");
    assertEquals(expected, runOnJava(TestClass.class));

    AndroidAppConsumers sink = new AndroidAppConsumers();
    Builder builder = R8Command.builder();
    for (Class<?> clazz : CLASSES) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(clazz), Origin.unknown());
    }
    builder
        .setProgramConsumer(sink.wrapProgramConsumer(emptyConsumer(backend)))
        .addLibraryFiles(runtimeJar(backend))
        .addProguardConfiguration(
            ImmutableList.of(
                // Keep all classes to prevent changes to the class hierarchy (e.g., due to
                // vertical class merging).
                "-keep class " + InterfaceA.class.getPackage().getName() + ".*",
                keepMainProguardConfigurationWithInliningAnnotation(TestClass.class)),
            Origin.unknown());
    ToolHelper.allowTestProguardOptions(builder);
    if (backend == Backend.DEX) {
      builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    }
    R8.run(builder.build());

    ProcessResult result = runOnVMRaw(sink.build(), TestClass.class, backend);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals(expected, result.stdout);
  }
}
