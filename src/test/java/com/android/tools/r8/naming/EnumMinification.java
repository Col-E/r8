// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumMinification extends TestBase {

  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public EnumMinification(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    AndroidApp output =
        ToolHelper.runR8(
            R8Command.builder()
                .addClassProgramData(ToolHelper.getClassAsBytes(Main.class), Origin.unknown())
                .addClassProgramData(ToolHelper.getClassAsBytes(Enum.class), Origin.unknown())
                .addProguardConfiguration(
                    ImmutableList.of(keepMainProguardConfiguration(Main.class)), Origin.unknown())
                .setProgramConsumer(emptyConsumer(backend))
                .build());

    // TODO(117299356): valueOf on enum fails for minified enums.
    ProcessResult result = runOnVMRaw(output, Main.class, backend);
    assertEquals(1, result.exitCode);
    assertThat(
        result.stderr,
        containsString(
            backend == Backend.DEX
                ? "java.lang.NoSuchMethodException"
                : "java.lang.IllegalArgumentException"));
  }
}

class Main {

  public static void main(String[] args) {
    Enum e = Enum.valueOf("VALUE1");
    System.out.println(e);
  }
}

enum Enum {
  VALUE1,
  VALUE2
}
