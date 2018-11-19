// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.invokespecial.Main;
import com.android.tools.r8.graph.invokespecial.TestClass;
import com.android.tools.r8.graph.invokespecial.TestClassDump;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InvokeSpecialTest extends AsmTestBase {

  @ClassRule
  public static TemporaryFolder tempFolder = ToolHelper.getTemporaryFolderForTest();

  private static Path inputJar;

  @BeforeClass
  public static void setup() throws Exception {
    inputJar = tempFolder.getRoot().toPath().resolve("input.jar");
    ClassFileConsumer consumer = new ClassFileConsumer.ArchiveConsumer(inputJar);
    consumer.accept(
        ByteDataView.of(ToolHelper.getClassAsBytes(Main.class)),
        javaTypeToDescriptor(Main.class.getTypeName()),
        null);
    consumer.accept(
        ByteDataView.of(TestClassDump.dump()),
        javaTypeToDescriptor(TestClass.class.getTypeName()),
        null);
    consumer.finished(null);
  }

  @Test
  public void testExpectedBehavior() throws Exception {
    testForJvm()
        .addProgramClasses(Main.class, TestClass.class)
        .run(Main.class)
        .assertSuccessWithOutput(StringUtils.lines("true", "false"));
  }

  @Test
  public void testD8Behavior() throws Exception {
    // TODO(b/110175213): Should succeed with output "true\nfalse\n".
    testForD8()
        .addProgramFiles(inputJar)
        .run(Main.class)
        .assertFailureWithErrorThatMatches(containsString(getExpectedOutput()));
  }

  @Test
  public void testDXBehavior() throws Exception {
    testForDX()
        .addProgramFiles(inputJar)
        .run(Main.class)
        .assertFailureWithErrorThatMatches(containsString(getExpectedOutput()));
  }

  private static String getExpectedOutput() {
    if (ToolHelper.getDexVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)) {
      return "VFY: unable to resolve direct method";
    }
    return "was expected to be of type direct but instead was found to be of type virtual";
  }
}
