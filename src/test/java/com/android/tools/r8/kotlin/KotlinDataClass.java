// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.naming.MemberNaming;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the definition of a Kotlin data class.
 *
 * <p>See https://kotlinlang.org/docs/reference/data-classes.html</p>
 */
class KotlinDataClass extends KotlinClass {

  KotlinDataClass(String className) {
    super(className);
  }

  @Override
  public KotlinDataClass addProperty(String name, String type, Visibility visibility) {
    return (KotlinDataClass) super.addProperty(name, type, visibility);
  }

  public MemberNaming.MethodSignature getComponentNFunctionForProperty(String name) {
    KotlinProperty property = getProperty(name);

    String componentName = "component" + Integer.toString(property.getIndex() + 1);
    return new MemberNaming.MethodSignature(componentName, property.getType(),
        Collections.emptyList());
  }

  public MemberNaming.MethodSignature getCopySignature() {
    List<String> propertiesTypes = properties.values().stream()
        .sorted(Comparator.comparingInt(p -> p.getIndex()))
        .map(p -> p.getType())
        .collect(Collectors.toList());
    return new MemberNaming.MethodSignature("copy", className, propertiesTypes);
  }

  public MemberNaming.MethodSignature getCopyDefaultSignature() {
    List<String> propertiesTypes = properties.values().stream()
        .sorted(Comparator.comparingInt(p -> p.getIndex()))
        .map(p -> p.getType())
        .collect(Collectors.toList());

    List<String> copyDefaultParameterTypes = new ArrayList<>(propertiesTypes.size() + 3);
    copyDefaultParameterTypes.add(className);
    copyDefaultParameterTypes.addAll(propertiesTypes);
    copyDefaultParameterTypes.add("int");
    copyDefaultParameterTypes.add("java.lang.Object");
    return new MemberNaming.MethodSignature("copy$default", className,
        copyDefaultParameterTypes);
  }
}
