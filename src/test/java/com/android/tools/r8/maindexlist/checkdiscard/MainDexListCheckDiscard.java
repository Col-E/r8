// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.checkdiscard;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class HelloWorldMain {
  public static void main(String[] args) {
    System.out.println(new MainDexClass());
  }
}

class MainDexClass {}

class NonMainDexClass {}

@RunWith(Parameterized.class)
public class MainDexListCheckDiscard extends TestBase {
  private enum Command {
    R8,
    Generator
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(HelloWorldMain.class, MainDexClass.class, NonMainDexClass.class);

  @Parameters(name = "{0}")
  public static Object[] parameters() {
    return Command.values();
  }

  private final Command command;

  public MainDexListCheckDiscard(Command command) {
    this.command = command;
  }

  public void runTestWithR8(String checkDiscardRule) throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(CLASSES)
        .setMinApi(AndroidApiLevel.K)
        .addMainDexRules(keepMainProguardConfiguration(HelloWorldMain.class))
        .addMainDexRules(checkDiscardRule)
        .noTreeShaking()
        .noMinification()
        .compile();
  }

  public void runTestWithGenerator(String checkDiscardRule) throws Exception {
    testForMainDexListGenerator()
        .addProgramClasses(CLASSES)
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
        .addMainDexRules(keepMainProguardConfiguration(HelloWorldMain.class))
        .addMainDexRules(checkDiscardRule)
        .run();
  }

  public void runTest(String checkDiscardRule, boolean shouldFail) throws Exception {
    try {
      switch (command) {
        case R8:
          runTestWithR8(checkDiscardRule);
          break;
        case Generator:
          runTestWithGenerator(checkDiscardRule);
          break;
        default:
          throw new Unreachable();
      }
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
