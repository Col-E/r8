// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

public class CustomLibrarySpecification {

  private final Collection<Class<?>> classes;
  private final AndroidApiLevel minApi;

  public CustomLibrarySpecification(Class<?> clazz, AndroidApiLevel minApi) {
    this(ImmutableList.of(clazz), minApi);
  }

  public CustomLibrarySpecification(Collection<Class<?>> classes, AndroidApiLevel minApi) {
    this.classes = classes;
    this.minApi = minApi;
  }

  public Collection<Class<?>> getClasses() {
    return classes;
  }

  public AndroidApiLevel getMinApi() {
    return minApi;
  }
}
