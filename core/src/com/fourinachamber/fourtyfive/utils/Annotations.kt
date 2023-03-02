package com.fourinachamber.fourtyfive.utils

/**
 * indicates that function, constructor, etc. can only be called from the main openGL thread, usually because it
 * performs an operation on the GPU, like drawing or creating a texture
 *
 * counterpart: [AllThreadsAllowed]
 */
@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE, // for annotating lambdas
)
@Retention(AnnotationRetention.SOURCE)
annotation class MainThreadOnly

/**
 * indicates that a function, constructor, etc. can be called from any thread. Targets annotated with this must not
 * perform any actions that need the openGL context, which is only present on the main thread
 *
 * counterpart: [MainThreadOnly]
 */
@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE, // for annotating lambdas
)
@Retention(AnnotationRetention.SOURCE)
annotation class AllThreadsAllowed
