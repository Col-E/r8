// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.function.BiConsumer;

/**
 * This is an interface for all generated classes from api-versions.xml for building a database from
 * a serialized hashed format.
 */
public interface AndroidApiForHashingClass {

  DexType getType();

  AndroidApiLevel getApiLevel();

  void visitMethodsWithApiLevels(BiConsumer<DexMethod, AndroidApiLevel> consumer);

  void visitFieldsWithApiLevels(BiConsumer<DexField, AndroidApiLevel> consumer);
}
