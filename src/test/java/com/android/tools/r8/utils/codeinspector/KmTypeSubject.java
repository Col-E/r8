// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKotlinClassifier;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.Box;
import java.util.List;
import java.util.stream.Collectors;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeVisitor;

public class KmTypeSubject extends Subject {
  private final CodeInspector codeInspector;
  private final KmType kmType;

  KmTypeSubject(CodeInspector codeInspector, KmType kmType) {
    assert kmType != null;
    this.codeInspector = codeInspector;
    this.kmType = kmType;
  }

  // TODO(b/145824437): This is a dup of DescriptorUtils#getDescriptorFromKmType
  static String getDescriptorFromKmType(KmType kmType) {
    if (kmType == null) {
      return null;
    }
    Box<String> descriptor = new Box<>(null);
    kmType.accept(new KmTypeVisitor() {
      @Override
      public void visitClass(String name) {
        // We don't check Kotlin types in tests, but be aware of the relocation issue.
        // See b/70169921#comment25 for more details.
        assert descriptor.get() == null;
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }

      @Override
      public void visitTypeAlias(String name) {
        assert descriptor.get() == null;
        descriptor.set(getDescriptorFromKotlinClassifier(name));
      }
    });
    return descriptor.get();
  }

  public String descriptor() {
    return getDescriptorFromKmType(kmType);
  }

  public List<KmTypeProjectionSubject> typeArguments() {
    return kmType.getArguments().stream()
        .map(kmTypeProjection -> new KmTypeProjectionSubject(codeInspector, kmTypeProjection))
        .collect(Collectors.toList());
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    ClassSubject classSubject = codeInspector.clazz(Reference.classFromDescriptor(descriptor()));
    return classSubject.isRenamed();
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a type is synthetic");
  }
}
