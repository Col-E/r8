// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

/** Immutable command structure for an invocation of the {@link ExtractMarker} tool. */
@KeepForApi
public class ExtractMarkerCommand {

  /** Builder for constructing a {@link ExtractMarkerCommand}. */
  @KeepForApi
  public static class Builder {
    private boolean printHelp = false;
    private final List<Path> programFiles = new ArrayList<>();
    private final List<Pair<Origin, byte[]>> dexData = new ArrayList<>();
    private final List<Pair<Origin, byte[]>> cfData = new ArrayList<>();
    private MarkerInfoConsumer consumer;
    private DiagnosticsHandler handler;

    Builder(DiagnosticsHandler handler) {
      this.handler = handler;
    }

    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public boolean isPrintHelp() {
      return printHelp;
    }

    /**
     * Add program files to extract marker information from.
     *
     * <p>All program files supported by the input and output of D8/R8 can be passed here.
     */
    public Builder addProgramFiles(Path... programFiles) {
      return addProgramFiles(Arrays.asList(programFiles));
    }

    /**
     * Add program files to extract marker information from.
     *
     * <p>Each file added here will result in exactly one callback to {@link
     * MarkerInfoConsumer#acceptMarkerInfo}.
     *
     * <p>All program files supported by the input and output of D8/R8 can be passed here.
     */
    public Builder addProgramFiles(Collection<Path> programFiles) {
      this.programFiles.addAll(programFiles);
      return this;
    }

    /**
     * Add dex encoded bytes to extract marker information from.
     *
     * <p>Each data & origin pair added here will result in exactly one callback to {@link
     * MarkerInfoConsumer#acceptMarkerInfo}.
     */
    public Builder addDexProgramData(byte[] data, Origin origin) {
      dexData.add(new Pair<>(origin, data));
      return this;
    }

    /**
     * Add classfile encoded bytes to extract marker information from.
     *
     * <p>Each data & origin pair added here will result in exactly one callback to {@link
     * MarkerInfoConsumer#acceptMarkerInfo}.
     */
    public Builder addClassProgramData(byte[] data, Origin origin) {
      cfData.add(new Pair<>(origin, data));
      return this;
    }

    /** Set the callback to obtain the collected marker information. */
    public Builder setMarkerInfoConsumer(MarkerInfoConsumer consumer) {
      this.consumer = consumer;
      return this;
    }

    public ExtractMarkerCommand build() {
      // If printing versions ignore everything else.
      if (isPrintHelp()) {
        return new ExtractMarkerCommand(isPrintHelp());
      }
      return new ExtractMarkerCommand(handler, consumer, programFiles, dexData, cfData);
    }
  }

  static final String USAGE_MESSAGE =
      String.join(
          "\n",
          ImmutableList.of(
              "Usage: extractmarker [options] <input-files>",
              " where <input-files> are D8 supported input/output files and options are:",
              "  --help                  # Print this message."));

  public static Builder builder() {
    return builder(new DiagnosticsHandler() {});
  }

  public static Builder builder(DiagnosticsHandler handler) {
    return new Builder(handler);
  }

  public static Builder parse(String[] args) {
    Builder builder = builder();
    parse(args, builder);
    return builder;
  }

  private static void parse(String[] args, Builder builder) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i].trim();
      if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else {
        if (arg.startsWith("--")) {
          throw new CompilationError("Unknown option: " + arg);
        }
        builder.addProgramFiles(Paths.get(arg));
      }
    }
  }

  private final boolean printHelp;
  private final DiagnosticsHandler handler;
  private final MarkerInfoConsumer consumer;
  private final List<Path> programFiles;
  private final List<Pair<Origin, byte[]>> dexData;
  private final List<Pair<Origin, byte[]>> cfData;

  private ExtractMarkerCommand(
      DiagnosticsHandler handler,
      MarkerInfoConsumer consumer,
      List<Path> programFiles,
      List<Pair<Origin, byte[]>> dexData,
      List<Pair<Origin, byte[]>> cfData) {
    this.printHelp = false;
    this.handler = handler;
    this.consumer = consumer;
    this.programFiles = programFiles;
    this.dexData = dexData;
    this.cfData = cfData;
  }

  private ExtractMarkerCommand(boolean printHelp) {
    this.printHelp = printHelp;
    handler = null;
    consumer = null;
    programFiles = null;
    dexData = null;
    cfData = null;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public MarkerInfoConsumer getMarkerInfoConsumer() {
    return consumer;
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return handler;
  }

  public void forEachEntry(
      BiConsumer<Path, Origin> onProgramFile,
      BiConsumer<byte[], Origin> onDexData,
      BiConsumer<byte[], Origin> onCfData) {
    programFiles.forEach(path -> onProgramFile.accept(path, new PathOrigin(path)));
    dexData.forEach(pair -> onDexData.accept(pair.getSecond(), pair.getFirst()));
    cfData.forEach(pair -> onCfData.accept(pair.getSecond(), pair.getFirst()));
  }
}
