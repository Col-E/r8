// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.naming.MemberNaming;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the definition of a Kotlin class.
 *
 * <p>See https://kotlinlang.org/docs/reference/classes.html</p>
 */
class KotlinClass {
  protected static class KotlinProperty {
    private final String name;
    private final String type;
    private final int index;

    private KotlinProperty(String name, String type, int index) {
      this.name = name;
      this.type = type;
      this.index = index;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public int getIndex() {
      return index;
    }
  }
  protected final String className;
  protected final Map<String, KotlinProperty> properties = Maps.newHashMap();

  public KotlinClass(String className) {
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public KotlinClass addProperty(String name, String type) {
    assert !properties.containsKey(name);
    properties.put(name, new KotlinProperty(name, type, properties.size()));
    return this;
  }

  protected KotlinProperty getProperty(String name) {
    assert properties.containsKey(name);
    return properties.get(name);
  }

  public MemberNaming.MethodSignature getGetterForProperty(String name) {
    String type = getProperty(name).type;
    String getterName = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
    return new MemberNaming.MethodSignature(getterName, type, Collections.emptyList());
  }
}
