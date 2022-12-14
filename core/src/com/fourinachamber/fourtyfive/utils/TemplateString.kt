package com.fourinachamber.fourtyfive.utils

class TemplateString(
    var rawString: String,
//    val params: Map<String, () -> Any>
) {

    val string: String
        get() {
            //TODO: there must be a better solution
            var s = rawString
            for ((name, provider) in params) {
                s = Regex("\\{$name}").replace(s, provider().toString())
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