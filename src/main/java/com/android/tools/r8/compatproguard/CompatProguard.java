// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Proguard + dx compatibility interface for r8.
 *
 * This should become a mostly drop-in replacement for uses of Proguard followed by dx for
 * use with the Android Platform build.
 *
 * It accepts all Proguard flags supported by r8, except -outjars.
 *
 * It accepts a few dx flags which are known to be used in the Android Platform build.
 *
 * The flag -outjars does not make sense as r8 (like Proguard + dx) produces Dex output.
 * For output use --output as for R8 proper.
 */
public class CompatProguard {
  public static class CompatProguardOptions {
    public final String output;
    public final int minApi;
    public final boolean forceProguardCompatibility;
    public final boolean multiDex;
    public final List<String> proguardConfig;

    CompatProguardOptions(List<String> proguardConfig, String output, int minApi,
        boolean multiDex, boolean forceProguardCompatibility) {
      this.output = output;
      this.minApi = minApi;
      this.forceProguardCompatibility = forceProguardCompatibility;
      this.multiDex = multiDex;
      this.proguardConfig = proguardConfig;
    }

    public static CompatProguardOptions parse(String[] args) throws CompilationException {
      String output = null;
      int minApi = 1;
      boolean forceProguardCompatibility = false;
      boolean multiDex = false;
      boolean coreLibrary = false;

      ImmutableList.Builder<String> builder = ImmutableList.builder();
      if (args.length > 0) {
        StringBuilder currentLine = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) {
          String arg = args[i];
          if (arg.charAt(0) == '-') {
            if (arg.equals("--min-api")) {
              minApi = Integer.valueOf(args[++i]);
            } else if (arg.equals("--force-proguard-compatibility")) {
              forceProguardCompatibility = true;
            } else if (arg.equals("--output")) {
              output = args[++i];
            } else if (arg.equals("--multi-dex")) {
              multiDex = true;
            } else if (arg.equals("--core-library")) {
              coreLibrary = true;
            } else if (arg.equals("-outjars")) {
              throw new CompilationException(
                  "Proguard argument -outjar is not supported. Use R8 compatible --output flag");
            } else {
              builder.add(currentLine.toString());
              currentLine = new StringBuilder(arg);
            }
          } else {
            currentLine.append(' ').append(arg);
          }
        }
        builder.add(currentLine.toString());
      }
      return new CompatProguardOptions(builder.build(), output, minApi, multiDex,
          forceProguardCompatibility);
    }
  }

  private static void run(String[] args) throws IOException, CompilationException {
    if (args.length == 0) {
      Version.printToolVersion("CompatProguard");
      return;
    }
    System.out.println("CompatProguard " + String.join(" ", args));
    // Run R8 passing all the options from the command line as a Proguard configuration.
    CompatProguardOptions options = CompatProguardOptions.parse(args);
    R8Command.Builder builder =
        new CompatProguardCommandBuilder(options.forceProguardCompatibility);
    builder.setOutputPath(Paths.get(options.output))
        .addProguardConfiguration(options.proguardConfig)
        .setMinApiLevel(options.minApi);
    AndroidApp result = R8.runInternal(builder.build());

    if (!options.multiDex) {
      if (result.getDexProgramResources().size() > 1) {
        throw new CompilationError(
            "Compilation result could not fit into a single dex file. "
                + "Reduce the input-program size or run with --multi-dex enabled");
      }
    }
  }

  public static void main(String[] args) throws IOException {
    try {
      run(args);
    } catch (CompilationException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
