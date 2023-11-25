// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.bisect.Bisect;
import com.android.tools.r8.cf.CfVerifierTool;
import com.android.tools.r8.compatproguard.CompatProguard;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.DesugaredMethodsList;
import com.android.tools.r8.relocator.RelocatorCommandLine;
import com.android.tools.r8.tracereferences.TraceReferences;
import java.util.Arrays;

/**
 * Common entry point to everything in the R8 project.
 *
 * <p>This class is used as the main class in {@code r8.jar}. It checks the first command-line
 * argument to find the tool to run, or runs {@link R8} if the first argument is not a recognized
 * tool name.
 *
 * <p>The set of tools recognized by this class is defined by a switch statement in {@link
 * SwissArmyKnife#main(String[])}.
 */
public class SwissArmyKnife {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      runDefault(args);
      return;
    }
    switch (args[0]) {
      case "bisect":
        Bisect.main(shift(args));
        break;
      case "compatproguard":
        CompatProguard.main(shift(args));
        break;
      case "d8":
        D8.main(shift(args));
        break;
      case "dexsegments":
        DexSegments.main(shift(args));
        break;
      case "disasm":
        Disassemble.main(shift(args));
        break;
      case "extractmarker":
        ExtractMarker.main(shift(args));
        break;
      case "jardiff":
        JarDiff.main(shift(args));
        break;
      case "maindex":
        GenerateMainDexList.main(shift(args));
        break;
      case "r8":
        R8.main(shift(args));
        break;
      case "l8":
        L8.main(shift(args));
        break;
      case "backportedmethods":
        BackportedMethodList.main(shift(args));
        break;
      case "desugaredmethods":
        DesugaredMethodsList.main(shift(args));
        break;
      case "relocator":
        RelocatorCommandLine.main(shift(args));
        break;
      case "tracereferences":
        TraceReferences.main(shift(args));
        break;
      case "verify":
        CfVerifierTool.main(shift(args));
        break;
      default:
        runDefault(args);
        break;
    }
  }

  private static void runDefault(String[] args) {
    R8.main(args);
  }

  private static String[] shift(String[] args) {
    return Arrays.copyOfRange(args, 1, args.length);
  }
}
