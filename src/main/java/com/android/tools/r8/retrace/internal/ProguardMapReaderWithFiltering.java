// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static java.lang.Integer.MAX_VALUE;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.LineReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Predicate;

public abstract class ProguardMapReaderWithFiltering implements LineReader {

  @SuppressWarnings("DefaultCharset")
  private static final byte[] SOURCE_FILE_BYTES = "sourceFile".getBytes();

  public enum LineParserNode {
    BEGINNING,
    BEGINNING_NO_WHITESPACE,
    SEEN_ORIGINAL_CLASS,
    SEEN_ARROW,
    SEEN_OBFUSCATED_CLASS,
    COMPLETE_CLASS_MAPPING,
    IS_COMMENT_START,
    IS_COMMENT_SOURCE_FILE,
    NOT_CLASS_MAPPING_OR_SOURCE_FILE;

    private boolean isTerminal() {
      return this == NOT_CLASS_MAPPING_OR_SOURCE_FILE
          || this == COMPLETE_CLASS_MAPPING
          || this == IS_COMMENT_SOURCE_FILE;
    }
  }

  // The LineParserState encodes a simple state that the line parser can be in, where the
  // (successful) transitions allowed are:

  // BEGINNING -> BEGINNING_NO_WHITESPACE -> SEEN_ORIGINAL_CLASS || IS_COMMENT_START

  // IS_COMMENT_START -> IS_COMMENT_SOURCE_FILE

  // SEEN_ORIGINAL_CLASS -> SEEN_ARROW -> SEEN_OBFUSCATED_CLASS -> COMPLETE_CLASS_MAPPING
  //
  // From all states there is a transition on invalid input to NOT_CLASS_MAPPING_OR_SOURCE_FILE.
  // The terminal states are:
  // { IS_COMMENT_SOURCE_FILE, COMPLETE_CLASS_MAPPING, NOT_CLASS_MAPPING_OR_SOURCE_FILE }
  //
  private static class LineParserState {

    private int currentIndex;
    private final int endIndex;
    private final byte[] bytes;
    private LineParserNode node;

    private LineParserState(byte[] bytes, int currentIndex, int endIndex) {
      this.currentIndex = currentIndex;
      this.endIndex = endIndex;
      this.bytes = bytes;
      node = LineParserNode.BEGINNING;
    }

    private LineParserNode run() {
      while (!node.isTerminal()) {
        node = computeNextState();
      }
      return node;
    }

    private LineParserNode computeNextState() {
      assert node != LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
      switch (node) {
        case BEGINNING:
          return readUntilNoWhiteSpace()
              ? LineParserNode.BEGINNING_NO_WHITESPACE
              : LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
        case BEGINNING_NO_WHITESPACE:
          if (isCommentChar()) {
            return LineParserNode.IS_COMMENT_START;
          } else {
            int readLength = readCharactersNoWhiteSpaceUntil(' ');
            return readLength > 0
                ? LineParserNode.SEEN_ORIGINAL_CLASS
                : LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
          }
        case SEEN_ORIGINAL_CLASS:
          return readArrow()
              ? LineParserNode.SEEN_ARROW
              : LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
        case SEEN_ARROW:
          int colonIndex = readCharactersNoWhiteSpaceUntil(':');
          return colonIndex > 0
              ? LineParserNode.SEEN_OBFUSCATED_CLASS
              : LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
        case SEEN_OBFUSCATED_CLASS:
          boolean read = readColon();
          if (!read) {
            return LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
          }
          boolean noWhiteSpace = readUntilNoWhiteSpace();
          return (!noWhiteSpace || isCommentChar())
              ? LineParserNode.COMPLETE_CLASS_MAPPING
              : LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
        case IS_COMMENT_START:
          if (readCharactersUntil('{')
              && readCharactersUntil(':')
              && readSingleOrDoubleQuote()
              && readSourceFile()) {
            return LineParserNode.IS_COMMENT_SOURCE_FILE;
          } else {
            return LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;
          }
        default:
          assert node.isTerminal();
          throw new Unreachable("Should not compute next state on terminal state");
      }
    }

    private boolean readColon() {
      return read(':');
    }

    private boolean readCharactersUntil(char ch) {
      while (currentIndex < endIndex) {
        if (bytes[currentIndex++] == ch) {
          return true;
        }
      }
      return false;
    }

