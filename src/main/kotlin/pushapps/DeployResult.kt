package pushapps

data class DeployResult(
    val appName: String,
    val didSucceed: Boolean,
    val error: Throwable? = null
)