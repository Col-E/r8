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
class TestKotlinClass {

  /**
   * This is the suffix appended by Kotlin compiler to getter and setter method names of
   * internal properties.
   *
   * It must match the string passed in command-line option "-module-name" of Kotlin compiler. The
   * default value is "main".
   */
  private static final String KOTLIN_MODULE_NAME = "main";

  enum Visibility {
    PUBLIC,
    INTERNAL,
    PROTECTED,
    PRIVATE;
  }

  protected static class KotlinProperty {
    private final String name;
    private final String type;
    private final Visibility visibility;
    private final int index;

    private KotlinProperty(String name, String type, Visibility visibility, int index) {
      this.name = name;
      this.type = type;
      this.index = index;
      this.visibility = visibility;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public Visibility getVisibility() {
      return visibility;
    }

    public int getIndex() {
      return index;
    }
  }

  protected final String className;
  protected final Map<String, KotlinProperty> properties = Maps.newHashMap();

  public TestKotlinClass(String className) {
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public TestKotlinClass addProperty(String name, String type, Visibility visibility) {
    assert !properties.containsKey(name);
    properties.put(name, new KotlinProperty(name, type, visibility, properties.size()));
    return this;
  }

  protected KotlinProperty getProperty(String name) {
    assert properties.containsKey(name);
    return properties.get(name);
  }

  public MemberNaming.MethodSignature getGetterForProperty(String propertyName) {
    KotlinProperty property = getProperty(propertyName);
    String type = property.type;
    String getterName;
    if (propertyName.length() > 2 && propertyName.startsWith("is")
        && (propertyName.charAt(2) == '_' || Character.isUpperCase(propertyName.charAt(2)))) {
      // Getter for property "isAbc" is "isAbc".
      getterName = propertyName;
    } else {
      // Getter for property "abc" is "getAbc".
      getterName =
          "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }
    if (property.getVisibility() == Visibility.INTERNAL) {
      // Append module name
      getterName += "$" + KOTLIN_MODULE_NAME;
    }
    return new MemberNaming.MethodSignature(getterName, type, Collections.emptyList());
  }

  public MemberNaming.MethodSignature getSetterForProperty(String propertyName) {
    KotlinProperty property = getProperty(propertyName);
    String setterName = "set"
        + Character.toUpperCase(property.name.charAt(0))
        + property.name.substring(1);
    if (property.getVisibility() == Visibility.INTERNAL) {
      // Append module name
      setterName += "$" + KOTLIN_MODULE_NAME;
    }
    return new MemberNaming.MethodSignature(setterName, "void",
        Collections.singleton(property.getType()));
  }

}
