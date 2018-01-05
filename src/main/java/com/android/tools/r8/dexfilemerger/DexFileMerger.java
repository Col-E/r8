// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFileMergerHelper;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

public class DexFileMerger {
  /** File name prefix of a {@code .dex} file automatically loaded in an archive. */
  private static final String DEX_PREFIX = "classes";

  private static final String DEFAULT_OUTPUT_ARCHIVE_FILENAME = "classes.dex.jar";

  private static final boolean PRINT_ARGS = false;

  /** Strategies for outputting multiple {@code .dex} files supported by {@link DexFileMerger}. */
  private enum MultidexStrategy {
    /** Create exactly one .dex file. The operation will fail if .dex limits are exceeded. */
    OFF,
    /** Create exactly one &lt;prefixN&gt;.dex file with N taken from the (single) input archive. */
    GIVEN_SHARD,
    /**
     * Assemble .dex files similar to {@link com.android.dx.command.dexer.Main dx}, with all but one
     * file as large as possible.
     */
    MINIMAL,
    /**
     * Allow some leeway and sometimes use additional .dex files to speed up processing. This option
     * exists to give flexibility but it often (or always) may be identical to {@link #MINIMAL}.
     */
    BEST_EFFORT;

    public boolean isMultidexAllowed() {
      switch (this) {
        case OFF:
        case GIVEN_SHARD:
          return false;
        case MINIMAL:
        case BEST_EFFORT:
          return true;
      }
      throw new AssertionError("Unknown: " + this);
    }
  }

  private static class Options {
    List<String> inputArchives = new ArrayList<>();
    String outputArchive = DEFAULT_OUTPUT_ARCHIVE_FILENAME;
    MultidexStrategy multidexMode = MultidexStrategy.OFF;
    String mainDexListFile = null;
    boolean minimalMainDex = false;
    boolean verbose = false;
    String dexPrefix = DEX_PREFIX;
  }

  private static class ParseContext {
    private String[] args;
    private int nextIndex = 0;

    ParseContext(String[] args) {
      this.args = args;
    }

    String head() {
      return nextIndex < args.length ? args[nextIndex] : null;
    }

    String next() {
      if (nextIndex < args.length) {
        ++nextIndex;
        return head();
      } else {
        throw new RuntimeException("Iterating over the end of argument list.");
      }
    }
  }

  /**
   * Try parsing the switch {@code name} and zero or more non-switch args after it. Also supports
   * the <name>=arg syntax.
   */
  private static List<String> tryParseMulti(ParseContext context, String name) {
    List<String> result = null;
    String head = context.head();
    if (head.equals(name)) {
      context.next();
      result = new ArrayList<>();
      while (context.head() != null && !context.head().startsWith("-")) {
        result.add(context.head());
        context.next();
      }
    } else if (head.startsWith(name) && head.charAt(name.length()) == '=') {
      result = Collections.singletonList(head.substring(name.length() + 1));
      context.next();
    }
    return result;
  }

  /**
   * Try parsing the switch {@code name} and one arg after it. Also supports the <name>=arg syntax.
   */
  private static String tryParseSingle(ParseContext context, String name, String shortName) {
    String head = context.head();
    if (head.equals(name) || head.equals(shortName)) {
      String next = context.next();
      if (next == null) {
        throw new RuntimeException(String.format("Missing argument for '%s'.", head));
      }
      context.next();
      return next;
    }

    if (head.startsWith(name) && head.charAt(name.length()) == '=') {
      context.next();
      return head.substring(name.length() + 1);
    }

    return null;
  }

  /**
   * Try parsing the switch {@code name} as a boolean switch or its negation, with a 'no' between
   * the dashes and the word.
   */
  private static Boolean tryParseBoolean(ParseContext context, String name) {
    if (context.head().equals(name)) {
      context.next();
      return true;
    }
    assert name.startsWith("--");
    if (context.head().equals("--no" + name.substring(2))) {
      context.next();
      return false;
    }
    return null;
  }

