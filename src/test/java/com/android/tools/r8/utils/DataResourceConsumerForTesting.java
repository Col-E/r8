// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class DataResourceConsumerForTesting implements DataResourceConsumer {

  private final DataResourceConsumer inner;
  private final Map<String, ImmutableList<String>> resources = new HashMap<>();

  public DataResourceConsumerForTesting() {
    this(null);
  }

  public DataResourceConsumerForTesting(DataResourceConsumer inner) {
    this.inner = inner;
  }

  @Override
  public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
    if (inner != null) {
      inner.accept(directory, diagnosticsHandler);
    }
  }

  @Override
  public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
    assertFalse(resources.containsKey(file.getName()));
    try {
      byte[] bytes = ByteStreams.toByteArray(file.getByteStream());
      String contents = new String(bytes, Charset.defaultCharset());
      resources.put(file.getName(), ImmutableList.copyOf(contents.split(System.lineSeparator())));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (inner != null) {
      inner.accept(file, diagnosticsHandler);
    }
  }

  @Override
  public void finished(DiagnosticsHandler handler) {}

  public ImmutableList<String> get(String name) {
    return resources.get(name);
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public int size() {
    return resources.size();
  }
}