    private int readCharactersNoWhiteSpaceUntil(char ch) {
      int startIndex = currentIndex;
      while (currentIndex < endIndex) {
        byte readByte = bytes[currentIndex];
        if (readByte == ch) {
          return currentIndex - startIndex;
        }
        if (Character.isWhitespace(readByte)) {
          return -1;
        }
        currentIndex++;
      }
      return -1;
    }

    private boolean readUntilNoWhiteSpace() {
      while (currentIndex < endIndex) {
        if (!Character.isWhitespace(bytes[currentIndex])) {
          return true;
        }
        currentIndex++;
      }
      return false;
    }

    private boolean readArrow() {
      return readSpace() && read('-') && read('>') && readSpace();
    }

    private boolean readSpace() {
      return read(' ');
    }

    private boolean read(char ch) {
      return bytes[currentIndex++] == ch;
    }

    private boolean isCommentChar() {
      return bytes[currentIndex] == '#';
    }

    private boolean readSourceFile() {
      if (endIndex - currentIndex < SOURCE_FILE_BYTES.length) {
        return false;
      }
      int endSourceFileIndex = currentIndex + SOURCE_FILE_BYTES.length;
      int sourceFileByteIndex = 0;
      for (; currentIndex < endSourceFileIndex; currentIndex++) {
        if (SOURCE_FILE_BYTES[sourceFileByteIndex++] != bytes[currentIndex]) {
          return false;
        }
      }
      return readSingleOrDoubleQuote();
    }

    private boolean readSingleOrDoubleQuote() {
      byte readByte = bytes[currentIndex++];
      return readByte == '\'' || readByte == '"';
    }
  }

  private int startIndex = 0;
  private int endIndex = 0;

  private final Predicate<String> filter;
  private final boolean readPreambleAndSourceFiles;

  protected ProguardMapReaderWithFiltering(
      Predicate<String> filter, boolean readPreambleAndSourceFiles) {
    this.filter = filter;
    this.readPreambleAndSourceFiles = readPreambleAndSourceFiles;
  }

  public abstract byte[] read() throws IOException;

  public abstract int getStartIndex();

  public abstract int getEndIndex();

  public abstract boolean exceedsBuffer();

  private boolean isInsideClassOfInterest = false;
  private boolean seenFirstClass = false;
  private LineParserNode lineParserResult = LineParserNode.NOT_CLASS_MAPPING_OR_SOURCE_FILE;

  @Override
  public String readLine() throws IOException {
    while (true) {
      byte[] bytes = readLineFromMultipleReads();
      if (bytes == null) {
        return null;
      }
      if (filter == null) {
        return new String(bytes, startIndex, endIndex - startIndex, StandardCharsets.UTF_8);
      }
      lineParserResult = new LineParserState(bytes, startIndex, endIndex).run();
      if (lineParserResult == LineParserNode.COMPLETE_CLASS_MAPPING) {
        seenFirstClass = true;
        String classMapping = getBufferAsString(bytes);
        String obfuscatedClassName = getObfuscatedClassName(classMapping);
        isInsideClassOfInterest = filter.test(obfuscatedClassName);
        if (isInsideClassOfInterest || readPreambleAndSourceFiles) {
          return classMapping;
        }
      } else if (lineParserResult == LineParserNode.IS_COMMENT_SOURCE_FILE
          && readPreambleAndSourceFiles) {
        return getBufferAsString(bytes);
      } else if (isInsideClassOfInterest || (!seenFirstClass && readPreambleAndSourceFiles)) {
        return getBufferAsString(bytes);
      }
    }
  }

  public boolean isClassMapping() {
    return lineParserResult == LineParserNode.COMPLETE_CLASS_MAPPING;
  }

  private String getBufferAsString(byte[] bytes) {
    return new String(bytes, startIndex, endIndex - startIndex, StandardCharsets.UTF_8);
  }

  private String getObfuscatedClassName(String classMapping) {
    int arrowIndex = classMapping.indexOf(">");
    return classMapping.substring(arrowIndex + 2, classMapping.length() - 1);
  }

  private byte[] readLineFromMultipleReads() throws IOException {
    startIndex = 0;
    endIndex = 0;
    byte[] currentReadBytes = null;
    do {
      byte[] readBytes = read();
      if (readBytes == null) {
        return currentReadBytes;
      }
      if (exceedsBuffer() || currentReadBytes != null) {
        // We are building up a partial result where all bytes will be present in the
        // currentReadBytes array.
        int thisLength = getEndIndex() - getStartIndex();
        int currentReadBytesLength = currentReadBytes == null ? 0 : currentReadBytes.length;
        byte[] newReadBytes = new byte[thisLength + currentReadBytesLength];
        if (currentReadBytes != null) {
          System.arraycopy(currentReadBytes, 0, newReadBytes, 0, currentReadBytes.length);
        }
        System.arraycopy(
            readBytes, getStartIndex(), newReadBytes, currentReadBytesLength, thisLength);
        currentReadBytes = newReadBytes;
        endIndex = newReadBytes.length;
      } else {
        currentReadBytes = readBytes;
        startIndex = getStartIndex();
        endIndex = getEndIndex();
      }
    } while (exceedsBuffer());
    return currentReadBytes;
  }

