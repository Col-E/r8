// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import java.util.function.Consumer;

/** Inspector for a class or interface definition. */
@KeepForApi
public interface ClassInspector {

  /** Get the class reference for the class of this inspector. */
  ClassReference getClassReference();

  /** Get the source file attribute content if present, otherwise null. */
  String getSourceFile();

  /** Iterate all fields declared in the class/interface (unspecified order). */
  void forEachField(Consumer<FieldInspector> inspection);

  /** Iterate all methods declared in the class/interface (unspecified order). */
  void forEachMethod(Consumer<MethodInspector> inspection);
}
