// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.OutputSink;
import java.io.IOException;
import java.util.Set;
import java.util.TreeMap;

public class AndroidAppOutputSink extends ForwardingOutputSink {

  private final AndroidApp.Builder builder = AndroidApp.builder();
  private final TreeMap<String, DescriptorsWithContents> dexFilesWithPrimary = new TreeMap<>();
  private final TreeMap<Integer, DescriptorsWithContents> dexFilesWithId = new TreeMap<>();
  private boolean closed = false;

  public AndroidAppOutputSink(OutputSink forwardTo) {
    super(forwardTo);
  }

  public AndroidAppOutputSink() {
    super(new IgnoreContentsOutputSink());
  }

  @Override
  public synchronized void writeDexFile(byte[] contents, Set<String> classDescriptors, int fileId)
      throws IOException {
    // Sort the files by id so that their order is deterministic. Some tests depend on this.
    dexFilesWithId.put(fileId, new DescriptorsWithContents(classDescriptors, contents));
    super.writeDexFile(contents, classDescriptors, fileId);
  }

  @Override
  public synchronized void writeDexFile(byte[] contents, Set<String> classDescriptors,
      String primaryClassName)
      throws IOException {
    // Sort the files by their name for good measure.
    dexFilesWithPrimary
        .put(primaryClassName, new DescriptorsWithContents(classDescriptors, contents));
    super.writeDexFile(contents, classDescriptors, primaryClassName);
  }

  @Override
  public void writePrintUsedInformation(byte[] contents) throws IOException {
    builder.setDeadCode(contents);
    super.writePrintUsedInformation(contents);
  }

  @Override
  public void writeProguardMapFile(byte[] contents) throws IOException {
    builder.setProguardMapData(contents);
    super.writeProguardMapFile(contents);
  }

  @Override
  public void writeProguardSeedsFile(byte[] contents) throws IOException {
    builder.setProguardSeedsData(contents);
    super.writeProguardSeedsFile(contents);
  }

  @Override
  public void writeMainDexListFile(byte[] contents) throws IOException {
    builder.setMainDexListOutputData(contents);
    super.writeMainDexListFile(contents);
  }

  @Override
  public void close() throws IOException {
    assert !closed;
    assert dexFilesWithId.isEmpty() || dexFilesWithPrimary.isEmpty();
    dexFilesWithPrimary.forEach((v, d) -> builder.addDexProgramData(d.contents, d.descriptors, v));
    dexFilesWithId.forEach((v, d) -> builder.addDexProgramData(d.contents, d.descriptors));
    closed = true;
    super.close();
  }

  public AndroidApp build() {
    assert closed;
    return builder.build();
  }

  private static class DescriptorsWithContents {

    final Set<String> descriptors;
    final byte[] contents;

    private DescriptorsWithContents(Set<String> descriptors, byte[] contents) {
      this.descriptors = descriptors;
      this.contents = contents;
    }
  }
}
