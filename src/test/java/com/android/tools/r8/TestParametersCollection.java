// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class TestParametersCollection implements Iterable<TestParameters> {

  private final Collection<TestParameters> parameters;

  public TestParametersCollection(Collection<TestParameters> parameters) {
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
