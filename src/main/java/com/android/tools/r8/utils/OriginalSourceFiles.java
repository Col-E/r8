// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import java.util.Map;

/** Abstraction to allow removal of the source file content prior to collecting DEX items. */
public abstract class OriginalSourceFiles {

  private static final OriginalSourceFiles UNREACHABLE =
      new OriginalSourceFiles() {
        @Override
        public DexString getOriginalSourceFile(DexProgramClass clazz) {
          throw new Unreachable();
        }
      };

  private static final OriginalSourceFiles FROM_CLASSES =
      new OriginalSourceFiles() {
        @Override
        public DexString getOriginalSourceFile(DexProgramClass clazz) {
          return clazz.getSourceFile();
        }
      };

  /** For compilations where original source files should never be needed. */
  public static OriginalSourceFiles unreachable() {
    return UNREACHABLE;
  }

  /** For compilations where the original source files is still valid on the classes. */
  public static OriginalSourceFiles fromClasses() {
    return FROM_CLASSES;
  }

  /** Saved mapping of original source files prior to mutating the file on classes. */
  public static OriginalSourceFiles fromMap(Map<DexType, DexString> map) {
    return new OriginalSourceFiles() {
      @Override
      public DexString getOriginalSourceFile(DexProgramClass clazz) {
        return map.get(clazz.getType());
      }
    };
  }

  public abstract DexString getOriginalSourceFile(DexProgramClass clazz);
}
