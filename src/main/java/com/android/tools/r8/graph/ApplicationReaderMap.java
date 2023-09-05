// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.InternalOptions;

public abstract class ApplicationReaderMap {

  public abstract String getDescriptor(String descriptor);

  public abstract DexType getType(DexType type);

  public abstract DexType getInvertedType(DexType type);

  public static ApplicationReaderMap getInstance(InternalOptions options) {
    ApplicationReaderMap result = new EmptyMap();
    if (options.shouldDesugarRecords() && !options.testing.disableRecordApplicationReaderMap) {
      result = new RecordMap(options.dexItemFactory());
    }
    return result;
  }

  public static class EmptyMap extends ApplicationReaderMap {

    @Override
    public String getDescriptor(String descriptor) {
      return descriptor;
    }

    @Override
    public DexType getType(DexType type) {
      return type;
    }

    @Override
    public DexType getInvertedType(DexType type) {
      return type;
    }
  }

  public static class RecordMap extends ApplicationReaderMap {

    private final DexItemFactory factory;

    public RecordMap(DexItemFactory factory) {
      this.factory = factory;
    }

    @Override
    public String getDescriptor(String descriptor) {
      return descriptor.equals(DexItemFactory.recordTagDescriptorString)
          ? DexItemFactory.recordDescriptorString
          : descriptor;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public DexType getType(DexType type) {
      return type == factory.recordTagType ? factory.recordType : type;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public DexType getInvertedType(DexType type) {
      return type == factory.recordType ? factory.recordTagType : type;
    }
  }
}
