// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import kotlinx.metadata.KmProperty;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;

public abstract class KmPropertySubject extends Subject {
  // TODO(b/145824437): This is a dup of KotlinMetadataSynthesizer#isExtension
  static boolean isExtension(KmProperty kmProperty) {
    return kmProperty.getReceiverParameterType() != null;
  }

  public abstract boolean isExtension();

  public abstract String name();

  public abstract JvmFieldSignature fieldSignature();

  public abstract JvmMethodSignature getterSignature();

  public abstract JvmMethodSignature setterSignature();

  public abstract KmTypeSubject returnType();
}
