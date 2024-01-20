import java.io.File

val (reductionFactorString, namePaddingString, animation) = args

val reduceFactor = reductionFactorString.toInt()
val namePadding = namePaddingString.toInt()

val split = File(animation)
    .walk()
    .asIterable()
    .filter { it.isFile }
    .filter { it.nameWithoutExtension.toIntOrNull() != null }
    .groupBy { it.nameWithoutExtension.toInt() % 2 }

val keep = split[0]!!
val delete = split[1]!!

delete.forEach { it.delete() }

keep
    .map { it.nameWithoutExtension.toInt() to it }
    .sortedBy { it.first }
    .forEachIndexed { i, (_, file) ->
        val newPath = "${file.parentFile.canonicalPath}/${i.toString().padStart(namePadding, '0')}.png"
        file.renameTo(File(newPath))
    }
