data class Message(
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: Int = TYPE_MESSAGE,
    // ... 其他现有字段
) {
    companion object {
        const val TYPE_MESSAGE = 0
        const val TYPE_TIMESTAMP = 1
    }
} 