  private static Options parseArguments(String[] args) throws IOException {
    // We may have a single argument which is a parameter file path, prefixed with '@'.
    if (args.length == 1 && args[0].startsWith("@")) {
      // TODO(tamaskenez) Implement more sophisticated processing
      // which is aligned with Blaze's
      // com.google.devtools.common.options.ShellQuotedParamsFilePreProcessor
      Path paramsFile = Paths.get(args[0].substring(1));
      List<String> argsList = new ArrayList<>();
      for (String s : Files.readAllLines(paramsFile)) {
        s = s.trim();
        if (s.isEmpty()) {
          continue;
        }
        // Trim optional enclosing single quotes. Unescaping omitted for now.
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
          s = s.substring(1, s.length() - 1);
        }
        argsList.add(s);
      }
      args = argsList.toArray(new String[argsList.size()]);
    }

    Options options = new Options();
    ParseContext context = new ParseContext(args);
    List<String> strings;
    String string;
    Boolean b;
    while (context.head() != null) {
      if (context.head().startsWith("@")) {
        throw new RuntimeException("A params file must be the only argument: " + context.head());
      }
      strings = tryParseMulti(context, "--input");
      if (strings != null) {
        options.inputArchives.addAll(strings);
        continue;
      }
      string = tryParseSingle(context, "--output", "-o");
      if (string != null) {
        options.outputArchive = string;
        continue;
      }
      string = tryParseSingle(context, "--multidex", null);
      if (string != null) {
        options.multidexMode = MultidexStrategy.valueOf(string.toUpperCase());
        continue;
      }
      string = tryParseSingle(context, "--main-dex-list", null);
      if (string != null) {
        options.mainDexListFile = string;
        continue;
      }
      b = tryParseBoolean(context, "--minimal-main-dex");
      if (b != null) {
        options.minimalMainDex = b;
        continue;
      }
      b = tryParseBoolean(context, "--verbose");
      if (b != null) {
        options.verbose = b;
        continue;
      }
      string = tryParseSingle(context, "--max-bytes-wasted-per-file", null);
      if (string != null) {
        System.err.println("Warning: '--max-bytes-wasted-per-file' is ignored.");
        continue;
      }
      string = tryParseSingle(context, "--set-max-idx-number", null);
      if (string != null) {
        System.err.println("Warning: The '--set-max-idx-number' option is ignored.");
        continue;
      }
      b = tryParseBoolean(context, "--forceJumbo");
      if (b != null) {
        System.err.println(
            "Warning: '--forceJumbo' can be safely omitted. Strings will only use "
                + "jumbo-string indexing if necessary.");
        continue;
      }
      string = tryParseSingle(context, "--dex_prefix", null);
      if (string != null) {
        options.dexPrefix = string;
        continue;
      }
      throw new RuntimeException(String.format("Unknown options: '%s'.", context.head()));
    }
    return options;
  }

  /**
   * Extends DexIndexedConsumer.ArchiveConsumer with support for custom dex file name prefix,
   * reindexing a single dex output file to a nonzero index and reporting if any data has been
   * written.
   */
  private static class ArchiveConsumer extends DexIndexedConsumer.ArchiveConsumer {
    private final Integer singleFixedFileIndex;
    private final String prefix;

    private final Map<Integer, Runnable> writers = new TreeMap<>();

    /** If singleFixedFileIndex is not null then we expect only one output dex file */
    private ArchiveConsumer(Path path, String prefix, Integer singleFixedFileIndex) {
      super(path);
      this.prefix = prefix;
      this.singleFixedFileIndex = singleFixedFileIndex;
    }

    private boolean hasAnythingToWrite() {
      return !writers.isEmpty();
    }

    @Override
    protected String getDexFileName(int fileIndex) {
      if (singleFixedFileIndex != null) {
        fileIndex = singleFixedFileIndex;
      }
      return prefix + (fileIndex == 0 ? "" : (fileIndex + 1)) + FileUtils.DEX_EXTENSION;
    }

    @Override
    public synchronized void accept(
        int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (singleFixedFileIndex != null && fileIndex != 0) {
        handler.error(new StringDiagnostic("Result does not fit into a single dex file."));
        return;
      }
      writers.put(fileIndex, () -> super.accept(fileIndex, data, descriptors, handler));
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      for (Runnable writer : writers.values()) {
        writer.run();
      }
      super.finished(handler);
    }
  }

  private static int parseFileIndexFromShardFilename(String inputArchive) {
    Pattern namingPattern = Pattern.compile("([0-9]+)\\..*");
    String name = new File(inputArchive).getName();
    Matcher matcher = namingPattern.matcher(name);
    if (!matcher.matches()) {
      throw new RuntimeException(
          String.format(
              "Expect input named <N>.xxx.zip for --multidex=given_shard but got %s.", name));
    }
    int shard = Integer.parseInt(matcher.group(1));
    if (shard <= 0) {
      throw new RuntimeException(
          String.format("Expect positive N in input named <N>.xxx.zip but got %d.", shard));
    }
    return shard;
  }

  public static void run(String[] args)
      throws CompilationFailedException, IOException, CompilationException, ExecutionException {
    Options options = parseArguments(args);

    if (options.inputArchives.isEmpty()) {
      throw new RuntimeException("Need at least one --input");
    }

    if (options.mainDexListFile != null && options.inputArchives.size() != 1) {
      throw new RuntimeException(
          "--main-dex-list only supported with exactly one --input, use DexFileSplitter for more");
    }

    if (!options.multidexMode.isMultidexAllowed()) {
      if (options.mainDexListFile != null) {
        throw new RuntimeException(
            "--main-dex-list is only supported with multidex enabled, but mode is: "
                + options.multidexMode.toString());
      }
      if (options.minimalMainDex) {
        throw new RuntimeException(
            "--minimal-main-dex is only supported with multidex enabled, but mode is: "
                + options.multidexMode.toString());
      }
    }

    D8Command.Builder builder = D8Command.builder();

    Map<String, Integer> inputOrdering = new HashMap<>(options.inputArchives.size());
    int sequenceNumber = 0;
    for (String s : options.inputArchives) {
      builder.addProgramFiles(Paths.get(s));
      inputOrdering.put(s, sequenceNumber++);
    }

    // Determine enabling multidexing and file indexing.
    Integer singleFixedFileIndex = null;
    switch (options.multidexMode) {
      case OFF:
        singleFixedFileIndex = 0;
        break;
      case GIVEN_SHARD:
        if (options.inputArchives.size() != 1) {
          throw new RuntimeException("'--multidex=given_shard' requires exactly one --input.");
        }
        singleFixedFileIndex = parseFileIndexFromShardFilename(options.inputArchives.get(0)) - 1;
        break;
      case MINIMAL:
      case BEST_EFFORT:
        // Nothing to do.
        break;
      default:
        throw new Unreachable("Unexpected enum: " + options.multidexMode);
    }

    if (options.mainDexListFile != null) {
      builder.addMainDexListFiles(Paths.get(options.mainDexListFile));
    }

    ArchiveConsumer consumer =
        new ArchiveConsumer(
            Paths.get(options.outputArchive), options.dexPrefix, singleFixedFileIndex);
    builder.setProgramConsumer(consumer);

    DexFileMergerHelper.run(builder.build(), options.minimalMainDex, inputOrdering);

    // If input was empty we still need to write out an empty zip.
    if (!consumer.hasAnythingToWrite()) {
      File f = new File(options.outputArchive);
      ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f));
      out.close();
    }
  }

  public static void main(String[] args) {
    try {
      if (PRINT_ARGS) {
        printArgs(args);
      }
      run(args);
    } catch (CompilationFailedException
        | IOException
        | CompilationException
        | ExecutionException e) {
      System.err.println("Merge failed: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void printArgs(String[] args) {
    System.err.printf("r8.DexFileMerger");
    for (String s : args) {
      System.err.printf(" %s", s);
    }
    System.err.println("");
  }
}
