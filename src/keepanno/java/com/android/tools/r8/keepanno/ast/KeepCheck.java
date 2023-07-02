// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public class KeepCheck extends KeepDeclaration {

  public enum KeepCheckKind {
    REMOVED,
    OPTIMIZED_OUT
  }

  public static class Builder {

    private KeepEdgeMetaInfo metaInfo = KeepEdgeMetaInfo.none();
    private KeepCheckKind kind = KeepCheckKind.REMOVED;
    private KeepItemPattern itemPattern;

    public Builder setMetaInfo(KeepEdgeMetaInfo metaInfo) {
      this.metaInfo = metaInfo;
      return this;
    }

    public Builder setKind(KeepCheckKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder setItemPattern(KeepItemPattern itemPattern) {
      this.itemPattern = itemPattern;
      return this;
    }

    public KeepCheck build() {
      if (itemPattern == null) {
        throw new KeepEdgeException("KeepCheck must have an item pattern.");
      }
      return new KeepCheck(metaInfo, kind, itemPattern);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepEdgeMetaInfo metaInfo;
  private final KeepCheckKind kind;
  private final KeepItemPattern itemPattern;

  private KeepCheck(KeepEdgeMetaInfo metaInfo, KeepCheckKind kind, KeepItemPattern itemPattern) {
    this.metaInfo = metaInfo;
    this.kind = kind;
    this.itemPattern = itemPattern;
  }

  @Override
  public KeepCheck asKeepCheck() {
    return this;
  }

  public KeepEdgeMetaInfo getMetaInfo() {
    return metaInfo;
  }

  public KeepCheckKind getKind() {
    return kind;
  }

  public KeepItemPattern getItemPattern() {
    return itemPattern;
  }
}
