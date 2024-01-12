import java.io.File

val (prefix, animation) = args

File(animation).walk().forEach { file ->
    val newName = file.name.removePrefix(prefix)
    val newPath = File("${file.parentFile.canonicalPath}/$newName")
//    println("rename ${file.canonicalPath} to $newPath")
    file.renameTo(newPath)
}
