// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bisect;

import com.android.tools.r8.errors.CompilationError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BisectOptions {

  private static final String HELP_FLAG = "--help";
  public static final String BUILD_GOOD_FLAG = "--good";
  public static final String BUILD_BAD_FLAG = "--bad";
  public static final String RESULT_GOOD_FLAG = "--result-good";
  public static final String RESULT_BAD_FLAG = "--result-bad";
  public static final String STATE_FLAG = "--state";
  public static final String OUTPUT_FLAG = "--output";
  public static final String COMMAND_FLAG = "--command";

  public final Path goodBuild;
  public final Path badBuild;
  public final Path stateFile;
  public final Path command;
  public final Path output;
  public final Result result;

  public enum Result {
    UNKNOWN,
    GOOD,
    BAD
  }

  private BisectOptions(
      Path goodBuild, Path badBuild, Path stateFile, Path command, Path output, Result result) {
    this.goodBuild = goodBuild;
    this.badBuild = badBuild;
    this.stateFile = stateFile;
    this.command = command;
    this.output = output;
    this.result = result;
  }

  public static BisectOptions parse(String[] args) throws IOException {
    Path badBuild = null;
    Path goodBuild = null;
    Path stateFile = null;
    Path command = null;
    Path output = null;
    Result result = Result.UNKNOWN;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      if (arg.equals(HELP_FLAG)) {
        printHelp();
        return null;
      } else if (arg.equals(BUILD_BAD_FLAG)) {
        i = nextArg(i, args, BUILD_BAD_FLAG);
        badBuild = Paths.get(args[i]);
      } else if (arg.equals(BUILD_GOOD_FLAG)) {
        i = nextArg(i, args, BUILD_GOOD_FLAG);
        goodBuild = Paths.get(args[i]);
      } else if (arg.equals(COMMAND_FLAG)) {
        i = nextArg(i, args, COMMAND_FLAG);
        command = Paths.get(args[i]);
      } else if (arg.equals(OUTPUT_FLAG)) {
        i = nextArg(i, args, OUTPUT_FLAG);
        output = Paths.get(args[i]);
      } else if (arg.equals(RESULT_BAD_FLAG)) {
        result = checkSingleResult(result, Result.BAD);
      } else if (arg.equals(RESULT_GOOD_FLAG)) {
        result = checkSingleResult(result, Result.GOOD);
      } else if (arg.equals(STATE_FLAG)) {
        i = nextArg(i, args, STATE_FLAG);
        stateFile = Paths.get(args[i]);
      }
    }
    exists(require(badBuild, BUILD_BAD_FLAG), BUILD_BAD_FLAG);
    exists(require(goodBuild, BUILD_GOOD_FLAG), BUILD_GOOD_FLAG);
    if (stateFile != null) {
      exists(stateFile, STATE_FLAG);
    }
    if (command != null) {
      exists(command, COMMAND_FLAG);
    }
    if (output != null) {
      directoryExists(output, OUTPUT_FLAG);
    }

    return new BisectOptions(goodBuild, badBuild, stateFile, command, output, result);
  }

  private static int nextArg(int index, String[] args, String flag) {
    if (args.length == index + 1) {
      throw new CompilationError("Missing argument for: " + flag);
    }
    return index + 1;
  }

  private static Path require(Path value, String flag) {
    if (value == null) {
      throw new CompilationError("Missing required option: " + flag);
    }
    return value;
  }

  private static Path exists(Path path, String flag) {
    if (Files.exists(path)) {
      return path;
    }
    throw new CompilationError("File " + flag + ": " + path + " does not exist");
  }

  private static Path directoryExists(Path path, String flag) {
    if (Files.exists(path) && Files.isDirectory(path)) {
      return path;
    }
    throw new CompilationError("File " + flag + ": " + path + " is not a valid directory");
  }

  private static Result checkSingleResult(Result current, Result result) {
    if (current != Result.UNKNOWN) {
      throw new CompilationError(
          "Cannot specify " + RESULT_GOOD_FLAG + " and " + RESULT_BAD_FLAG + " simultaneously");
    }
    return result;
  }

  public static void printHelp() throws IOException {
    System.out.println("--bad <apk>       Known bad APK.");
    System.out.println("--command <file>  Command to run after each bisection.");
    System.out.println("--good <apk>      Known good APK.");
    System.out.println("--help");
    System.out.println("--output <dir>    Output directory.");
    System.out.println(
        "--result-bad      Bisect again assuming previous run was\n" + "        bad.");
    System.out.println(
        "--result-good     Bisect again assuming previous run was\n" + "        good.");
    System.out.println("--state <file>    Bisection state.");
  }
}
