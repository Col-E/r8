// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public abstract class ApplicationReaderMap {

  public static ApplicationReaderMap INSTANCE;

  public abstract String getDescriptor(String descriptor);

  public abstract DexType getType(DexType type);

  public abstract DexType getInvertedType(DexType type);

  public static ApplicationReaderMap getInstance(InternalOptions options) {
    ApplicationReaderMap result = new EmptyMap();
    if (options.shouldDesugarRecords() && !options.testing.disableRecordApplicationReaderMap) {
      result = new RecordMap(options.dexItemFactory());
    }
    if (options.shouldDesugarVarHandle()) {
      return new VarHandleMap(result);
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
    public DexType getType(DexType type) {
      return type == factory.recordTagType ? factory.recordType : type;
    }

    @Override
    public DexType getInvertedType(DexType type) {
      return type == factory.recordType ? factory.recordTagType : type;
    }
  }

  public static class VarHandleMap extends ApplicationReaderMap {

    private final ApplicationReaderMap previous;
    private final Map<String, String> descriptorMap =
        ImmutableMap.of(
            DexItemFactory.varHandleDescriptorString,
            DexItemFactory.desugarVarHandleDescriptorString,
            DexItemFactory.methodHandlesLookupDescriptorString,
            DexItemFactory.desugarMethodHandlesLookupDescriptorString);

    public VarHandleMap(ApplicationReaderMap previous) {
      this.previous = previous;
    }

    @Override
    public String getDescriptor(String descriptor) {
      return previous.getDescriptor(descriptorMap.getOrDefault(descriptor, descriptor));
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
}
