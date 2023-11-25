// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MapIdProvider;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.MapIdTemplateProvider;
import com.android.tools.r8.utils.SourceFileTemplateProvider;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
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
    CompilationMode mode;
    public final int minApi;
    public final boolean forceProguardCompatibility;
    public final boolean includeDataResources;
    public final boolean multiDex;
    public final String mainDexList;
    public final MapIdProvider mapIdProvider;
    public final SourceFileProvider sourceFileProvider;
    public final String depsFileOutput;
    public final List<String> proguardConfig;
    public boolean printHelpAndExit;

    CompatProguardOptions(
        List<String> proguardConfig,
        String output,
        CompilationMode mode,
        int minApi,
        boolean multiDex,
        boolean forceProguardCompatibility,
        boolean includeDataResources,
        String mainDexList,
        MapIdProvider mapIdProvider,
        SourceFileProvider sourceFileProvider,
        String depsFileOutput,
        boolean printHelpAndExit) {
      this.output = output;
      this.mode = mode;
      this.minApi = minApi;
      this.forceProguardCompatibility = forceProguardCompatibility;
      this.includeDataResources = includeDataResources;
      this.multiDex = multiDex;
      this.mainDexList = mainDexList;
      this.proguardConfig = proguardConfig;
      this.mapIdProvider = mapIdProvider;
      this.sourceFileProvider = sourceFileProvider;
      this.depsFileOutput = depsFileOutput;
      this.printHelpAndExit = printHelpAndExit;
    }

    public static CompatProguardOptions parse(String[] args) {
      @SuppressWarnings("UnusedVariable")
      DiagnosticsHandler handler = new DiagnosticsHandler() {};
      String output = null;
      CompilationMode mode = null;
      int minApi = 1;
      boolean forceProguardCompatibility = false;
      boolean includeDataResources = true;
      boolean multiDex = false;
      String mainDexList = null;
      boolean printHelpAndExit = false;
      MapIdProvider mapIdProvider = null;
      SourceFileProvider sourceFileProvider = null;
      String depsFileOutput = null;

      ImmutableList.Builder<String> builder = ImmutableList.builder();
      if (args.length > 0) {
        StringBuilder currentLine = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
          String arg = args[i];
          if (arg.charAt(0) == '-') {
            if (arg.equals("-h") || arg.equals("--help")) {
              printHelpAndExit = true;
            } else if (arg.equals("--debug")) {
              if (mode == CompilationMode.RELEASE) {
                throw new CompilationError("Cannot compile in both --debug and --release mode.");
              }
              mode = CompilationMode.DEBUG;
            } else if (arg.equals("--release")) {
              if (mode == CompilationMode.DEBUG) {
                throw new CompilationError("Cannot compile in both --debug and --release mode.");
              }
              mode = CompilationMode.RELEASE;
            } else if (arg.equals("--min-api")) {
              minApi = Integer.valueOf(args[++i]);
            } else if (arg.equals("--force-proguard-compatibility")) {
              forceProguardCompatibility = true;
            } else if (arg.equals("--no-data-resources")) {
              includeDataResources = false;
            } else if (arg.equals("--output")) {
              output = args[++i];
            } else if (arg.equals("--multi-dex")) {
              multiDex = true;
            } else if (arg.equals("--main-dex-list")) {
              mainDexList = args[++i];
            } else if (arg.startsWith("--main-dex-list=")) {
              mainDexList = arg.substring("--main-dex-list=".length());
            } else if (arg.equals("--map-id-template")) {
              mapIdProvider = MapIdTemplateProvider.create(args[++i], handler);
            } else if (arg.equals("--source-file-template")) {
              sourceFileProvider = SourceFileTemplateProvider.create(args[++i], handler);
            } else if (arg.equals("--deps-file")) {
              depsFileOutput = args[++i];
            } else if (arg.equals("--core-library")
                || arg.equals("--minimal-main-dex")
                || arg.equals("--no-locals")) {
              // Ignore.
            } else if (arg.equals("-outjars")) {
              throw new CompilationError(
                  "Proguard argument -outjar is not supported. Use R8 compatible --output flag");
            } else {
              if (currentLine.length() > 0) {
                builder.add(currentLine.toString());
              }
              currentLine = new StringBuilder(arg);
            }
          } else {
            if (currentLine.length() > 0) {
              currentLine.append(' ');
            }
            currentLine.append(arg);
          }
        }
        if (currentLine.length() > 0) {
          builder.add(currentLine.toString());
        }
      }
      return new CompatProguardOptions(
          builder.build(),
          output,
          mode,
          minApi,
          multiDex,
          forceProguardCompatibility,
          includeDataResources,
          mainDexList,
          mapIdProvider,
          sourceFileProvider,
          depsFileOutput,
          printHelpAndExit);
    }

    public static void print() {
      System.out.println("-h/--help            : print this help message");
      System.out.println("--release            : compile without debugging information (default).");
      System.out.println("--debug              : compile with debugging information.");
      System.out.println("--min-api n          : specify the targeted min android api level");
      System.out.println("--main-dex-list list : specify main dex list for multi-dexing");
      System.out.println("--minimal-main-dex   : ignored (provided for compatibility)");
      System.out.println("--multi-dex          : ignored (provided for compatibility)");
      System.out.println("--no-locals          : ignored (provided for compatibility)");
      System.out.println("--core-library       : ignored (provided for compatibility)");
      System.out.println("--force-proguard-compatibility : Proguard compatibility mode");
      System.out.println("--no-data-resources  : ignore all data resources");
    }
  }

  private static void printVersion() {
    System.out.println("CompatProguard " + Version.getVersionString());
  }

  private static void printHelp() {
    printVersion();
    System.out.println();
    System.out.println("compatproguard [options] --output <dir> <proguard-config>*");
    System.out.println();
    System.out.println("Where options are:");
    CompatProguardOptions.print();
  }

  private static void run(String[] args) throws CompilationFailedException {
    // Run R8 passing all the options from the command line as a Proguard configuration.
    CompatProguardOptions options = CompatProguardOptions.parse(args);
    if (options.printHelpAndExit || options.output == null) {
      System.out.println();
      printHelp();
      return;
    }
    CompatProguardCommandBuilder builder =
        new CompatProguardCommandBuilder(options.forceProguardCompatibility);
    builder
        .setOutput(Paths.get(options.output), OutputMode.DexIndexed, options.includeDataResources)
        .addProguardConfiguration(options.proguardConfig, CommandLineOrigin.INSTANCE)
        .setMinApiLevel(options.minApi)
        .setMapIdProvider(options.mapIdProvider)
        .setSourceFileProvider(options.sourceFileProvider);
    if (options.mode != null) {
      builder.setMode(options.mode);
    }
    if (options.mainDexList != null) {
      builder.addMainDexListFiles(Paths.get(options.mainDexList));
    }
    if (options.depsFileOutput != null) {
      Path target = Paths.get(options.output);
      if (!FileUtils.isArchive(target)) {
        target = target.resolve("classes.dex");
      }
      builder.setInputDependencyGraphConsumer(new DepsFileWriter(target, options.depsFileOutput));
    }
    R8.run(builder.build());
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
