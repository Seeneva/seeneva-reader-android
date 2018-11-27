package com.almadevelop.comixreader

typealias ClassId = Long

data class ComicPageObjects(val position: Long, val name: String, val objects: ObjectDetection) {
    @Suppress("unused")
    class Builder(private val position: Long, private val name: String) {
        private val data: MutableMap<ClassId, MutableList<ObjectPrediction>> = hashMapOf()

        fun addObject(id: ClassId, probability: Float, cx: Float, cy: Float, w: Float, h: Float) {
            data.getOrPut(id) { arrayListOf() } += ObjectPrediction(probability, cx, cy, w, h)
        }

        fun build() = ComicPageObjects(position, name, ObjectDetection(data))
    }
}

data class ObjectDetection(val data: Map<ClassId, List<ObjectPrediction>>)

data class ObjectPrediction(val probability: Float, val box: ObjectBox) {
    constructor(probability: Float, cx: Float, cy: Float, w: Float, h: Float) : this(
        probability,
        ObjectBox(cx, cy, w, h)
    )
}

data class ObjectBox(val cx: Float, val cy: Float, val w: Float, val h: Float)