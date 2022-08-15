// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.position.Position;

/**
 * MappingComposer is a utility to do composition of mapping files to map line numbers correctly
 * when having shrunken input that will end up using DEX PC mappings.
 */
public class MappingComposer {

  public static ClassNameMapper compose(ClassNameMapper... classNameMappers) {
    assert classNameMappers.length > 0;
    if (classNameMappers.length == 1) {
      return classNameMappers[0];
    }
    ClassNameMapper.Builder builder = ClassNameMapper.builder();
    for (ClassNameMapper classNameMapper : classNameMappers) {
      compose(builder, classNameMapper);
    }
    return builder.build();
  }

  private static void compose(ClassNameMapper.Builder builder, ClassNameMapper toAdd) {
    toAdd
        .getClassNameMappings()
        .forEach(
            (key, classNamingForNameMapper) -> {
              ClassNamingForNameMapper.Builder classNameMapperBuilder =
                  builder.classNamingBuilder(
                      key, classNamingForNameMapper.originalName, Position.UNKNOWN);
              classNamingForNameMapper.compose(classNameMapperBuilder);
            });
  }
}
