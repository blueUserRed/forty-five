package com.fourinachamber.fourtyfive.utils

class TemplateString(
    var rawString: String,
    val additionalParams: Map<String, Any>? = null
) {

    val string: String
        get() {
            //TODO: there must be a better solution
            var s = rawString
            for ((name, provider) in params) {
                s = Regex("\\{$name}").replace(s, provider().toString())
            }
            additionalParams ?: return s
            for ((name, value) in additionalParams) {
                s = Regex("\\{$name}").replace(s, value.toString())
            }
            return s
        }

    companion object {

        private val params: MutableMap<String, () -> Any> = mutableMapOf()

        fun bindParam(name: String, provider: () -> Any) {
            params[name] = provider
        }

        fun removeParam(name: String): Unit = run { params.remove(name) }

    }

}