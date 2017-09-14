// Copyright (c) 2017, the Rex project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ExtractMarker {
  private static class Command extends BaseCommand {

    public static class Builder
        extends BaseCommand.Builder<ExtractMarker.Command, ExtractMarker.Command.Builder> {

      private boolean verbose;

      @Override
      ExtractMarker.Command.Builder self() {
        return this;
      }

      public Builder setVerbose(boolean verbose) {
        this.verbose = verbose;
        return self();
      }

      @Override
      public ExtractMarker.Command build() throws CompilationException, IOException {
        // If printing versions ignore everything else.
        if (isPrintHelp()) {
          return new ExtractMarker.Command(isPrintHelp());
        }
        validate();
        return new ExtractMarker.Command(getAppBuilder().build(), verbose, programFiles);
      }
    }

    static final String USAGE_MESSAGE = String.join("\n", ImmutableList.of(
        "Usage: extractmarker [options] <input-files>",
        " where <input-files> are dex or vdex files",
        "  --version               # Print the version of r8.",
        "  --verbose               # More verbose output.",
        "  --help                  # Print this message."));

    public static ExtractMarker.Command.Builder builder() {
      // Allow vdex files for the extract marker tool.
      return new ExtractMarker.Command.Builder().setVdexAllowed();
    }

    public static ExtractMarker.Command.Builder parse(String[] args)
        throws CompilationException, IOException {
      ExtractMarker.Command.Builder builder = builder();
      parse(args, builder);
      return builder;
    }

    private static void parse(String[] args, ExtractMarker.Command.Builder builder)
        throws CompilationException, IOException {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i].trim();
        if (arg.length() == 0) {
          continue;
        } else if (arg.equals("--verbose")) {
          builder.setVerbose(true);
        } else if (arg.equals("--help")) {
          builder.setPrintHelp(true);
        } else {
          if (arg.startsWith("--")) {
            throw new CompilationException("Unknown option: " + arg);
          }
          builder.addProgramFiles(Paths.get(arg));
        }
      }
    }

    private final List<Path> programFiles;
    private final boolean verbose;

    private Command(AndroidApp inputApp, boolean verbose, List<Path> programFiles) {
      super(inputApp);
      this.programFiles = programFiles;
      this.verbose = verbose;
    }

    private Command(boolean printHelp) {
      super(printHelp, false);
      this.verbose = false;
      programFiles = ImmutableList.of();
    }

    public List<Path> getProgramFiles() {
      return programFiles;
    }

    public boolean getVerbose() {
      return verbose;
    }

    @Override
    InternalOptions getInternalOptions() {
      return new InternalOptions();
    }
  }

  public static void main(String[] args)
      throws IOException, CompilationException, ExecutionException {
    ExtractMarker.Command.Builder builder = ExtractMarker.Command.parse(args);
    ExtractMarker.Command command = builder.build();
    if (command.isPrintHelp()) {
      System.out.println(ExtractMarker.Command.USAGE_MESSAGE);
      return;
    }

    try {
      AndroidApp app = command.getInputApp();
      InternalOptions options = new InternalOptions();
      // Dex code is not needed for getting the marker. VDex files typically contains quickened byte
      // codes which cannot be read, and we want to get the marker from vdex files as well.
      options.skipReadingDexCode = true;
      DexApplication dexApp = new ApplicationReader(app, options, new Timing("ExtractMarker"))
          .read();
      Marker readMarker = dexApp.dexItemFactory.extractMarker();
      if (command.getVerbose()) {
        for (int i = 0; i < command.getProgramFiles().size(); i++) {
          if (i != 0) {
            System.out.print(", ");
          }
          System.out.print(command.getProgramFiles().get(i));
        }
        System.out.print(": ");
      }
      if (readMarker == null) {
        System.out.println("D8/R8 marker not found.");
        System.exit(1);
      } else {
        System.out.println(readMarker.toString());
      }
    } catch (CompilationError e) {
      System.out.println("Failed to read dex file: '" + e.getMessage() + "'");
      System.exit(0);
    }
  }
}
