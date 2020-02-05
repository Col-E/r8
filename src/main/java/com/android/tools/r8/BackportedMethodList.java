// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;

@Keep
public class BackportedMethodList {

  static final String USAGE_MESSAGE =
      StringUtils.joinLines(
          "Usage: BackportedMethodList [options]",
          " Options are:",
          "  --output <file>         # Output result in <file>.",
          "  --min-api <number>      # Minimum Android API level",
          "  --version               # Print the version of BackportedMethodList.",
          "  --help                  # Print this message.");

  private static String formatMethod(DexMethod method) {
    return DescriptorUtils.getClassBinaryNameFromDescriptor(method.holder.descriptor.toString())
        + '#'
        + method.name
        + method.proto.toDescriptorString();
  }

  public static void run(BackportedMethodListCommand command) {
    if (command.isPrintHelp()) {
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("BackportedMethodList " + Version.getVersionString());
      return;
    }
    BackportedMethodRewriter.generateListOfBackportedMethods(
            AndroidApiLevel.getAndroidApiLevel(command.getMinApiLevel()))
        .stream()
        .map(BackportedMethodList::formatMethod)
        .sorted()
        .forEach(
            formattedMethod ->
                command
                    .getBackportedMethodListConsumer()
                    .accept(formattedMethod, command.getReporter()));
    command.getBackportedMethodListConsumer().finished(command.getReporter());
  }

  public static void main(String[] args) {
    run(BackportedMethodListCommand.parse(args).build());
  }
}
