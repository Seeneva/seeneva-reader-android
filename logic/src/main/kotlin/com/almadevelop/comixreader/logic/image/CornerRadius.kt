package com.almadevelop.comixreader.logic.image

/**
 * Describes an image corner radius
 * @param topLeft
 * @param topRight
 * @param bottomRight
 * @param bottomLeft
 */
data class CornerRadius(
    val topLeft: Float = .0f,
    val topRight: Float = .0f,
    val bottomRight: Float = .0f,
    val bottomLeft: Float = .0f
) {
    init {
        require(topLeft >= .0f)
        require(topRight >= .0f)
        require(bottomRight >= .0f)
        require(bottomLeft >= .0f)
    }

    /**
     * Are all corners have non zero values
     */
    val hasRoundCorners: Boolean
        get() = topLeft > .0f || topRight > .0f || bottomLeft > .0f || bottomRight > .0f

    /**
     * Are all corners have same values
     */
    val equalCorners: Boolean
        get() = topLeft == topRight && topLeft == bottomLeft && topLeft == bottomRight
}