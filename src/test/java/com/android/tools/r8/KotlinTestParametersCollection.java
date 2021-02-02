// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.util.Collection;
import java.util.Iterator;

public class KotlinTestParametersCollection implements Iterable<KotlinTestParameters> {

  private final Collection<KotlinTestParameters> parameters;

  public KotlinTestParametersCollection(Collection<KotlinTestParameters> parameters) {
    assert parameters != null;
    this.parameters = parameters;
  }

  @Override
  public Iterator<KotlinTestParameters> iterator() {
    return parameters.iterator();
  }
}
