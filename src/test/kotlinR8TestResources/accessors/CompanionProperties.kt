// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package accessors

class CompanionProperties {
    companion object {
        private var privateProp: String = "privateProp"
    }

    fun callSetterPrivateProp(v: String) {
        privateProp = v
    }

    fun callGetterPrivateProp(): String {
        return privateProp
    }
}

fun companionProperties_noUseOfProperties() {
    CompanionProperties()
    println("DONE")
}

fun companionProperties_usePrivatePropFromOuter() {
    val obj = CompanionProperties()
    obj.callSetterPrivateProp("foo")
    println(obj.callGetterPrivateProp())
}
