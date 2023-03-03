// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.naming.Range;
import com.android.tools.r8.utils.SegmentTree;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// This is a parser for the Kotlin-generated source debug extensions, which is a stratified map.
// The kotlin-parser for this data is can be found here in the kotlin compiler:
// compiler/backend/src/org/jetbrains/kotlin/codegen/inline/SMAPParser.kt
public class KotlinSourceDebugExtensionParser {

  private static final String SMAP_IDENTIFIER = "SMAP";
  private static final String SMAP_SECTION_START = "*S";
  private static final String SMAP_SECTION_KOTLIN_START = SMAP_SECTION_START + " Kotlin";
  private static final String SMAP_SECTION_KOTLIN_DEBUG_START = SMAP_SECTION_START + " KotlinDebug";
  private static final String SMAP_FILES_IDENTIFIER = "*F";
  private static final String SMAP_LINES_IDENTIFIER = "*L";
  private static final String SMAP_END_IDENTIFIER = "*E";

  public static class KotlinSourceDebugExtensionParserException extends Exception {

    KotlinSourceDebugExtensionParserException(String message) {
      super(message);
    }
  }

  public static class BufferedStringReader implements Closeable {

    private final BufferedReader reader;

    private String readLine;

    BufferedStringReader(String data) {
      reader = new BufferedReader(new StringReader(data));
    }

    String readNextLine() throws IOException {
      return readLine = reader.readLine();
    }

    boolean readExpectedLine(String expected) throws IOException {
      return readNextLine().equals(expected);
    }

    void readExpectedLineOrThrow(String expected)
        throws KotlinSourceDebugExtensionParserException, IOException {
      if (!readExpectedLine(expected)) {
        throw new KotlinSourceDebugExtensionParserException(
            "The string " + readLine + " does not match the expected string " + expected);
      }
    }

    boolean isEOF() {
      return readLine == null;
    }

    BufferedStringReader readUntil(String terminator) throws IOException {
      while (!terminator.equals(readLine) && !isEOF()) {
        readNextLine();
      }
      if (isEOF()) {
        return this;
      }
      return this;
    }

    void readUntil(
        String terminator,
        int linesInBlock,
        ThrowingConsumer<List<String>, KotlinSourceDebugExtensionParserException> callback)
        throws IOException, KotlinSourceDebugExtensionParserException {
      readUntil(terminator::equals, linesInBlock, callback);
    }

    String readUntil(
        Predicate<String> terminator,
        int linesInBlock,
        ThrowingConsumer<List<String>, KotlinSourceDebugExtensionParserException> callback)
        throws IOException, KotlinSourceDebugExtensionParserException {
      if (terminator.test(readLine)) {
        return readLine;
      }
      List<String> readStrings = new ArrayList<>();
      readStrings.add(readNextLine());
      int linesLeft = linesInBlock;
      while (!terminator.test(readLine) && !isEOF()) {
        if (linesLeft == 1) {
          assert readStrings.size() == linesInBlock;
          callback.accept(readStrings);
          linesLeft = linesInBlock;
          readStrings = new ArrayList<>();
        } else {
          linesLeft -= 1;
        }
        readStrings.add(readNextLine());
      }
      if (!readStrings.isEmpty() && !terminator.test(readStrings.get(0))) {
        throw new KotlinSourceDebugExtensionParserException(
            "Block size does not match linesInBlock = " + linesInBlock);
      }
      return readStrings.isEmpty() ? null : readStrings.get(0);
    }

    @Override
    public void close() throws IOException {
      reader.close();
    }
  }

