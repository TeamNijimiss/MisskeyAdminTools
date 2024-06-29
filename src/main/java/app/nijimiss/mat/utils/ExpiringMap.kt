/*
 * Copyright 2024 Nafu Satsuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.nijimiss.mat.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExpiringMap<K : Any, V : Any>(private val expiryDuration: Long, private val timeUnit: TimeUnit) :
    MutableMap<K, V> {
    private val map = ConcurrentHashMap<K, V>()
    private val expiryMap = ConcurrentHashMap<K, Long>()
    private val scheduler = Executors.newScheduledThreadPool(1)

    init {
        scheduler.scheduleAtFixedRate({
            val currentTime = System.currentTimeMillis()
            val keysToRemove = expiryMap.filterValues { it <= currentTime }.keys
            keysToRemove.forEach {
                map.remove(it)
                expiryMap.remove(it)
            }
        }, expiryDuration, expiryDuration, timeUnit)
    }

    override val size: Int
        get() = map.size

    override fun isEmpty() = map.isEmpty()

    override fun containsKey(key: K) = map.containsKey(key)

    override fun containsValue(value: V) = map.containsValue(value)

    override fun get(key: K): V? = map[key]

    override fun put(key: K, value: V): V? {
        val result: V? = map.put(key, value)
        expiryMap[key] = System.currentTimeMillis() + timeUnit.toMillis(expiryDuration)
        return result
    }

    override fun remove(key: K): V? {
        expiryMap.remove(key)
        return map.remove(key)
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) ->
            map[key] = value
            expiryMap[key] = System.currentTimeMillis() + timeUnit.toMillis(expiryDuration)
        }
    }

    override fun clear() {
        map.clear()
        expiryMap.clear()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = map.entries

    override val keys: MutableSet<K>
        get() = map.keys

    override val values: MutableCollection<V>
        get() = map.values
}
