// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexmerger;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

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

  private static class DexFileWriter implements DexIndexedConsumer {

    final Path outputFile;
    byte[] data = null;

    public DexFileWriter(Path outputFile) {
      this.outputFile = outputFile;
    }

    @Override
    public synchronized void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (fileIndex > 0) {
        throw new ResultTooBigForSingleDexException();
      }
      this.data = data;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (data == null) {
        throw new RuntimeException("Failed to produce the output file.");
      }
      try {
        Files.write(
            outputFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public static void run(String[] args) throws CompilationFailedException, IOException {
    ProgramConsumer consumer = new DexFileWriter(Paths.get(args[0]));
    D8Command.Builder builder = D8Command.builder().setProgramConsumer(consumer);
    for (int i = 1; i < args.length; ++i) {
      builder.addProgramFiles(Paths.get(args[i]));
    }
    D8.run(builder.build());
  }

  private static void printUsage() {
    System.out.println("Usage: DexMerger <out.dex> <a.dex> <b.dex> ...");
    System.out.println();
    System.out.println("If a class is defined in multiple dex files, it is an error.");
  }
}
