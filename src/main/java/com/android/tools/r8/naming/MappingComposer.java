// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

/**
 * MappingComposer is a utility to do composition of mapping files to map line numbers correctly
 * when having shrunken input that will end up using DEX PC mappings.
 */
public class MappingComposer {

  public static String compose(ClassNameMapper... classNameMappers) throws MappingComposeException {
    assert classNameMappers.length > 0;
    ComposingBuilder builder = new ComposingBuilder();
    for (ClassNameMapper classNameMapper : classNameMappers) {
      builder.compose(classNameMapper);
    }
    return builder.toString();
  }
}