  public static Result parse(String annotationData) {
    if (annotationData == null || annotationData.isEmpty()) {
      return null;
    }

    // The source debug smap data is on the following form:
    // SMAP
    // <filename>
    // Kotlin <-- why is this even here?
    // *S <section>
    // *F
    // + <file_id_i> <file_name_i>
    // <path_i>
    // *L
    // <from>#<file>,<range>:<debug-line-position>
    // <from>#<file>:<debug-line-position>
    // *E <-- This is an error in versions prior to kotlin 1.5 and is not present in kotlin 1.5.
    // *S KotlinDebug
    // + <file_id_i> <file_name_i>
    // <path_i>
    // *L
    // <from>#<file>,<range>:<debug-line-position>
    // <from>#<file>:<debug-line-position>
    // *E
    try (BufferedStringReader reader = new BufferedStringReader(annotationData)) {
      // Check for SMAP
      if (!reader.readExpectedLine(SMAP_IDENTIFIER)) {
        return null;
      }
      if (reader.readUntil(SMAP_SECTION_KOTLIN_START).isEOF()) {
        return null;
      }

      StratumBuilder inlineePositions = new StratumBuilder();
      StratumBuilder calleePositions = new StratumBuilder();

      String terminatedLine = parseStratumContents(reader, inlineePositions);

      // Read callee positions
      if (terminatedLine.equals(SMAP_END_IDENTIFIER)) {
        String nextLine = reader.readNextLine();
        // If we read to the end then there is not KotlinDebug section which translates to no need
        // for mapping the resulting positions obtained from the inlineePositions.
        if (reader.isEOF()) {
          assert nextLine == null;
          return new Result(inlineePositions.segmentTree, calleePositions.segmentTree);
        }
        if (!nextLine.equals(SMAP_SECTION_KOTLIN_DEBUG_START)) {
          return null;
        }
      } else if (!terminatedLine.equals(SMAP_SECTION_KOTLIN_DEBUG_START)) {
        return null;
      }
      parseStratumContents(reader, calleePositions);

      // Ensure that we read the end section and it is terminated.
      if (reader.isEOF() && !reader.readLine.equals(SMAP_END_IDENTIFIER)) {
        throw new KotlinSourceDebugExtensionParserException(
            "Unexpected EOF when parsing SMAP debug entries");
      }
      return new Result(inlineePositions.segmentTree, calleePositions.segmentTree);
    } catch (IOException | KotlinSourceDebugExtensionParserException e) {
      return null;
    }
  }

  private static String parseStratumContents(BufferedStringReader reader, StratumBuilder builder)
      throws KotlinSourceDebugExtensionParserException, IOException {
    // At this point we should be parsing a kotlin source debug extension, so we will throw if we
    // read an unexpected line.
    reader.readExpectedLineOrThrow(SMAP_FILES_IDENTIFIER);
    // Iterate over the files section with the format:
    // + <file_number_i> <file_name_i>
    // <file_path_i>
    reader.readUntil(
        SMAP_LINES_IDENTIFIER, 2, block -> addFileToBuilder(block.get(0), block.get(1), builder));

    // Ensure that we read *L.
    if (reader.isEOF()) {
      throw new KotlinSourceDebugExtensionParserException(
          "Unexpected EOF - no debug line positions");
    }
    // Iterate over the debug line number positions:
    // <from>#<file>,<range>:<debug-line-position>
    // or
    // <from>#<file>:<debug-line-position>
    return reader.readUntil(
        line -> line.equals(SMAP_END_IDENTIFIER) || line.startsWith(SMAP_SECTION_START),
        1,
        block -> addDebugEntryToBuilder(block.get(0), builder));
  }

  private static void addFileToBuilder(String entryLine, String filePath, StratumBuilder builder)
      throws KotlinSourceDebugExtensionParserException {
    // + <file_number_i> <file_name_i>
    // <file_path_i>
    String[] entries = StringUtils.splitKnownSize(entryLine.trim(), ' ', 3);
    if (entries == null || !entries[0].equals("+")) {
      throw new KotlinSourceDebugExtensionParserException(
          "Wrong number of entries on line " + entryLine);
    }
    String fileName = entries[2];
    if (fileName.isEmpty()) {
      throw new KotlinSourceDebugExtensionParserException(
          "Did not expect file name to be empty for line " + entryLine);
    }
    if (filePath == null || filePath.isEmpty()) {
      throw new KotlinSourceDebugExtensionParserException(
          "Did not expect file path to be null or empty for " + entryLine);
    }
    int index = asInteger(entries[1]);
    Source source = new Source(fileName, filePath);
    Source existingSource = builder.files.put(index, source);
    if (existingSource != null) {
      throw new KotlinSourceDebugExtensionParserException(
          "File index " + index + " was already mapped to an existing source: " + source);
    }
  }

