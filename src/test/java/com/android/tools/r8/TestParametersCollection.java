// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class TestParametersCollection implements Iterable<TestParameters> {

  private final ImmutableList<TestParameters> parameters;

  public TestParametersCollection(ImmutableList<TestParameters> parameters) {
    assert parameters != null;
    this.parameters = parameters;
  }

  @NotNull
  @Override
  public Iterator<TestParameters> iterator() {
    return parameters.iterator();
  }

  public Stream<TestParameters> stream() {
    return parameters.stream();
  }
}
