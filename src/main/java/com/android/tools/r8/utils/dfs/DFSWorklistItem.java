// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dfs;

public abstract class DFSWorklistItem<T> {

  T value;

  public DFSWorklistItem(T value) {
    this.value = value;
  }

  public T getValue() {
    return value;
  }

  public boolean isFullyVisited() {
    return false;
  }

  public boolean isNewlyVisited() {
    return false;
  }

  public NewlyVisitedDFSWorklistItem<T> asNewlyVisited() {
    return null;
  }

  public static class NewlyVisitedDFSWorklistItem<T> extends DFSWorklistItem<T> {

    public NewlyVisitedDFSWorklistItem(T value) {
      super(value);
    }

    @Override
    public boolean isNewlyVisited() {
      return true;
    }

    @Override
    public NewlyVisitedDFSWorklistItem<T> asNewlyVisited() {
      return this;
    }

    public FullyVisitedDFSWorklistItem<T> toFullyVisited() {
      return new FullyVisitedDFSWorklistItem<>(getValue());
    }
  }

  public static class FullyVisitedDFSWorklistItem<T> extends DFSWorklistItem<T> {

    public FullyVisitedDFSWorklistItem(T value) {
      super(value);
    }

    @Override
    public boolean isFullyVisited() {
      return true;
    }
  }
}
