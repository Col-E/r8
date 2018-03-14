// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package accessors

class PropertyAccessorForLambda {
    private var property: String = "foo"
        get() = { field }()
        set(v) = { field = v }()

    // Causes a class initializer to be added to the class.
    companion object {
        public var companionProperty = "static"
    }

    fun accessPropertyOfOuterClass() {
        // Access to the property requires to go through an accessor method, respectively
        // named "access$getProperty$lp" for getter and "access$setProperty$lp" for setter).
        property = "bar"
        println(property)
    }
}

fun noUseOfPropertyAccessorFromLambda() {
    // Create instance of class to keep them after tree shaking.
    PropertyAccessorForLambda()
}

fun usePropertyAccessorFromLambda() {
    PropertyAccessorForLambda.companionProperty = "fake"
    PropertyAccessorForLambda().accessPropertyOfOuterClass()
}