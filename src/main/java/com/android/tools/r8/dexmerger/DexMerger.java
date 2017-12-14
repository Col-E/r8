// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexmerger;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.D8Output;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class DexMerger {
  static class ResultTooBigForSingleDexException extends RuntimeException {
    ResultTooBigForSingleDexException() {
      super("Result too big for a single dex file and multidex is not supported.");
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      printUsage();
      System.exit(1);
    }
    try {
      run(args);
    } catch (ResultTooBigForSingleDexException | CompilationFailedException | IOException e) {
      System.err.println("Merge failed: " + e.getMessage());
      System.exit(1);
    }
  }

  public static void run(String[] args) throws CompilationFailedException, IOException {
    Path tempDirectory = Files.createTempDirectory(null);
    try {
      D8Command.Builder builder = D8Command.builder().setOutputPath(tempDirectory);
      for (int i = 1; i < args.length; ++i) {
        builder.addProgramFiles(Paths.get(args[i]));
      }

      D8Output output = D8.run(builder.build());

      if (output.getDexResources().size() == 0) {
        throw new RuntimeException("Failed to produce the output file.");
      }

      if (output.getDexResources().size() != 1) {
        throw new ResultTooBigForSingleDexException();
      }

      Files.copy(
          tempDirectory.resolve("classes.dex"),
          Paths.get(args[0]),
          StandardCopyOption.REPLACE_EXISTING);
    } finally {
      MoreFiles.deleteRecursively(tempDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }

  private static void printUsage() {
    System.out.println("Usage: DexMerger <out.dex> <a.dex> <b.dex> ...");
    System.out.println();
    System.out.println("If a class is defined in multiple dex files, it is an error.");
  }
}
