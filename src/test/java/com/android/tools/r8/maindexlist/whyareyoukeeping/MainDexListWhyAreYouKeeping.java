// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.whyareyoukeeping;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import org.junit.Test;

class HelloWorldMain {
  public static void main(String[] args) {
    System.out.println(new MainDexClass());
  }
}

class MainDexClass {}

class NonMainDexClass {}

public class MainDexListWhyAreYouKeeping extends TestBase {
  public String runTest(String whyAreYouKeepingRule) throws Exception {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(
                readClasses(HelloWorldMain.class, MainDexClass.class, NonMainDexClass.class))
            .addMainDexRules(
                ImmutableList.of(keepMainProguardConfiguration(HelloWorldMain.class)),
                Origin.unknown())
            .addMainDexRules(ImmutableList.of(whyAreYouKeepingRule), Origin.unknown())
            .setOutput(temp.getRoot().toPath(), OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)
            .build();
    PrintStream stdout = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos));
    ToolHelper.runR8(command);
    String output = new String(baos.toByteArray(), Charset.defaultCharset());
    System.setOut(stdout);
    return output;
  }

  @Test
  public void testMainDexClassWhyAreYouKeeping() throws Exception {
    String output = runTest("-whyareyoukeeping class " + MainDexClass.class.getCanonicalName());
    assertThat(
        output, containsString("com.android.tools.r8.maindexlist.whyareyoukeeping.MainDexClass"));
    assertThat(output, containsString("- is live because referenced in keep rule:"));
  }

  @Test
  public void testNonMainDexWhyAreYouKeeping() throws Exception {
    String output = runTest("-whyareyoukeeping class " + NonMainDexClass.class.getCanonicalName());
    assertTrue(output.isEmpty());
  }
}