  public static class ProguardMapReaderWithFilteringMappedBuffer
      extends ProguardMapReaderWithFiltering {

    private final int PAGE_SIZE = 1024 * 8;

    private final FileChannel fileChannel;
    private MappedByteBuffer mappedByteBuffer;
    private final long channelSize;
    private final byte[] buffer = new byte[PAGE_SIZE];

    private int currentPosition = 0;
    private int temporaryBufferPosition = 0;

    public ProguardMapReaderWithFilteringMappedBuffer(
        Path mappingFile,
        Predicate<String> classNamesOfInterest,
        boolean readPreambleAndSourceFiles)
        throws IOException {
      super(classNamesOfInterest, readPreambleAndSourceFiles);
      fileChannel = FileChannel.open(mappingFile, StandardOpenOption.READ);
      channelSize = fileChannel.size();
      readFromChannel();
    }

    private void readFromChannel() throws IOException {
      mappedByteBuffer =
          fileChannel.map(
              MapMode.READ_ONLY,
              currentPosition,
              Math.min(channelSize - currentPosition, MAX_VALUE));
    }

    @Override
    public byte[] read() throws IOException {
      if (currentPosition >= channelSize) {
        return null;
      }
      temporaryBufferPosition = 0;
      while (currentPosition < channelSize) {
        if (!mappedByteBuffer.hasRemaining()) {
          readFromChannel();
        }
        byte readByte = readByte();
        if (readByte == '\n') {
          break;
        }
        buffer[temporaryBufferPosition++] = readByte;
        if (temporaryBufferPosition == PAGE_SIZE) {
          break;
        }
      }
      return buffer;
    }

    @Override
    public int getStartIndex() {
      return 0;
    }

    @Override
    public int getEndIndex() {
      if (temporaryBufferPosition > 0 && buffer[temporaryBufferPosition - 1] == '\r') {
        return temporaryBufferPosition - 1;
      }
      return temporaryBufferPosition;
    }

    @Override
    public boolean exceedsBuffer() {
      return temporaryBufferPosition == PAGE_SIZE;
    }

    private byte readByte() {
      currentPosition += 1;
      return mappedByteBuffer.get();
    }

    @Override
    public void close() throws IOException {
      fileChannel.close();
    }
  }

  public static class ProguardMapReaderWithFilteringInputBuffer
      extends ProguardMapReaderWithFiltering {

    private final InputStream inputStream;

    private final int BUFFER_SIZE = 1024 * 8;
    private final byte[] buffer = new byte[BUFFER_SIZE];

    private int bufferIndex = BUFFER_SIZE;
    private int startIndex = 0;
    private int endIndex = 0;
    private int endReadIndex = 0;

    public ProguardMapReaderWithFilteringInputBuffer(
        InputStream inputStream,
        Predicate<String> classNamesOfInterest,
        boolean readPreambleAndSourceFiles) {
      super(classNamesOfInterest, readPreambleAndSourceFiles);
      this.inputStream = inputStream;
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }

    @Override
    public byte[] read() throws IOException {
      if (bufferIndex >= endReadIndex) {
        endReadIndex = inputStream.read(buffer);
        if (endReadIndex == -1) {
          return null;
        }
        bufferIndex = 0;
      }
      startIndex = bufferIndex;
      boolean foundLineBreak = false;
      for (endIndex = startIndex; endIndex < endReadIndex; endIndex++) {
        if (buffer[endIndex] == '\n') {
          foundLineBreak = true;
          break;
        }
      }
      bufferIndex = endIndex;
      if (foundLineBreak) {
        bufferIndex += 1;
      }
      return buffer;
    }

    @Override
    public int getStartIndex() {
      return startIndex;
    }

    @Override
    public int getEndIndex() {
      if (endIndex > 0 && buffer[endIndex - 1] == '\r') {
        return endIndex - 1;
      }
      return endIndex;
    }

    @Override
    public boolean exceedsBuffer() {
      return endReadIndex == endIndex;
    }
  }
}
