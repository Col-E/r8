// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b71604169;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class Regress71604169Test extends TestBase {
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

    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    assertEquals("Hello, world!", runOnArt(ToolHelper.runR8(builder.build()), mainClass));
  }
}
