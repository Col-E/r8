// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.delegated_property_lib

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class Resource(private var s : String = "") {

  override fun toString(): String {
    return s;
  }
}

class CustomDelegate(var resource: Resource = Resource()) {

  operator fun getValue(thisRef: Any?, property: KProperty<*>): Resource {
    println("$resource has been read in CustomDelegate from '" +
            "${property.name}' in ${thisRef?.javaClass?.typeName}")
    return resource;
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Resource) {
    println("$value has been assigned to '${property.name}'" +
            " in ${thisRef?.javaClass?.typeName}")
    this.resource = value
  }
}

class CustomReadOnlyDelegate(private var resource : Resource = Resource("read-only"))
  : ReadOnlyProperty<Any?, Resource> {
  override fun getValue(thisRef: Any?, property: KProperty<*>): Resource {
    println("$resource has been read in CustomReadOnlyDelegate" +
            " from '${property.name}' in ${thisRef?.javaClass?.typeName}")
    return resource;
  }
}

class Delegates {

  var customDelegate : Resource by CustomDelegate()
  val customReadOnlyDelegate : Resource by CustomReadOnlyDelegate()
  val lazyString : String by lazy {
    println("Generating lazy string")
    "42"
  }

  fun localDelegatedProperties(compute: () -> Resource) : Resource {
    val foo by lazy(compute)
    println(foo)
    return foo
  }
}
class User(val map : Map<String, Any?>) {
  val name : String by map
  val age: Int by map
}

class ResourceDelegate(val r : Resource): ReadOnlyProperty<ProvidedDelegates, Resource> {
  override fun getValue(thisRef: ProvidedDelegates, property: KProperty<*>): Resource {
    return r
  }
}

class ResourceLoader(val id: Resource) {
  operator fun provideDelegate(
    thisRef: ProvidedDelegates,
    prop: KProperty<*>
  ): ReadOnlyProperty<ProvidedDelegates, Resource> {
    checkProperty(prop.name)
    return ResourceDelegate(id)
  }

  private fun checkProperty(name: String) {
    println("Checking property for " + name)
  }
}

class ProvidedDelegates {
  fun bindResource(id: String): ResourceLoader {
    return ResourceLoader(Resource(id))
  }

  val image by bindResource("image_id")
  val text by bindResource("text_id")
}
