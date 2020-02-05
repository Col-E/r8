// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BackportedMethodListTest {

  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private static class ListStringConsumer implements StringConsumer {
    List<String> strings = new ArrayList<>();
    boolean finished = false;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      strings.add(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      finished = true;
    }
  }

  private void checkContent(int apiLevel, List<String> backports) {
    // Java 8 methods added at various API levels.
    assertEquals(
        apiLevel < AndroidApiLevel.K.getLevel(), backports.contains("java/lang/Byte#compare(BB)I"));
    assertEquals(
        apiLevel < AndroidApiLevel.N.getLevel(),
        backports.contains("java/lang/Integer#hashCode(I)I"));
    assertEquals(
        apiLevel < AndroidApiLevel.O.getLevel(),
        backports.contains("java/lang/Short#toUnsignedLong(S)J"));

    // Java 9, 10 and 11 Optional methods.
    assertEquals(
        apiLevel >= AndroidApiLevel.N.getLevel(),
        backports.contains(
            "java/util/Optional#or(Ljava/util/function/Supplier;)Ljava/util/Optional;"));
    assertEquals(
        apiLevel >= AndroidApiLevel.N.getLevel(),
        backports.contains("java/util/OptionalInt#orElseThrow()I"));
    assertEquals(
        apiLevel >= AndroidApiLevel.N.getLevel(),
        backports.contains("java/util/OptionalLong#isEmpty()Z"));

    // Java 9, 10 and 11 methods.
    assertTrue(backports.contains("java/lang/StrictMath#multiplyExact(JI)J"));
    assertTrue(backports.contains("java/util/List#copyOf(Ljava/util/Collection;)Ljava/util/List;"));
    assertTrue(backports.contains("java/lang/Character#toString(I)Ljava/lang/String;"));
  }

  @Test
  public void testConsumer() throws Exception {
    for (int apiLevel = 1; apiLevel < AndroidApiLevel.LATEST.getLevel(); apiLevel++) {
      ListStringConsumer consumer = new ListStringConsumer();
      BackportedMethodList.run(
          BackportedMethodListCommand.builder()
              .setMinApiLevel(apiLevel)
              .setConsumer(consumer)
              .build());

      assertTrue(consumer.finished);

      checkContent(apiLevel, consumer.strings);
    }
  }

  @Test
  public void testFile() throws Exception {
    for (int apiLevel = 1; apiLevel < AndroidApiLevel.LATEST.getLevel(); apiLevel++) {
      Path output = temp.newFile().toPath();
      BackportedMethodList.run(
          BackportedMethodListCommand.builder()
              .setMinApiLevel(apiLevel)
              .setOutputPath(output)
              .build());

      checkContent(apiLevel, Files.readAllLines(output));
    }
  }
}
