// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;


import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import java.util.Map;

public class DexFileMergerHelper {

  private final Map<String, Integer> inputOrdering;

  private DexFileMergerHelper(Map<String, Integer> inputOrdering) {
    this.inputOrdering = inputOrdering;
  }

  private DexProgramClass keepFirstProgramClassConflictResolver(
      DexProgramClass a, DexProgramClass b) {
    String aPath = a.getOrigin().parent().part();
    String bPath = b.getOrigin().parent().part();
    Integer aIndex = inputOrdering.get(aPath);
    Integer bIndex = inputOrdering.get(bPath);
    if (aIndex == null || bIndex == null) {
      StringBuilder builder = new StringBuilder();
      builder.append("Class parent paths not found among input paths: ");
      if (aIndex == null) {
        builder.append(aPath);
      }
      if (bIndex == null) {
        if (aIndex == null) {
          builder.append(", ");
        }
        builder.append(bPath);
      }
      throw new RuntimeException(builder.toString());
    }
    return aIndex <= bIndex ? a.get() : b.get();
  }

  // NOTE: Don't change this signature! Reflectively accessed from bazel DexFileMerger.
  public static void run(
      D8Command command, Boolean minimalMainDex, Map<String, Integer> inputOrdering)
      throws CompilationFailedException {
    InternalOptions options = command.getInternalOptions();

    // TODO(b/241351268): Don't compile in intermediate mode as the output is a final "shard".
    options.intermediate = true;

    // TODO(b/241063980): Move this to D8Command.Builder.setDisableDesugaring(true) in bazel.
    options.desugarState = DesugarState.OFF;

    // TODO(b/241063980): Is this configuration needed?
    options.enableMainDexListCheck = false;

    // TODO(b/241063980): Is this configuration needed?
    options.minimalMainDex = minimalMainDex;

    // TODO(b/241063980): Add API to configure this in D8Command.Builder.
    options.programClassConflictResolver =
        new DexFileMergerHelper(inputOrdering)::keepFirstProgramClassConflictResolver;

    D8.runForTesting(command.getInputApp(), options);
  }
}
