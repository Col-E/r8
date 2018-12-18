// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.whyareyoukeeping;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.GenerateMainDexList;
import com.android.tools.r8.GenerateMainDexListCommand;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graphinfo.GraphConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
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
public class MainDexListWhyAreYouKeeping extends TestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(HelloWorldMain.class, MainDexClass.class, NonMainDexClass.class);

  private enum Command {
    R8,
    Generator
  }

  private enum ApiUse {
    WhyAreYouKeepingRule,
    KeptGraphConsumer
  }

  @Parameters(name = "{0} {1}")
  public static List<Object[]> parameters() {
    return buildParameters(Command.values(), ApiUse.values());
  }

  private final Command command;
  private final ApiUse apiUse;

  public MainDexListWhyAreYouKeeping(Command command, ApiUse apiUse) {
    this.command = command;
    this.apiUse = apiUse;
  }

  public void runTestWithGenerator(GraphConsumer consumer, String rule) throws Exception {
    GenerateMainDexListCommand.Builder builder =
        GenerateMainDexListCommand.builder()
            .addProgramFiles(ListUtils.map(CLASSES, ToolHelper::getClassFileForTestClass))
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
            .addMainDexRules(
                ImmutableList.of(keepMainProguardConfiguration(HelloWorldMain.class)),
                Origin.unknown())
            .setMainDexKeptGraphConsumer(consumer);
    if (rule != null) {
      builder.addMainDexRules(ImmutableList.of(rule), Origin.unknown());
    }
    GenerateMainDexList.run(builder.build());
  }

  public void runTestWithR8(GraphConsumer consumer, String rule) throws Exception {
    R8TestBuilder builder =
        testForR8(Backend.DEX)
            .setMinApi(AndroidApiLevel.L)
            .addProgramClasses(CLASSES)
            .addMainDexRules(keepMainProguardConfiguration(HelloWorldMain.class))
            .setMainDexKeptGraphConsumer(consumer);
    if (rule != null) {
      builder.addMainDexRules(rule);
    }
    builder.compile();
  }

  private String runTest(Class clazz) throws Exception {
    PrintStream stdout = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String rule = null;
    WhyAreYouKeepingConsumer consumer = null;
    if (apiUse == ApiUse.KeptGraphConsumer) {
      consumer = new WhyAreYouKeepingConsumer(null);
    } else {
      assert apiUse == ApiUse.WhyAreYouKeepingRule;
      rule = "-whyareyoukeeping class " + clazz.getTypeName();
      System.setOut(new PrintStream(baos));
    }
    switch (command) {
      case R8:
        runTestWithR8(consumer, rule);
        break;
      case Generator:
        runTestWithGenerator(consumer, rule);
        break;
      default:
        throw new Unreachable();
    }
    if (consumer != null) {
      consumer.printWhyAreYouKeeping(
          DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()), new PrintStream(baos));
    } else {
      System.setOut(stdout);
    }
    return baos.toString();
  }

  @Test
  public void testMainDexClassWhyAreYouKeeping() throws Exception {
    String expected =
        StringUtils.lines(
            "com.android.tools.r8.maindexlist.whyareyoukeeping.MainDexClass",
            "|- is instantiated in:",
            "|  void com.android.tools.r8.maindexlist.whyareyoukeeping.HelloWorldMain.main(java.lang.String[])",
            "|- is referenced in keep rule:",
            "|  -keep class com.android.tools.r8.maindexlist.whyareyoukeeping.HelloWorldMain {",
            "|    public static void main(java.lang.String[]);",
            "|  }");
    assertEquals(expected, runTest(MainDexClass.class));
  }

  @Test
  public void testNonMainDexWhyAreYouKeeping() throws Exception {
    String expected =
        StringUtils.lines(
            "Nothing is keeping com.android.tools.r8.maindexlist.whyareyoukeeping.NonMainDexClass");
    assertEquals(expected, runTest(NonMainDexClass.class));
  }
}
