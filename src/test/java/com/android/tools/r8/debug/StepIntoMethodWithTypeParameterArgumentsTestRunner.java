// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;

/** Tests debugging behavior with regards to exception handling */
public class StepIntoMethodWithTypeParameterArgumentsTestRunner extends DebugTestBase {

  private static final Class CLASS = StepIntoMethodWithTypeParameterArgumentsTest.class;
  private static final String NAME = CLASS.getCanonicalName();
  private static final String DESC = DescriptorUtils.javaTypeToDescriptor(NAME);
  private static final String FILE = CLASS.getSimpleName() + ".java";

  @Test
  public void testCf() throws Throwable {
    byte[] bytes = StepIntoMethodWithTypeParameterArgumentsTestDump.dump(true);
    assertEquals(NAME, extractClassName(bytes));
    // Java jumps to first instruction of the catch handler, matching the source code.
    Path jar = temp.getRoot().toPath().resolve("test.jar");
    ArchiveConsumer archiveConsumer = new ArchiveConsumer(jar);
    archiveConsumer.accept(ByteDataView.of(bytes), DESC, null);
    archiveConsumer.finished(null);
    run(new CfDebugTestConfig().addPaths(jar));
  }

  @Test
  public void testD8() throws Throwable {
    Path out = temp.getRoot().toPath().resolve("out.jar");
    D8.run(
        D8Command.builder()
            .addClassProgramData(
                StepIntoMethodWithTypeParameterArgumentsTestDump.dump(true), Origin.unknown())
            .setOutput(out, OutputMode.DexIndexed)
            .build());
    run(new DexDebugTestConfig().addPaths(out));
  }

  private void run(DebugTestConfig config) throws Throwable {
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "main"),
        run(),
        checkLine(FILE, 18), // First line in main.
        stepInto(),
        checkLine(FILE, -1), // First line in foo is undefined due to ASM dump change.
        checkLocal("strings"),
        checkNoLocal("objects"),
        stepOver(),
        // Step will skip line 14 and hit 15 on JVM but will (correctly?) hit 14 on Art.
        subcommands(
            config instanceof CfDebugTestConfig
                ? ImmutableList.of()
                : ImmutableList.of(checkLine(FILE, 14), stepOver())),
        checkLine(FILE, 15),
        checkLocal("strings"),
        checkLocal("objects"),
        run());
  }
}
