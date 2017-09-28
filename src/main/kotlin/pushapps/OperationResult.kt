package pushapps

data class OperationResult(
    val name: String,
    val didSucceed: Boolean,
    val error: Throwable? = null,
    val optional: Boolean = false
)
