// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package accessors

class PropertyAccessorForInnerClass {
    private var privateProp = "private"

    private lateinit var privateLateInitProp: String

    // Causes a class initializer to be added to the class.
    companion object {
        public var companionProperty = "static"
    }

    inner class Inner {
        fun accessPrivateProperty() {
            privateProp = "bar"
            println(privateProp)
        }

        fun accessPrivateLateInitPropertyStatus() {
            println(::privateLateInitProp.isInitialized)
        }
    }
}

fun noUseOfPropertyAccessorFromInnerClass() {
    // Create instance of class to keep them after tree shaking.
    PropertyAccessorForInnerClass().Inner()
}

fun usePrivatePropertyAccessorFromInnerClass() {
    // Creates a non-trivial class initializer
    println(PropertyAccessorForInnerClass.companionProperty)
    PropertyAccessorForInnerClass().Inner().accessPrivateProperty()
}

fun usePrivateLateInitPropertyAccessorFromInnerClass() {
    // Creates a non-trivial class initializer
    println(PropertyAccessorForInnerClass.companionProperty)
    PropertyAccessorForInnerClass().Inner().accessPrivateLateInitPropertyStatus()
}