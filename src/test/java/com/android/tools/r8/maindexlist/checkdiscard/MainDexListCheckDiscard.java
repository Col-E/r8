// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.checkdiscard;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ListUtils;
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
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(
                readClasses(HelloWorldMain.class, MainDexClass.class, NonMainDexClass.class))
            .addMainDexRules(
                ImmutableList.of(keepMainProguardConfiguration(HelloWorldMain.class)),
                Origin.unknown())
            .addMainDexRules(ImmutableList.of(checkDiscardRule), Origin.unknown())
            .setOutput(temp.getRoot().toPath(), OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .build();
    ToolHelper.runR8(command);
  }

  public void runTestWithGenerator(String checkDiscardRule) throws Exception {
    GenerateMainDexListCommand.Builder builder =
        GenerateMainDexListCommand.builder()
            .addProgramFiles(ListUtils.map(CLASSES, ToolHelper::getClassFileForTestClass))
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
            .addMainDexRules(
                ImmutableList.of(keepMainProguardConfiguration(HelloWorldMain.class)),
                Origin.unknown())
            .addMainDexRules(ImmutableList.of(checkDiscardRule), Origin.unknown());
    GenerateMainDexList.run(builder.build());
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
