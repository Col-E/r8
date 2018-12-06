// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.warnings;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class MainDexWarningsTest extends TestBase {

  private List<Class<?>> testClasses = ImmutableList.of(Main.class, Static.class, Static2.class);
  private Class<?> mainClass = Main.class;

  private void classStaticGone(CodeInspector inspector) {
    assertThat(inspector.clazz(Static.class), CoreMatchers.not(isPresent()));
    assertThat(inspector.clazz(Static2.class), CoreMatchers.not(isPresent()));
  }

  @Test
  public void testNoWarningFromMainDexRules() throws Exception {
    testForR8(Backend.DEX)
        .setMinApi(AndroidApiLevel.K)
        .enableInliningAnnotations()
        .addProgramClasses(testClasses)
        .addKeepMainRule(mainClass)
        // Include main dex rule for class Static.
        .addMainDexClassRules(Main.class, Static.class)
        .compile()
        .inspect(this::classStaticGone)
        .assertNoMessages();
  }

  @Test
  public void testWarningFromManualMainDexList() throws Exception {
    testForR8(Backend.DEX)
        .setMinApi(AndroidApiLevel.K)
        .enableInliningAnnotations()
        .addProgramClasses(testClasses)
        .addKeepMainRule(mainClass)
        // Include explicit main dex entry for class Static.
        .addMainDexListClasses(Static.class)
        .compile()
        .inspect(this::classStaticGone)
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(containsString("Application does not contain"))
        .assertWarningMessageThatMatches(containsString(Static.class.getTypeName()))
        .assertWarningMessageThatMatches(containsString("as referenced in main-dex-list"));
  }

  @Test
  public void testWarningFromManualMainDexListWithRuleAsWell() throws Exception {
    testForR8(Backend.DEX)
        .setMinApi(AndroidApiLevel.K)
        .enableInliningAnnotations()
        .addProgramClasses(testClasses)
        .addKeepMainRule(mainClass)
        // Include explicit main dex entry for class Static.
        .addMainDexListClasses(Main.class, Static.class)
        // Include main dex rule for class Static2.
        .addMainDexClassRules(Static2.class)
        .compile()
        .inspect(this::classStaticGone)
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(containsString("Application does not contain"))
        .assertWarningMessageThatMatches(containsString(Static.class.getTypeName()))
        .assertWarningMessageThatMatches(containsString("as referenced in main-dex-list"))
        .assertNoWarningMessageThatMatches(containsString(Static2.class.getTypeName()));
  }
}

class Main {
  public static void main(String[] args) {
    System.out.println(Static.m());
    System.out.println(Static2.m());
  }
}

class Static {
  @ForceInline
  public static int m() {
    return 1;
  }
}

class Static2 {
  @ForceInline
  public static int m() {
    return 1;
  }
}
