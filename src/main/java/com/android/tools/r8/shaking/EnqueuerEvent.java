// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;

public abstract class EnqueuerEvent {

  public static UnconditionalKeepInfoEvent unconditional() {
    return UnconditionalKeepInfoEvent.get();
  }

  public DexDefinition getDefinition(DexDefinitionSupplier definitions) {
    return null;
  }

  public boolean isNoSuchEvent() {
    return false;
  }

  public boolean isClassEvent() {
    return false;
  }

  public ClassEnqueuerEvent asClassEvent() {
    return null;
  }

  public boolean isLiveClassEvent() {
    return false;
  }

  public LiveClassEnqueuerEvent asLiveClassEvent() {
    return null;
  }

  public boolean isInstantiatedClassEvent() {
    return false;
  }

  public InstantiatedClassEnqueuerEvent asInstantiatedClassEvent() {
    return null;
  }

  public boolean isUnconditionalKeepInfoEvent() {
    return false;
  }

  public abstract EnqueuerEvent rewrittenWithLens(GraphLens lens);

  public static class NoSuchEnqueuerEvent extends EnqueuerEvent {

    private static final NoSuchEnqueuerEvent INSTANCE = new NoSuchEnqueuerEvent();

    private NoSuchEnqueuerEvent() {}

    public static NoSuchEnqueuerEvent get() {
      return INSTANCE;
    }

    @Override
    public boolean isNoSuchEvent() {
      return true;
    }

    @Override
    public EnqueuerEvent rewrittenWithLens(GraphLens lens) {
      return this;
    }
  }

  public abstract static class ClassEnqueuerEvent extends EnqueuerEvent {

    private final DexType clazz;

    ClassEnqueuerEvent(DexType clazz) {
      this.clazz = clazz;
    }

    @Override
    public DexDefinition getDefinition(DexDefinitionSupplier definitions) {
      return definitions.definitionFor(getType());
    }

    public DexType getType() {
      return clazz;
    }

    @Override
    public boolean isClassEvent() {
      return true;
    }

    @Override
    public ClassEnqueuerEvent asClassEvent() {
      return this;
    }
  }

  public static class LiveClassEnqueuerEvent extends ClassEnqueuerEvent {

    public LiveClassEnqueuerEvent(DexProgramClass clazz) {
      this(clazz.getType());
    }

    private LiveClassEnqueuerEvent(DexType type) {
      super(type);
    }

    @Override
    public boolean isLiveClassEvent() {
      return true;
    }

    @Override
    public LiveClassEnqueuerEvent asLiveClassEvent() {
      return this;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public EnqueuerEvent rewrittenWithLens(GraphLens lens) {
      DexType rewrittenType = lens.lookupType(getType());
      if (rewrittenType == getType()) {
        return this;
      }
      if (rewrittenType.isIntType()) {
        return NoSuchEnqueuerEvent.get();
      }
      return new LiveClassEnqueuerEvent(rewrittenType);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof LiveClassEnqueuerEvent) {
        LiveClassEnqueuerEvent event = (LiveClassEnqueuerEvent) obj;
        return getType() == event.getType();
      }
      return false;
    }

    @Override
    public int hashCode() {
      return (getType().hashCode() << 1) | 0;
    }
  }

  public static class InstantiatedClassEnqueuerEvent extends ClassEnqueuerEvent {

    public InstantiatedClassEnqueuerEvent(DexProgramClass clazz) {
      this(clazz.getType());
    }

    private InstantiatedClassEnqueuerEvent(DexType type) {
      super(type);
    }

    @Override
    public boolean isInstantiatedClassEvent() {
      return true;
    }

    @Override
    public InstantiatedClassEnqueuerEvent asInstantiatedClassEvent() {
      return this;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public EnqueuerEvent rewrittenWithLens(GraphLens lens) {
      DexType rewrittenType = lens.lookupType(getType());
      if (rewrittenType == getType()) {
        return this;
      }
      if (rewrittenType.isIntType()) {
        return NoSuchEnqueuerEvent.get();
      }
      return new InstantiatedClassEnqueuerEvent(rewrittenType);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof InstantiatedClassEnqueuerEvent) {
        InstantiatedClassEnqueuerEvent event = (InstantiatedClassEnqueuerEvent) obj;
        return getType() == event.getType();
      }
      return false;
    }

    @Override
    public int hashCode() {
      return (getType().hashCode() << 1) | 1;
    }
  }

  public static class UnconditionalKeepInfoEvent extends EnqueuerEvent {

    private static final UnconditionalKeepInfoEvent INSTANCE = new UnconditionalKeepInfoEvent();

    private UnconditionalKeepInfoEvent() {}

    public static UnconditionalKeepInfoEvent get() {
      return INSTANCE;
    }

    @Override
    public boolean isUnconditionalKeepInfoEvent() {
      return true;
    }

    @Override
    public EnqueuerEvent rewrittenWithLens(GraphLens lens) {
      return this;
    }
  }
}
