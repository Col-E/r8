// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.whyareyoukeeping;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {

}

@RunWith(Parameterized.class)
public class WhyAreYouKeepingTest extends TestBase {

  public static final String expected =
      StringUtils.joinLines(
          "com.android.tools.r8.shaking.whyareyoukeeping.A",
          "|- is referenced in keep rule:",
          "|  -keep class com.android.tools.r8.shaking.whyareyoukeeping.A { *; }",
          "");

  @Parameters(name = "{0}")
  public static Backend[] parameters() {
    return Backend.values();
  }

  public final Backend backend;

  public WhyAreYouKeepingTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testWhyAreYouKeepingViaProguardConfig() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    testForR8(backend)
        .addProgramClasses(A.class)
        .addKeepClassAndMembersRules(A.class)
        .addKeepRules("-whyareyoukeeping class " + A.class.getTypeName())
        // Clear the default library and ignore missing classes to avoid processing the library.
        .addLibraryFiles()
        .addOptionsModification(o -> o.ignoreMissingClasses = true)
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .redirectStdOut(new PrintStream(baos))
        .compile();
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertEquals(expected, output);
  }

  @Test
  public void testWhyAreYouKeepingViaConsumer() throws Exception {
    WhyAreYouKeepingConsumer graphConsumer = new WhyAreYouKeepingConsumer(null);
    testForR8(backend)
        .addProgramClasses(A.class)
        .addKeepClassAndMembersRules(A.class)
        // Clear the default library and ignore missing classes to avoid processing the library.
        .addLibraryFiles()
        .addOptionsModification(o -> o.ignoreMissingClasses = true)
        .setKeptGraphConsumer(graphConsumer)
        .compile();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String descriptor = DescriptorUtils.javaTypeToDescriptor(A.class.getTypeName());
    graphConsumer.printWhyAreYouKeeping(descriptor, new PrintStream(baos));
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertEquals(expected, output);
  }
}
