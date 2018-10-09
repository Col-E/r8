// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.memberrebinding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IndirectSuperInterfaceTest extends TestBase {

  public interface Interface {
    @NeverInline
    default void foo() {
      System.out.print("Interface::foo ");
    }
  }

  public static class A implements Interface {
    // Intentionally empty.
  }

  public static class B extends A {
    @Override
    public void foo() {
      System.out.print("B::foo ");
      super.foo();
    }

    public static void main(String[] args) {
      new B().foo();
    }
  }

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] setup() {
    return new Backend[] {Backend.CF, Backend.DEX};
  }

  public IndirectSuperInterfaceTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected = "B::foo Interface::foo ";
    String reference = runOnJava(B.class);
    assertEquals(expected, reference);

    AndroidAppConsumers sink = new AndroidAppConsumers();
    Builder builder =
        R8Command.builder()
            .addClassProgramData(ToolHelper.getClassAsBytes(Interface.class), Origin.unknown())
            .addClassProgramData(ToolHelper.getClassAsBytes(A.class), Origin.unknown())
            .addClassProgramData(ToolHelper.getClassAsBytes(B.class), Origin.unknown())
            .setProgramConsumer(sink.wrapProgramConsumer(emptyConsumer(backend)))
            .addLibraryFiles(runtimeJar(backend))
            .addProguardConfiguration(
                ImmutableList.of(
                    "-keep class " + Interface.class.getTypeName(),
                    "-keep class " + A.class.getTypeName(),
                    keepMainProguardConfigurationWithInliningAnnotation(B.class)),
                Origin.unknown());
    ToolHelper.allowTestProguardOptions(builder);
    if (backend == Backend.DEX) {
      builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    }
    R8.run(builder.build());

    ProcessResult result = runOnVMRaw(sink.build(), B.class, backend);

    // TODO(b/117407667): Assert the test does not fail once fixed.
    assertTrue(result.toString(), result.exitCode == (backend == Backend.DEX ? 0 : 1));
    if (result.exitCode == 0) {
      assertEquals(reference, result.stdout);
    }
  }
}
