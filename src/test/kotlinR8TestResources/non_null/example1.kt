// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package non_null

data class Car(
        val make: String,
        val model: String,
        val year: Int,
        val plateNumber: String)

fun Collection<Car>.forMakeAndModel(
        make: String, model: String, sinceYear: Int?
) = this.asSequence()
        .filter { it.make == make }
        .filter { it.model == model }
        .filter { sinceYear != null && it.year >= sinceYear }
        .groupBy { it.year }
        .toSortedMap()

fun main(args: Array<String>) {
    val leaf = Car("Nissan", "Leaf", 2015, "  LEAF  ")
    val ms1 = Car("Tesla", "Model S", 2015, "  LGTM1 ")
    val ms2 = Car("Tesla", "Model S", 2017, "  LGTM2 ")
    val m3 = Car("Tesla", "Model 3", 2018, "  LGTM3 ")
    val cars: List<Car> = mutableListOf(leaf, ms1, ms2, m3)
    println(cars.forMakeAndModel("Tesla", "Model S", null))
}
