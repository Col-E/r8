// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;

public abstract class EnqueuerEvent {

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

  public abstract static class ClassEnqueuerEvent extends EnqueuerEvent {

    private final DexType clazz;

    public ClassEnqueuerEvent(DexProgramClass clazz) {
      this.clazz = clazz.getType();
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
      super(clazz);
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
      super(clazz);
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
}
