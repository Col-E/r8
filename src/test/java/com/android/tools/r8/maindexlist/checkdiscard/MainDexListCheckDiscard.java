// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.checkdiscard;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

class HelloWorldMain {
  public static void main(String[] args) {
    System.out.println(new MainDexClass());
  }
}

class MainDexClass {}

class NonMainDexClass {}

public class MainDexListCheckDiscard extends TestBase {
  public void runTest(String checkDiscardRule, boolean shouldFail) throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(
                readClasses(HelloWorldMain.class, MainDexClass.class, NonMainDexClass.class))
            .addMainDexRules(
                ImmutableList.of(keepMainProguardConfiguration(HelloWorldMain.class)),
                Origin.unknown())
            .addMainDexRules(ImmutableList.of(checkDiscardRule), Origin.unknown())
            .setOutput(temp.getRoot().toPath(), OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)
            .build();
    try {
      ToolHelper.runR8(command);
    } catch (CompilationFailedException e) {
      Assert.assertTrue(shouldFail);
      return;
    }
    Assert.assertFalse(shouldFail);
  }

  @Test
  public void testMainDexClassNotDiscarded() throws Exception {
    runTest("-checkdiscard class " + MainDexClass.class.getCanonicalName(), true);
  }

  @Test
  public void testNonMainDexClassDiscarded() throws Exception {
    runTest("-checkdiscard class " + NonMainDexClass.class.getCanonicalName(), false);
  }
}
