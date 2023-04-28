package org.frc1778.junction

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.javapoet.KTypeName
import com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview
import com.squareup.kotlinpoet.javapoet.toJTypeName
import com.squareup.kotlinpoet.javapoet.toKTypeName
import java.io.IOException
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.tools.Diagnostic


@KotlinPoetJavaPoetPreview
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class AutoLogProcessor : AbstractProcessor() {

    private fun getPackageName(e: Element): String? {
        var e = e
        while (e != null) {
            if (e.kind.equals(ElementKind.PACKAGE)) {
                return (e as PackageElement).qualifiedName.toString()
            }
            e = e.enclosingElement
        }
        return null
    }


    private val LOG_TABLE_TYPE: KTypeName =
        ClassName("org.littletonrobotics.junction", "LogTable").toJTypeName().toKTypeName()
    private val LOGGABLE_INPUTS_TYPE: KTypeName = ClassName(
        "org.littletonrobotics.junction.inputs", "LoggableInputs"
    ).toJTypeName().toKTypeName()
    private val LOGGABLE_TYPES_LOOKUP: MutableMap<String, String> = java.util.HashMap<String, String>()
    private val UNLOGGABLE_TYPES_SUGGESTIONS: MutableMap<String, String> = java.util.HashMap<String, String>()

    init {
        LOGGABLE_TYPES_LOOKUP["byte[]"] = "Raw";
        LOGGABLE_TYPES_LOOKUP["boolean"] = "Boolean";
        LOGGABLE_TYPES_LOOKUP["long"] = "Integer";
        LOGGABLE_TYPES_LOOKUP["float"] = "Float";
        LOGGABLE_TYPES_LOOKUP["double"] = "Double";
        LOGGABLE_TYPES_LOOKUP["java.lang.String"] = "String";
        LOGGABLE_TYPES_LOOKUP["boolean[]"] = "BooleanArray";
        LOGGABLE_TYPES_LOOKUP["long[]"] = "IntegerArray";
        LOGGABLE_TYPES_LOOKUP["float[]"] = "FloatArray";
        LOGGABLE_TYPES_LOOKUP["double[]"] = "DoubleArray";
        LOGGABLE_TYPES_LOOKUP["java.lang.String[]"] = "StringArray";

        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Byte[]"] = "byte[]";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Boolean"] = "boolean";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Long"] = "long";
        UNLOGGABLE_TYPES_SUGGESTIONS["int"] = "long";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Integer"] = "long";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Float"] = "float";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Double"] = "double";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Boolean[]"] = "boolean[]";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Long[]"] = "long[]";
        UNLOGGABLE_TYPES_SUGGESTIONS["int[]"] = "long[]";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Integer[]"] = "long[]";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Float[]"] = "float[]";
        UNLOGGABLE_TYPES_SUGGESTIONS["java.lang.Double[]"] = "double[]";
    }

    @OptIn(DelicateKotlinPoetApi::class)
    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        val annotationOptional =
            annotations!!.stream().filter { te -> te.simpleName.toString() == "org.frc1778.junction.AutoLog" }.findFirst()
        if (!annotationOptional.isPresent) {
            return false
        }

        val annotation: TypeElement = annotationOptional.get()

        roundEnv!!.getElementsAnnotatedWith(annotation).forEach { classElement ->
            val autoLoggedClassName: String = classElement.simpleName.toString() + "AutoLogged";
            val autoLoggedPackage: String? = getPackageName(classElement)


            val toLogBuilder =
                FunSpec.builder("toLog").addModifiers(KModifier.OVERRIDE).addParameter("table", LOG_TABLE_TYPE)

            val fromLogBuilder =
                FunSpec.builder("fromLog").addModifiers(KModifier.OVERRIDE).addParameter("table", LOG_TABLE_TYPE)

            val cloneBuilder = FunSpec.builder("clone")
                .addCode("%L copy = new %L\n", autoLoggedClassName, autoLoggedClassName)
                .returns(ClassName(autoLoggedPackage ?: "", autoLoggedClassName))

            classElement.enclosedElements.filter { element -> element.kind == ElementKind.FIELD }
                .forEach { fieldElement ->
                    val simpleName: String = fieldElement.simpleName.toString()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() };
                    val logName: String =
                        simpleName.substring(0, 1).uppercase(Locale.getDefault()) + simpleName.substring(1)

                    val fieldType = fieldElement.asType().toString()
                    val logType = LOGGABLE_TYPES_LOOKUP[fieldType]

                    if (logType == null) {
                        val typeSuggestion = UNLOGGABLE_TYPES_SUGGESTIONS[fieldType]
                        var extraText = ""
                        if (typeSuggestion != null) {
                            extraText = "Did you mean to use \"$typeSuggestion\" instead?"
                        } else {
                            extraText = "\"$fieldType\" is not supported"
                        }
                        System.err.println(
                            "[org.frc1778.junction.AutoLog] Unkonwn type for \"" + simpleName + "\" from \"" + classElement.simpleName + "\" (" + extraText + ")"
                        )
                    } else {
                        val getterName = "get$logType"
                        toLogBuilder.addCode("table.put(%S, get%L())\n", logName, simpleName)
                        fromLogBuilder.addCode(
                            "set%L(table.%L(%S, get%L()))\n", simpleName, getterName, logName, simpleName
                        )
                        // Need to deep copy arrays
                        if ((fieldElement.asType().kind == TypeKind.ARRAY)) {
                            cloneBuilder.addCode("copy.get%L() = this.get%L().clone();\n", simpleName, simpleName)
                        } else {
                            cloneBuilder.addCode("copy.get%L() = this.get%L();\n", simpleName, simpleName)
                        }
                    }
                }
            cloneBuilder.addCode("return copy\n")

            val type = TypeSpec.classBuilder(autoLoggedClassName)
                .addSuperinterface(LOGGABLE_INPUTS_TYPE)
                .addSuperinterface(ClassName("java.lang", "Cloneable"))
                .superclass(classElement.asType().asTypeName())
                .addFunction(toLogBuilder.build())
                .addFunction(fromLogBuilder.build())
                .addFunction(cloneBuilder.build())
                .build()

            val kotlinFile = FileSpec.builder(autoLoggedPackage.toString(), type.toString()).build()

            try {
                kotlinFile.writeTo(processingEnv.filer)
            } catch (e: IOException) {
                processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write class", classElement)
                e.printStackTrace()
            }
        }
        return true
    }
}