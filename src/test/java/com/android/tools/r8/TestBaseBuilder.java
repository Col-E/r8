// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.Collection;

public abstract class TestBaseBuilder<
        C extends BaseCommand,
        B extends BaseCommand.Builder<C, B>,
        CR extends TestBaseResult<CR, RR>,
        RR extends TestRunResult,
        T extends TestBaseBuilder<C, B, CR, RR, T>>
    extends TestBuilder<RR, T> {

  final B builder;

  TestBaseBuilder(TestState state, B builder) {
    super(state);
    this.builder = builder;
  }

  @Override
  public T addProgramClassFileData(Collection<byte[]> classes) {
    for (byte[] clazz : classes) {
      builder.addClassProgramData(clazz, Origin.unknown());
    }
    return self();
  }

  @Override
  public T addProgramFiles(Collection<Path> files) {
    builder.addProgramFiles(files);
    return self();
  }

  @Override
  public T addLibraryFiles(Collection<Path> files) {
    builder.addLibraryFiles(files);
    return self();
  }

  public T addMainDexListFiles(Collection<Path> files) {
    builder.addMainDexListFiles(files);
    return self();
  }
}