  private static int asInteger(String numberAsString)
      throws KotlinSourceDebugExtensionParserException {
    int number = -1;
    try {
      number = Integer.parseInt(numberAsString);
    } catch (NumberFormatException e) {
      throw new KotlinSourceDebugExtensionParserException(
          "Could not parse number " + numberAsString);
    }
    return number;
  }

  private static void addDebugEntryToBuilder(String debugEntry, StratumBuilder builder)
      throws KotlinSourceDebugExtensionParserException {
    // <from>#<file>,<size>:<debug-line-position>,<size>
    // or
    // <from>#<file>:<debug-line-position>
    // All positions should define intervals for mappings.
    try {
      int size = 1;
      int targetSplit = debugEntry.indexOf(':');
      int targetRangeStart = targetSplit + 1;
      int targetRangeSeparator = debugEntry.indexOf(',', targetSplit);
      int target;
      if (targetRangeSeparator > -1) {
        target = Integer.parseInt(debugEntry.substring(targetRangeStart, targetRangeSeparator));
        size = Integer.parseInt(debugEntry.substring(targetRangeSeparator + 1));
      } else {
        target = Integer.parseInt(debugEntry.substring(targetRangeStart));
      }
      String original = debugEntry.substring(0, targetSplit);
      int fileIndexSplit = original.indexOf('#');
      int originalStart = Integer.parseInt(original.substring(0, fileIndexSplit));
      // The range may have a different end than start.
      String fileAndEndRange = original.substring(fileIndexSplit + 1);
      int endRangeCharPosition = fileAndEndRange.indexOf(',');
      if (endRangeCharPosition > -1) {
        // The file should be at least one number wide.
        assert endRangeCharPosition > 0;
        size = Integer.parseInt(fileAndEndRange.substring(endRangeCharPosition + 1));
      } else {
        endRangeCharPosition = fileAndEndRange.length();
      }
      int fileIndex = Integer.parseInt(fileAndEndRange.substring(0, endRangeCharPosition));
      Source thisFileSource = builder.files.get(fileIndex);
      if (thisFileSource == null) {
        throw new KotlinSourceDebugExtensionParserException(
            "Could not find file with index " + fileIndex);
      }
      Range range = new Range(originalStart, originalStart + (size - 1));
      Position position = new Position(thisFileSource, range);
      builder.segmentTree.add(target, target + (size - 1), position);
    } catch (NumberFormatException e) {
      throw new KotlinSourceDebugExtensionParserException("Could not convert position to number");
    }
  }

  public static class Result {

    private final SegmentTree<Position> inlineePositions;
    private final SegmentTree<Position> calleePositions;

    public Result(SegmentTree<Position> inlineePositions, SegmentTree<Position> calleePositions) {
      this.inlineePositions = inlineePositions;
      this.calleePositions = calleePositions;
    }

    public Map.Entry<Integer, Position> lookupInlinedPosition(int point) {
      return inlineePositions.findEntry(point);
    }

    public Map.Entry<Integer, Position> lookupCalleePosition(int point) {
      return calleePositions.findEntry(point);
    }

    public int inlinePositionsCount() {
      return inlineePositions.size();
    }
  }

  public static class StratumBuilder {

    SegmentTree<Position> segmentTree = new SegmentTree<>(false);
    Map<Integer, Source> files = new HashMap<>();
  }

  public static class Source {
    private final String fileName;
    private final String path;

    private Source(String fileName, String path) {
      this.fileName = fileName;
      this.path = path;
    }

    public String getFileName() {
      return fileName;
    }

    public String getPath() {
      return path;
    }

    @Override
    public String toString() {
      return path + "(" + fileName + ")";
    }
  }

  public static class Position {
    private final Source source;
    private final Range range;

    public Position(Source source, Range range) {
      this.source = source;
      this.range = range;
    }

    public Source getSource() {
      return source;
    }

    public Range getRange() {
      return range;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(range.from);
      sb.append("#");
      sb.append(source);
      if (range.to != range.from) {
        sb.append(",");
        sb.append(range.to);
      }
      sb.append(":");
      return sb.toString();
    }
  }
}
