// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static java.lang.Integer.MAX_VALUE;

import com.android.tools.r8.naming.LineReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public abstract class ProguardMapReaderWithFiltering implements LineReader {

  private int startIndex = 0;
  private int endIndex = 0;

  public abstract byte[] read() throws IOException;

  public abstract int getStartIndex();

  public abstract int getEndIndex();

  public abstract boolean exceedsBuffer();

  @Override
  public String readLine() throws IOException {
    byte[] bytes = readLineFromMultipleReads();
    if (bytes == null) {
      return null;
    }
    return new String(bytes, startIndex, endIndex - startIndex, StandardCharsets.UTF_8);
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

    public ProguardMapReaderWithFilteringMappedBuffer(Path mappingFile) throws IOException {
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

    public ProguardMapReaderWithFilteringInputBuffer(InputStream inputStream) {
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
