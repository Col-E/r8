// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b71604169;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress71604169Test extends TestBase {
  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public Regress71604169Test(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    R8Command.Builder builder = R8Command.builder();
    // Add application classes.
    Class mainClass = Regress71604169.class;
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(mainClass));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(Regress71604169.X.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(Regress71604169.Creator.class));

    // Keep main class.
    builder.addProguardConfiguration(
        ImmutableList.of(keepMainProguardConfiguration(mainClass, true, false)), Origin.unknown());

    if (backend == Backend.DEX) {
      builder
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    } else {
      assert backend == Backend.CF;
      builder
          .setProgramConsumer(ClassFileConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    assertEquals(
        "Hello, world!",
        backend == Backend.DEX
            ? runOnArt(ToolHelper.runR8(builder.build()), mainClass)
            : runOnJava(ToolHelper.runR8(builder.build()), mainClass));
  }
}
