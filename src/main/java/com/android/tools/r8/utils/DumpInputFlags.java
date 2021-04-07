// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.nio.file.Path;

public abstract class DumpInputFlags {

  public static DumpInputFlags noDump() {
    return new DumpInputFlags() {
      @Override
      Path getDumpInputToFile() {
        return null;
      }

      @Override
      Path getDumpInputToDirectory() {
        return null;
      }
    };
  }

  public static DumpInputFlags dumpToFile(Path file) {
    return new DumpInputFlags() {
      @Override
      Path getDumpInputToFile() {
        return file;
      }

      @Override
      Path getDumpInputToDirectory() {
        return null;
      }
    };
  }

  public static DumpInputFlags dumpToDirectory(Path file) {
    return new DumpInputFlags() {
      @Override
      Path getDumpInputToFile() {
        return null;
      }

      @Override
      Path getDumpInputToDirectory() {
        return file;
      }
    };
  }

  abstract Path getDumpInputToFile();

  abstract Path getDumpInputToDirectory();
}
