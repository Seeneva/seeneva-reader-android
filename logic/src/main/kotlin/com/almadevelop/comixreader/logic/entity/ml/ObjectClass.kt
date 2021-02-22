package com.almadevelop.comixreader.logic.entity.ml

/**
 * Represent ML object class supported by the app
 * @param id object class id
 */
enum class ObjectClass(internal val id: Long) {
    /**
     * Single comic book speech balloon
     */
    SPEECH_BALLOON(0),

    /**
     * Sing comic book panel on the page
     */
    PANEL(1);

    companion object {
        /**
         * @param id object class id
         * @return [ObjectClass] if provided class id was valid or null
         */
        internal fun fromId(id: Long) = values().firstOrNull { it.id == id }

        /**
         * @param id object class id
         * @throws IllegalArgumentException
         */
        internal fun requireFromId(id: Long) =
            requireNotNull(fromId(id)) { "Can't get object class by id: $id" }
    }
}